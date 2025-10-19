package com.zzy.blog.service

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.zzy.blog.context.AuthContextHolder
import com.zzy.blog.dto.BatchUploadResponse
import com.zzy.blog.dto.BatchUploadResultItem
import com.zzy.blog.entity.DocStatus
import com.zzy.blog.entity.Document
import com.zzy.blog.entity.Group
import com.zzy.blog.exception.ResourceNotFoundException
import com.zzy.blog.mapper.DocumentMapper
import com.zzy.blog.mapper.GroupMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.MalformedInputException
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
    private val directoryTreeService: DirectoryTreeService
) {
    
    private val logger = LoggerFactory.getLogger(BatchUploadService::class.java)
    
    companion object {
        // 支持的文档文件类型
        private val SUPPORTED_DOC_TYPES = setOf("md", "txt", "pdf")
        private val TEXT_DOC_TYPES = setOf("md", "txt")
        // 最大文件大小: 100MB
        private const val MAX_FILE_SIZE = 100 * 1024 * 1024L
    }
    
    /**
     * 批量上传ZIP文件
     * @param file ZIP文件
     * @param parentGroupId 父分组ID，null表示根目录
     * @return 批量上传结果
     */
    @Transactional(rollbackFor = [Exception::class])
    fun batchUpload(file: MultipartFile, parentGroupId: Long?): BatchUploadResponse {
        val userId = getCurrentUserId()
        
        // 1. 验证文件
        validateZipFile(file)
        
        // 2. 验证父分组
        if (parentGroupId != null) {
            val parentGroup = groupMapper.selectById(parentGroupId)
            if (parentGroup == null || parentGroup.userId != userId) {
                throw ResourceNotFoundException("父分组不存在或无权访问")
            }
        }
        
        // 3. 解压ZIP文件到临时目录
        val tempDir = Files.createTempDirectory("batch_upload_").toFile()
        val results = mutableListOf<BatchUploadResultItem>()
        var totalFiles = 0
        var totalFolders = 0
        
        try {
            // 解压文件
            extractZipFile(file, tempDir)
            
            // 4. 递归处理文件夹和文件
            val rootFiles = tempDir.listFiles()?.filter { !it.name.startsWith(".") } ?: emptyList()
            
            for (rootFile in rootFiles) {
                if (rootFile.isDirectory) {
                    val (folders, files) = processDirectory(
                        rootFile, 
                        userId, 
                        parentGroupId, 
                        "",
                        results
                    )
                    totalFolders += folders
                    totalFiles += files
                } else {
                    // 根目录下的文件
                    val success = processFile(rootFile, userId, parentGroupId, rootFile.name, results)
                    if (success) totalFiles++
                }
            }
            
            // 5. 清除缓存
            directoryTreeService.clearCache(userId)
            
            val successCount = results.count { it.success }
            val failedCount = results.size - successCount
            
            logger.info(
                "批量上传完成: userId={}, totalFolders={}, totalFiles={}, success={}, failed={}",
                userId, totalFolders, totalFiles, successCount, failedCount
            )
            
            return BatchUploadResponse(
                success = failedCount == 0,
                totalFiles = totalFiles,
                totalFolders = totalFolders,
                successCount = successCount,
                failedCount = failedCount,
                items = results,
                message = if (failedCount == 0) "批量上传成功" else "批量上传完成，部分失败"
            )
            
        } catch (e: Exception) {
            logger.error("批量上传失败", e)
            throw RuntimeException("批量上传失败: ${e.message}", e)
        } finally {
            // 清理临时文件
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
        
        if (file.size > MAX_FILE_SIZE) {
            throw IllegalArgumentException("文件大小超过限制，最大100MB")
        }
        
        val filename = file.originalFilename ?: ""
        if (!filename.endsWith(".zip", ignoreCase = true)) {
            throw IllegalArgumentException("只支持ZIP格式的压缩包")
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
    
    /**
     * 递归处理目录
     * @return Pair<folders, files> 处理的文件夹数和文件数
     */
    private fun processDirectory(
        dir: File,
        userId: Long,
        parentGroupId: Long?,
        pathPrefix: String,
        results: MutableList<BatchUploadResultItem>
    ): Pair<Int, Int> {
        var folderCount = 0
        var fileCount = 0
        
        val currentPath = if (pathPrefix.isEmpty()) dir.name else "$pathPrefix/${dir.name}"
        
        try {
            // 1. 创建或获取分组
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
            folderCount++
            
            // 2. 处理子文件和子文件夹
            val children = dir.listFiles()?.filter { !it.name.startsWith(".") } ?: emptyList()
            
            for (child in children) {
                if (child.isDirectory) {
                    val (subFolders, subFiles) = processDirectory(child, userId, groupId, currentPath, results)
                    folderCount += subFolders
                    fileCount += subFiles
                } else {
                    val success = processFile(child, userId, groupId, "$currentPath/${child.name}", results)
                    if (success) fileCount++
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
        }
        
        return Pair(folderCount, fileCount)
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
        results: MutableList<BatchUploadResultItem>
    ): Boolean {
        try {
            val fileName = file.name
            val extension = file.extension.lowercase()
            
            // 检查文件类型
            if (extension !in SUPPORTED_DOC_TYPES) {
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
                return false
            }
            
            // 读取文件内容
            val content = if (extension in TEXT_DOC_TYPES) {
                readTextWithCharsetFallback(file)
            } else {
                null  // PDF等文件暂不读取内容
            }
            
            // 创建文档
            val document = Document(
                userId = userId,
                groupId = groupId,
                title = fileName.substringBeforeLast("."),
                type = if (extension == "pdf") "pdf" else "md",
                status = DocStatus.DRAFT.value,
                contentMd = content,
                sortIndex = 0
            )
            
            documentMapper.insert(document)
            
            results.add(
                BatchUploadResultItem(
                    type = "document",
                    name = fileName,
                    path = path,
                    id = document.id,
                    success = true,
                    message = "文档创建成功"
                )
            )
            
            return true
            
        } catch (e: Exception) {
            logger.error("处理文件失败: {}", path, e)
            results.add(
                BatchUploadResultItem(
                    type = "document",
                    name = file.name,
                    path = path,
                    id = null,
                    success = false,
                    message = "创建失败: ${e.message}"
                )
            )
            return false
        }
    }

    /**
     * 按照常见编码尝试读取文本文件，默认UTF-8，失败时尝试国标编码
     */
    private fun readTextWithCharsetFallback(file: File): String {
        val charsets = listOf(
            StandardCharsets.UTF_8,
            Charset.forName("GB18030"),
            Charset.forName("GBK"),
            StandardCharsets.ISO_8859_1
        )
        var lastError: Exception? = null

        for (charset in charsets) {
            try {
                file.inputStream().use { input ->
                    val decoder = charset.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                    InputStreamReader(input, decoder).use { reader ->
                        return reader.readText()
                    }
                }
            } catch (e: MalformedInputException) {
                lastError = e
            } catch (e: CharacterCodingException) {
                lastError = e
            } catch (e: Exception) {
                lastError = e
            }
        }

        // 最后尝试使用UTF-8替换模式，避免完全失败
        file.inputStream().use { input ->
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
            InputStreamReader(input, decoder).use { reader ->
                return reader.readText()
            }
        }

        throw IllegalArgumentException("无法解析文件编码", lastError)
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

