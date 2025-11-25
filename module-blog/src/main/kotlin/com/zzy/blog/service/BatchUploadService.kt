package com.zzy.blog.service

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.zzy.blog.constants.DocumentConstants
import com.zzy.blog.dto.BatchUploadJobStatus
import com.zzy.blog.dto.BatchUploadProgressUpdate
import com.zzy.blog.dto.BatchUploadResponse
import com.zzy.blog.dto.BatchUploadResultItem
import com.zzy.blog.entity.Document
import com.zzy.blog.entity.Group
import com.zzy.blog.support.FileBackedMultipartFile
import com.zzy.common.context.AuthContextHolder
import com.zzy.common.exception.ResourceNotFoundException
import com.zzy.blog.mapper.DocumentMapper
import com.zzy.blog.mapper.GroupMapper
import com.zzy.file.mapper.FileMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.CharacterCodingException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.ZipInputStream

/**
 * 批量上传服务
 * 处理ZIP文件的批量上传，将文件夹转换为分组，文件转换为文档
 * @author ZZY
 * @date 2025-10-19
 */
@Service
class BatchUploadService(
    private val groupMapper: GroupMapper,
    private val documentMapper: DocumentMapper,
    private val directoryTreeService: DirectoryTreeService,
    private val fileService: com.zzy.file.service.FileService,
    private val systemFolderManager: com.zzy.file.service.SystemFolderManager,
    private val fileMapper: FileMapper,
    private val folderMapper: com.zzy.file.mapper.FolderMapper
) {
    
    private val logger = LoggerFactory.getLogger(BatchUploadService::class.java)
    
    /**
     * 批量上传ZIP文件
     * @param file ZIP文件
     * @param parentGroupId 父分组ID，null表示根目录
     * @return 批量上传结果
     */
    @Transactional(rollbackFor = [Exception::class])
    fun batchUpload(file: MultipartFile, parentGroupId: Long?): BatchUploadResponse {
        val userId = getCurrentUserId()
        validateBatchUploadRequest(file, parentGroupId, userId)
        return executeBatchUpload(file, parentGroupId, userId, null)
    }

    @Transactional(rollbackFor = [Exception::class])
    fun batchUploadWithProgress(
        file: MultipartFile,
        parentGroupId: Long?,
        userId: Long,
        progressCallback: BatchUploadProgressCallback
    ): BatchUploadResponse {
        validateBatchUploadRequest(file, parentGroupId, userId)
        return executeBatchUpload(file, parentGroupId, userId, progressCallback)
    }

    internal fun validateBatchUploadRequest(file: MultipartFile, parentGroupId: Long?, userId: Long) {
        validateZipFile(file)
        validateParentGroup(parentGroupId, userId)
    }

    private fun executeBatchUpload(
        file: MultipartFile,
        parentGroupId: Long?,
        userId: Long,
        progressCallback: BatchUploadProgressCallback?
    ): BatchUploadResponse {
        val tempDir = Files.createTempDirectory("batch_upload_").toFile()
        val results = mutableListOf<BatchUploadResultItem>()
        val progress = ProgressContext(progressCallback)
        progress.stage("任务初始化中")

        try {
            progress.stage("正在解压 ZIP 文件")
            extractZipFile(file, tempDir)

            val rootFiles = tempDir.listFiles()?.filter { !it.name.startsWith(".") } ?: emptyList()
            val (totalFolders, totalFiles) = calculateTotals(rootFiles)
            progress.updateTotals(totalFolders = totalFolders, totalFiles = totalFiles)
            if (rootFiles.isEmpty()) {
                progress.stage("压缩包为空")
            }

            for (rootFile in rootFiles) {
                if (rootFile.isDirectory) {
                    val currentPath = rootFile.name
                    progress.stage("正在处理分组: $currentPath")
                    processDirectory(
                        dir = rootFile,
                        userId = userId,
                        parentGroupId = parentGroupId,
                        pathPrefix = "",
                        results = results,
                        progress = progress
                    )
                } else {
                    progress.stage("正在处理文件: ${rootFile.name}")
                    processFile(
                        file = rootFile,
                        userId = userId,
                        groupId = parentGroupId,
                        path = rootFile.name,
                        results = results,
                        progress = progress
                    )
                }
            }

            directoryTreeService.clearCache(userId)

            val successCount = results.count { it.success }
            val failedCount = results.size - successCount

            logger.info(
                "批量上传完成: userId={}, totalFolders={}, totalFiles={}, success={}, failed={}",
                userId,
                progress.totalFolders,
                progress.totalFiles,
                progress.successCount,
                progress.failedCount
            )

            val message = if (failedCount == 0) "批量上传成功" else "批量上传完成，部分失败"
            progress.stage(message)

            return BatchUploadResponse(
                success = failedCount == 0,
                totalFiles = progress.totalFiles,
                totalFolders = progress.totalFolders,
                successCount = progress.successCount,
                failedCount = progress.failedCount,
                items = results,
                message = message
            )

        } catch (e: Exception) {
            progress.stage("批量上传失败: ${e.message}")
            logger.error("批量上传失败", e)
            throw RuntimeException("批量上传失败: ${e.message}", e)
        } finally {
            try {
                tempDir.deleteRecursively()
            } catch (e: Exception) {
                logger.warn("清理临时文件失败: {}", e.message)
            }
        }
    }
    
    /**
     * 验证ZIP文件
     */
    private fun validateZipFile(file: MultipartFile) {
        if (file.isEmpty) {
            throw IllegalArgumentException("上传文件不能为空")
        }
        
        if (file.size > DocumentConstants.MAX_FILE_SIZE) {
            throw IllegalArgumentException("文件大小超过限制，最大100MB")
        }
        
        val filename = file.originalFilename ?: ""
        if (!filename.endsWith(".zip", ignoreCase = true)) {
            throw IllegalArgumentException("只支持ZIP格式的压缩包")
        }
    }

    private fun validateParentGroup(parentGroupId: Long?, userId: Long) {
        if (parentGroupId == null) {
            return
        }

        val parentGroup = groupMapper.selectById(parentGroupId)
        if (parentGroup == null || parentGroup.userId != userId) {
            throw ResourceNotFoundException("父分组不存在或无权访问")
        }
    }
    
    /**
     * 解压ZIP文件
     */
    private fun extractZipFile(file: MultipartFile, destDir: File) {
        val zipBytes = file.bytes
        val charsets = listOf(
            StandardCharsets.UTF_8,
            Charset.forName("GB18030"),
            Charset.forName("GBK"),
            Charset.forName("GB2312"),
            StandardCharsets.ISO_8859_1
        )
        var lastError: Exception? = null

        for (charset in charsets) {
            try {
                // 重置解压目录，确保每次尝试都是干净的
                if (destDir.exists()) {
                    destDir.deleteRecursively()
                }
                destDir.mkdirs()

                ByteArrayInputStream(zipBytes).use { byteInput ->
                    ZipInputStream(byteInput, charset).use { zipIn ->
                        var entry = zipIn.nextEntry

                        while (entry != null) {
                            val entryPath = entry.name

                            // 安全检查：防止路径遍历攻击
                            val destFile = File(destDir, entryPath)
                            if (!destFile.canonicalPath.startsWith(destDir.canonicalPath)) {
                                throw SecurityException("检测到不安全的ZIP条目: $entryPath")
                            }

                            if (entry.isDirectory) {
                                destFile.mkdirs()
                            } else {
                                destFile.parentFile?.mkdirs()
                                destFile.outputStream().use { output ->
                                    zipIn.copyTo(output)
                                }
                            }

                            zipIn.closeEntry()
                            entry = zipIn.nextEntry
                        }
                    }
                }

                // 成功解压，直接返回
                return
            } catch (e: SecurityException) {
                // 安全异常立即抛出
                throw e
            } catch (e: CharacterCodingException) {
                lastError = e
            } catch (e: Exception) {
                lastError = e
            }
        }

        throw lastError ?: RuntimeException("ZIP文件解压失败")
    }

    private fun calculateTotals(entries: List<File>): Pair<Int, Int> {
        var folderCount = 0
        var fileCount = 0

        fun traverse(file: File) {
            if (file.name.startsWith(".")) {
                return
            }
            if (file.isDirectory) {
                folderCount++
                file.listFiles()?.forEach { traverse(it) }
            } else {
                fileCount++
            }
        }

        entries.forEach { traverse(it) }
        return Pair(folderCount, fileCount)
    }
    
    /**
     * 递归处理目录
     */
    private fun processDirectory(
        dir: File,
        userId: Long,
        parentGroupId: Long?,
        pathPrefix: String,
        results: MutableList<BatchUploadResultItem>,
        progress: ProgressContext
    ) {
        val currentPath = if (pathPrefix.isEmpty()) dir.name else "$pathPrefix/${dir.name}"

        try {
            progress.stage("正在处理分组: $currentPath")
            val groupId = createOrGetGroup(dir.name, userId, parentGroupId)

            results.add(
                BatchUploadResultItem(
                    type = "group",
                    name = dir.name,
                    path = currentPath,
                    id = groupId,
                    success = true,
                    message = "分组创建成功"
                )
            )
            progress.recordGroupResult(true, "分组创建成功: $currentPath")

            val children = dir.listFiles()?.filter { !it.name.startsWith(".") } ?: emptyList()

            for (child in children) {
                if (child.isDirectory) {
                    processDirectory(
                        dir = child,
                        userId = userId,
                        parentGroupId = groupId,
                        pathPrefix = currentPath,
                        results = results,
                        progress = progress
                    )
                } else {
                    val path = "$currentPath/${child.name}"
                    progress.stage("正在处理文件: $path")
                    processFile(
                        file = child,
                        userId = userId,
                        groupId = groupId,
                        path = path,
                        results = results,
                        progress = progress
                    )
                }
            }

        } catch (e: Exception) {
            logger.error("处理文件夹失败: {}", currentPath, e)
            results.add(
                BatchUploadResultItem(
                    type = "group",
                    name = dir.name,
                    path = currentPath,
                    id = null,
                    success = false,
                    message = "创建失败: ${e.message}"
                )
            )
            progress.recordGroupResult(false, "分组创建失败: ${e.message}")
        }
    }
    
    /**
     * 处理文件
     * @return 是否成功
     */
    private fun processFile(
        file: File,
        userId: Long,
        groupId: Long?,
        path: String,
        results: MutableList<BatchUploadResultItem>,
        progress: ProgressContext
    ): Boolean {
        try {
            val fileName = file.name
            val extension = file.extension.lowercase()
            
            // 检查文件类型
            if (extension !in DocumentConstants.SUPPORTED_EXTENSIONS) {
                results.add(
                    BatchUploadResultItem(
                        type = "document",
                        name = fileName,
                        path = path,
                        id = null,
                        success = false,
                        message = "不支持的文件类型: $extension"
                    )
                )
                progress.recordDocumentResult(false, "不支持的文件类型: $extension ($path)")
                return false
            }
            
            // 1. 获取知识库文件夹的内部ID
            val knowledgeBaseFolderInternalId = systemFolderManager.getOrCreateSystemFolder(
                userId = userId,
                type = com.zzy.file.service.SystemFolderManager.SystemFolderType.KNOWLEDGE_BASE
            )
            
            // 2. 查询文件夹的公开ID
            val knowledgeBaseFolder = folderMapper.selectById(knowledgeBaseFolderInternalId)
                ?: throw com.zzy.common.exception.BusinessException(500, "知识库文件夹创建失败")
            val knowledgeBaseFolderPublicId = knowledgeBaseFolder.publicId
                ?: throw com.zzy.common.exception.BusinessException(500, "知识库文件夹缺少公开ID")
            
            // 3. 上传文件到文件管理系统的知识库文件夹
            val multipartFile = convertToMultipartFile(file)
            val fileResponse = fileService.uploadFile(
                userId = userId,
                file = multipartFile,
                request = com.zzy.file.dto.FileUploadRequest(
                    folderId = knowledgeBaseFolderPublicId
                )
            )
            
            // 4. 通过公开ID查询文件元数据获取内部ID
            val fileMetadata = fileMapper.selectByPublicId(fileResponse.id)
                ?: throw com.zzy.common.exception.BusinessException(500, "文件上传成功但查询失败: ${fileName}")
            
            // 5. 创建文档记录
            val document = Document(
                userId = userId,
                groupId = groupId,
                title = fileName.substringBeforeLast("."),
                type = extension,
                fileId = fileMetadata.id,
                filePath = fileResponse.downloadUrl,
                sortIndex = 0
            )
            
            documentMapper.insert(document)
            
            val message = "文档创建成功"
            results.add(
                BatchUploadResultItem(
                    type = "document",
                    name = fileName,
                    path = path,
                    id = document.id,
                    success = true,
                    message = message
                )
            )
            progress.recordDocumentResult(true, "$message: $path")
            
            return true
            
        } catch (e: Exception) {
            logger.error("处理文件失败: {}", path, e)
            val message = "创建失败: ${e.message}"
            results.add(
                BatchUploadResultItem(
                    type = "document",
                    name = file.name,
                    path = path,
                    id = null,
                    success = false,
                    message = message
                )
            )
            progress.recordDocumentResult(false, "$message ($path)")
            return false
        }
    }
    
    /**
     * 将File转换为MultipartFile
     * 使用流式读取，避免大文件内存压力
     */
    private fun convertToMultipartFile(file: File): MultipartFile {
        return FileBackedMultipartFile(file)
    }

    private class ProgressContext(
        private val callback: BatchUploadProgressCallback?
    ) {
        var totalFiles: Int = 0
            private set
        var totalFolders: Int = 0
            private set
        var processedFiles: Int = 0
            private set
        var successCount: Int = 0
            private set
        var failedCount: Int = 0
            private set

        private var lastMessage: String = ""

        fun updateTotals(totalFolders: Int, totalFiles: Int) {
            this.totalFolders = totalFolders
            this.totalFiles = totalFiles
            publish()
        }

        fun recordDocumentResult(success: Boolean, message: String?) {
            processedFiles++
            recordResult(success, message)
        }

        fun recordGroupResult(success: Boolean, message: String?) {
            if (!message.isNullOrBlank()) {
                lastMessage = message
            }
            publish()
        }

        fun stage(message: String) {
            if (!message.isNullOrBlank()) {
                lastMessage = message
            }
            publish()
        }

        private fun recordResult(success: Boolean, message: String?) {
            if (success) successCount++ else failedCount++
            if (!message.isNullOrBlank()) {
                lastMessage = message
            }
            publish()
        }

        private fun publish(status: BatchUploadJobStatus = BatchUploadJobStatus.RUNNING) {
            callback?.onProgress(
                BatchUploadProgressUpdate(
                    status = status,
                    totalFiles = totalFiles,
                    totalFolders = totalFolders,
                    processedFiles = processedFiles,
                    successCount = successCount,
                    failedCount = failedCount,
                    message = lastMessage
                )
            )
        }
    }
    /**
     * 创建或获取分组（同名分组会复用）
     * 在同一个父分组下，同名分组会被合并
     */
    private fun createOrGetGroup(name: String, userId: Long, parentGroupId: Long?): Long {
        // 查询是否存在同名分组
        val existingGroup = groupMapper.selectOne(
            QueryWrapper<Group>()
                .eq("user_id", userId)
                .eq("name", name)
                .apply {
                    if (parentGroupId != null) {
                        eq("parent_id", parentGroupId)
                    } else {
                        isNull("parent_id")
                    }
                }
        )
        
        if (existingGroup != null) {
            logger.debug("复用已存在的分组: name={}, id={}", name, existingGroup.id)
            return existingGroup.id!!
        }
        
        // 创建新分组
        val group = Group(
            userId = userId,
            name = name,
            parentId = parentGroupId,
            sortIndex = 0
        )
        
        groupMapper.insert(group)
        logger.debug("创建新分组: name={}, id={}", name, group.id)
        
        return group.id!!
    }
    
    /**
     * 获取当前用户ID
     */
    private fun getCurrentUserId(): Long {
        return AuthContextHolder.getCurrentUserId()
    }
}
