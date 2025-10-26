package com.zzy.file.service

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.extension.plugins.pagination.Page
import com.zzy.file.dto.*
import com.zzy.file.entity.FileMetadata
import com.zzy.common.exception.BusinessException
import com.zzy.file.mapper.FileMapper
import com.zzy.file.mapper.FolderMapper
import com.zzy.file.util.FileUtil
import com.zzy.common.util.RedisUtil
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.InputStream
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * 文件服务（重构版，支持文件夹）
 * @author ZZY
 * @date 2025-10-23
 */
@Service
class FileService(
    private val fileMapper: FileMapper,
    private val folderMapper: FolderMapper,
    private val storageService: StorageService,
    private val redisUtil: RedisUtil
) {
    
    private val logger = LoggerFactory.getLogger(FileService::class.java)
    
    @Value("\${file.max-size}")
    private var maxFileSize: Long = 104857600 // 100MB
    
    @Value("\${file.allowed-types}")
    private lateinit var allowedTypes: String
    
    companion object {
        private const val CACHE_KEY_FILE_LIST = "file:list:"
        private const val CACHE_KEY_FILE_INFO = "file:info:"
        private const val CACHE_EXPIRE_TIME = 300L // 5分钟
        
        private val SORTABLE_FIELDS = mapOf(
            "uploadTime" to "upload_time",
            "updateTime" to "update_time",
            "fileName" to "file_name",
            "fileSize" to "file_size",
            "downloadCount" to "download_count",
            "id" to "id"
        )
        
        private const val DEFAULT_SORT_FIELD = "upload_time"
    }
    
    /**
     * 上传文件
     */
    @Transactional(rollbackFor = [Exception::class])
    fun uploadFile(
        userId: Long,
        file: MultipartFile,
        request: FileUploadRequest
    ): FileResponse {
        // 1. 验证文件
        validateFile(file)
        
        // 2. 检查文件夹是否存在
        if (request.folderId != null) {
            val folder = folderMapper.selectById(request.folderId)
            if (folder == null || folder.deleted) {
                throw BusinessException(404, "文件夹不存在")
            }
            if (folder.userId != userId) {
                throw BusinessException(403, "无权上传到该文件夹")
            }
        }
        
        // 3. 计算MD5（用于秒传）
        val md5 = FileUtil.calculateMd5(file)
        
        // 4. 检查是否存在相同文件（秒传）
        val existingFile = fileMapper.selectByMd5(md5)
        if (existingFile != null && !existingFile.deleted) {
            logger.info("文件已存在，执行秒传: {}", file.originalFilename)
            // 创建新的元数据记录（不同用户、不同文件夹）
            val originalFilename = file.originalFilename ?: "unknown"
            val newFile = FileMetadata(
                userId = userId,
                folderId = request.folderId,
                fileName = originalFilename,
                storedName = existingFile.storedName,
                fileSize = existingFile.fileSize,
                fileType = existingFile.fileType,
                fileExtension = existingFile.fileExtension,
                filePath = existingFile.filePath,
                fileMd5 = md5,
                description = request.description,
                uploadTime = LocalDateTime.now(),
                updateTime = LocalDateTime.now()
            )
            fileMapper.insert(newFile)
            clearFileListCache(userId)
            return FileResponse.fromEntity(newFile)
        }
        
        // 5. 生成存储文件名
        val originalFilename = file.originalFilename ?: "unknown"
        val storedName = FileUtil.generateUniqueFileName(originalFilename)
        val fileExtension = FileUtil.getFileExtension(originalFilename)
        
        // 6. 上传到MinIO
        try {
            storageService.uploadFile(file, storedName)
        } catch (e: Exception) {
            logger.error("文件上传到存储失败", e)
            throw BusinessException(500, "文件上传失败")
        }
        
        // 7. 保存元数据到数据库
        val fileMetadata = FileMetadata(
            userId = userId,
            folderId = request.folderId,
            fileName = originalFilename,
            storedName = storedName,
            fileSize = file.size,
            fileType = file.contentType ?: "application/octet-stream",
            fileExtension = fileExtension,
            filePath = storedName,
            fileMd5 = md5,
            description = request.description,
            uploadTime = LocalDateTime.now(),
            updateTime = LocalDateTime.now()
        )
        
        try {
            fileMapper.insert(fileMetadata)
            logger.info("文件元数据保存成功: userId={}, fileName={}, folderId={}", 
                userId, originalFilename, request.folderId)
        } catch (e: Exception) {
            // 如果数据库保存失败，删除已上传的文件
            storageService.deleteFile(storedName)
            logger.error("文件元数据保存失败", e)
            throw BusinessException(500, "文件信息保存失败")
        }
        
        // 8. 清除缓存
        clearFileListCache(userId)
        
        return FileResponse.fromEntity(fileMetadata)
    }
    
    /**
     * 获取文件列表（支持文件夹筛选）
     */
    fun getFileList(
        userId: Long,
        folderId: Long? = null,
        page: Int = 1,
        size: Int = 20,
        keyword: String? = null,
        sortBy: String = "uploadTime",
        order: String = "desc"
    ): FileListResponse {
        // 尝试从缓存获取
        val cacheKey = "$CACHE_KEY_FILE_LIST$userId:$folderId:$page:$size:$keyword:$sortBy:$order"
        val cached = redisUtil.get(cacheKey, FileListResponse::class.java)
        if (cached != null) {
            logger.debug("从缓存获取文件列表")
            return cached
        }
        
        // 白名单验证
        val dbSortBy = SORTABLE_FIELDS[sortBy] ?: run {
            logger.warn("检测到无效的排序字段: {}, 使用默认排序字段: {}", sortBy, DEFAULT_SORT_FIELD)
            DEFAULT_SORT_FIELD
        }
        
        // 构建查询条件（手动过滤已删除的记录）
        val queryWrapper = QueryWrapper<FileMetadata>()
            .eq("user_id", userId)
            .eq("deleted", false)
            .apply {
                // 文件夹筛选
                if (folderId != null) {
                    eq("folder_id", folderId)
                } else {
                    // folderId == null 表示查询根目录下的文件
                    isNull("folder_id")
                }
                
                // 关键词搜索
                if (!keyword.isNullOrBlank()) {
                    and { qw ->
                        qw.like("file_name", keyword)
                            .or().like("description", keyword)
                    }
                }
            }
            .orderBy(true, order.equals("asc", ignoreCase = true), dbSortBy)
        
        // 分页查询
        val pageObj = Page<FileMetadata>(page.toLong(), size.toLong())
        val pageResult = fileMapper.selectPage(pageObj, queryWrapper)
        
        // 转换为响应对象
        val fileResponses = pageResult.records.map { FileResponse.fromEntity(it) }
        
        // 获取当前文件夹信息（面包屑导航）
        val currentFolder = if (folderId != null) {
            val folder = folderMapper.selectById(folderId)
            if (folder != null && !folder.deleted) {
                val path = buildFolderPath(folderId, userId)
                FolderBreadcrumb(
                    id = folder.id,
                    name = folder.name,
                    path = path
                )
            } else null
        } else null
        
        val response = FileListResponse(
            total = pageResult.total,
            page = page,
            size = size,
            files = fileResponses,
            currentFolder = currentFolder
        )
        
        // 缓存结果
        redisUtil.set(cacheKey, response, CACHE_EXPIRE_TIME, TimeUnit.SECONDS)
        
        return response
    }
    
    /**
     * 获取文件详情
     */
    fun getFileInfo(fileId: Long, userId: Long): FileResponse {
        // 尝试从缓存获取
        val cacheKey = "$CACHE_KEY_FILE_INFO$fileId"
        val cached = redisUtil.get(cacheKey, FileResponse::class.java)
        if (cached != null) {
            logger.debug("从缓存获取文件信息: fileId={}", fileId)
            return cached
        }
        
        val fileMetadata = fileMapper.selectById(fileId)
            ?: throw BusinessException(404, "文件不存在")
        
        // 权限检查
        if (fileMetadata.userId != userId) {
            throw BusinessException(403, "无权访问该文件")
        }
        
        if (fileMetadata.deleted) {
            throw BusinessException(404, "文件已被删除")
        }
        
        val response = FileResponse.fromEntity(fileMetadata)
        
        // 缓存结果
        redisUtil.set(cacheKey, response, CACHE_EXPIRE_TIME * 2, TimeUnit.SECONDS)
        
        return response
    }
    
    /**
     * 更新文件信息
     */
    @Transactional(rollbackFor = [Exception::class])
    fun updateFile(fileId: Long, userId: Long, request: UpdateFileRequest): FileResponse {
        val file = fileMapper.selectById(fileId)
            ?: throw BusinessException(404, "文件不存在")
        
        // 权限检查
        if (file.userId != userId) {
            throw BusinessException(403, "无权修改该文件")
        }
        
        if (file.deleted) {
            throw BusinessException(404, "文件已被删除")
        }
        
        var updated = false
        
        // 更新文件名
        if (request.fileName != null && request.fileName != file.fileName) {
            file.fileName = request.fileName
            updated = true
        }
        
        // 更新文件夹
        if (request.folderId != file.folderId) {
            if (request.folderId != null) {
                val folder = folderMapper.selectById(request.folderId)
                if (folder == null || folder.deleted) {
                    throw BusinessException(404, "目标文件夹不存在")
                }
                if (folder.userId != userId) {
                    throw BusinessException(403, "无权移动到该文件夹")
                }
            }
            file.folderId = request.folderId
            updated = true
        }
        
        // 更新其他字段
        if (request.description != null && request.description != file.description) {
            file.description = request.description
            updated = true
        }
        
        if (updated) {
            file.updateTime = LocalDateTime.now()
            fileMapper.updateById(file)
            
            // 清除缓存
            clearFileInfoCache(fileId)
            clearFileListCache(userId)
            
            logger.info("更新文件信息成功: fileId={}, fileName={}", fileId, file.fileName)
        }
        
        return FileResponse.fromEntity(file)
    }
    
    /**
     * 下载文件
     */
    fun downloadFile(fileId: Long): Pair<InputStream, FileMetadata> {
        val fileMetadata = fileMapper.selectById(fileId)
            ?: throw BusinessException(404, "文件不存在")
        
        if (fileMetadata.deleted) {
            throw BusinessException(404, "文件已被删除")
        }
        
        // 从存储获取文件流
        val inputStream = storageService.downloadFile(fileMetadata.storedName ?: "")
        
        // 增加下载次数
        fileMapper.increaseDownloadCount(fileId)
        
        // 清除文件信息缓存
        clearFileInfoCache(fileId)
        
        return Pair(inputStream, fileMetadata)
    }
    
    /**
     * 批量移动文件
     */
    @Transactional(rollbackFor = [Exception::class])
    fun batchMoveFiles(userId: Long, request: BatchMoveFilesRequest): Int {
        // 验证文件归属
        val files = fileMapper.selectBatchIds(request.fileIds)
        files.forEach { file ->
            if (file.userId != userId) {
                throw BusinessException(403, "无权操作文件: ${file.fileName}")
            }
            if (file.deleted) {
                throw BusinessException(404, "文件已被删除: ${file.fileName}")
            }
        }
        
        // 验证目标文件夹
        if (request.targetFolderId != null) {
            val targetFolder = folderMapper.selectById(request.targetFolderId)
            if (targetFolder == null || targetFolder.deleted) {
                throw BusinessException(404, "目标文件夹不存在")
            }
            if (targetFolder.userId != userId) {
                throw BusinessException(403, "无权移动到该文件夹")
            }
        }
        
        // 批量移动
        val count = fileMapper.batchMoveFiles(request.fileIds, request.targetFolderId)
        
        // 清除缓存
        clearFileListCache(userId)
        request.fileIds.forEach { clearFileInfoCache(it) }
        
        logger.info("批量移动文件成功: userId={}, count={}, targetFolderId={}", 
            userId, count, request.targetFolderId)
        
        return count
    }
    
    /**
     * 删除文件（软删除）
     */
    @Transactional(rollbackFor = [Exception::class])
    fun deleteFile(fileId: Long, userId: Long): Boolean {
        val file = fileMapper.selectById(fileId)
            ?: throw BusinessException(404, "文件不存在")
        
        // 权限检查
        if (file.userId != userId) {
            throw BusinessException(403, "无权删除该文件")
        }
        
        if (file.deleted) {
            throw BusinessException(404, "文件已被删除")
        }
        
        // 软删除
        fileMapper.softDelete(fileId)
        
        // 清除缓存
        clearFileInfoCache(fileId)
        clearFileListCache(userId)
        
        logger.info("文件已移入回收站: fileId={}, fileName={}", fileId, file.fileName)
        
        return true
    }
    
    /**
     * 批量删除文件
     */
    @Transactional(rollbackFor = [Exception::class])
    fun batchDeleteFiles(userId: Long, request: BatchDeleteFilesRequest): Int {
        // 验证文件归属
        val files = fileMapper.selectBatchIds(request.fileIds)
        files.forEach { file ->
            if (file.userId != userId) {
                throw BusinessException(403, "无权删除文件: ${file.fileName}")
            }
        }
        
        // 批量软删除
        val count = fileMapper.batchSoftDelete(request.fileIds)
        
        // 清除缓存
        clearFileListCache(userId)
        request.fileIds.forEach { clearFileInfoCache(it) }
        
        logger.info("批量删除文件成功: userId={}, count={}", userId, count)
        
        return count
    }
    
    /**
     * 永久删除文件
     */
    @Transactional(rollbackFor = [Exception::class])
    fun permanentlyDeleteFile(fileId: Long, userId: Long): Boolean {
        // 使用 selectByIdIncludeDeleted 查询包括已删除的文件
        val file = fileMapper.selectByIdIncludeDeleted(fileId)
            ?: throw BusinessException(404, "文件不存在")
        
        // 权限检查
        if (file.userId != userId) {
            throw BusinessException(403, "无权删除该文件")
        }
        
        // 从MinIO删除
        try {
            storageService.deleteFile(file.storedName ?: "")
            logger.info("从MinIO删除文件: {}", file.storedName)
        } catch (e: Exception) {
            logger.warn("从MinIO删除文件失败: {}", file.storedName, e)
        }
        
        // 从数据库物理删除
        fileMapper.hardDelete(fileId)
        
        // 清除缓存
        clearFileInfoCache(fileId)
        clearFileListCache(userId)
        
        logger.info("文件永久删除成功: fileId={}, fileName={}", fileId, file.fileName)
        
        return true
    }
    
    /**
     * 恢复文件
     */
    @Transactional(rollbackFor = [Exception::class])
    fun restoreFile(fileId: Long, userId: Long): Boolean {
        // 使用 selectByIdIncludeDeleted 查询包括已删除的文件
        val file = fileMapper.selectByIdIncludeDeleted(fileId)
            ?: throw BusinessException(404, "文件不存在")
        
        // 权限检查
        if (file.userId != userId) {
            throw BusinessException(403, "无权恢复该文件")
        }
        
        if (!file.deleted) {
            logger.warn("文件未被删除，无需恢复: fileId={}", fileId)
            return false
        }
        
        // 恢复文件
        fileMapper.restore(fileId)
        
        // 清除缓存
        clearFileInfoCache(fileId)
        clearFileListCache(userId)
        
        logger.info("文件已从回收站恢复: fileId={}, fileName={}", fileId, file.fileName)
        
        return true
    }
    
    /**
     * 获取回收站文件列表
     */
    fun getRecycleBinFiles(userId: Long, page: Int = 1, size: Int = 10): FileListResponse {
        // 查询已删除的文件（deleted = 1）
        val offset = (page - 1) * size.toLong()
        val files = fileMapper.selectRecycleBinFiles(userId, offset, size.toLong())
        val total = fileMapper.countRecycleBinFiles(userId)
        
        val fileResponses = files.map { FileResponse.fromEntity(it) }
        return FileListResponse(
            total = total,
            page = page,
            size = size,
            files = fileResponses
        )
    }
    
    /**
     * 清理过期的回收站文件
     */
    @Transactional(rollbackFor = [Exception::class])
    fun cleanupExpiredFiles(retentionDays: Int = 30): Int {
        val threshold = LocalDateTime.now().minusDays(retentionDays.toLong())
        
        val expiredFiles = fileMapper.selectList(
            QueryWrapper<FileMetadata>()
                .eq("deleted", true)
                .isNotNull("deleted_at")
                .le("deleted_at", threshold)
        )
        
        var cleanedCount = 0
        expiredFiles.forEach { file ->
            try {
                storageService.deleteFile(file.storedName ?: "")
                fileMapper.hardDelete(file.id!!)
                cleanedCount++
                logger.info("清理过期文件: fileId={}, fileName={}, deletedAt={}", 
                    file.id, file.fileName, file.deletedAt)
            } catch (e: Exception) {
                logger.error("清理文件失败: fileId={}, fileName={}", file.id, file.fileName, e)
            }
        }
        
        if (cleanedCount > 0) {
            logger.info("文件清理完成: 共清理 {} 个过期文件（保留期{}天）", cleanedCount, retentionDays)
        }
        
        return cleanedCount
    }
    
    /**
     * 获取文件统计信息
     */
    fun getFileStatistics(userId: Long): FileStatisticsResponse {
        val totalFiles = fileMapper.countByUserId(userId)
        val totalSize = fileMapper.sumSizeByUserId(userId)
        val typeDistribution = fileMapper.getFileTypeDistribution(userId)
            .associate { it["file_extension"].toString() to (it["count"] as Number).toInt() }
        
        // 最近上传的文件（手动过滤已删除的记录）
        val recentFiles = fileMapper.selectList(
            QueryWrapper<FileMetadata>()
                .eq("user_id", userId)
                .eq("deleted", false)
                .orderByDesc("upload_time")
                .last("LIMIT 5")
        )
        
        return FileStatisticsResponse(
            totalFiles = totalFiles,
            totalSize = totalSize,
            totalSizeFormatted = formatFileSize(totalSize),
            fileTypeDistribution = typeDistribution,
            recentUploads = recentFiles.map { FileResponse.fromEntity(it) }
        )
    }
    
    /**
     * 验证文件
     */
    private fun validateFile(file: MultipartFile) {
        val filename = file.originalFilename ?: throw BusinessException(400, "文件名不能为空")
        
        if (!FileUtil.validateFileSize(file.size, maxFileSize)) {
            throw BusinessException(400, "文件大小超过限制，最大支持 ${FileUtil.formatFileSize(maxFileSize)}")
        }
        
        val allowedTypeList = allowedTypes.split(",").map { it.trim() }
        if (!FileUtil.validateFileType(filename, allowedTypeList)) {
            throw BusinessException(400, "不支持的文件类型，仅支持: $allowedTypes")
        }
    }
    
    /**
     * 构建文件夹路径
     */
    private fun buildFolderPath(folderId: Long, userId: Long): List<com.zzy.file.dto.FolderPathItem> {
        val path = mutableListOf<com.zzy.file.dto.FolderPathItem>()
        var currentId: Long? = folderId
        
        while (currentId != null) {
            val folder = folderMapper.selectById(currentId)
            if (folder == null || folder.deleted || folder.userId != userId) {
                break
            }
            path.add(0, com.zzy.file.dto.FolderPathItem(id = folder.id!!, name = folder.name))
            currentId = folder.parentId
        }
        
        return path
    }
    
    /**
     * 格式化文件大小
     */
    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format("%.2f KB", size / 1024.0)
            size < 1024 * 1024 * 1024 -> String.format("%.2f MB", size / (1024.0 * 1024))
            else -> String.format("%.2f GB", size / (1024.0 * 1024 * 1024))
        }
    }
    
    /**
     * 清除文件列表缓存
     */
    private fun clearFileListCache(userId: Long) {
        redisUtil.deleteByPattern("$CACHE_KEY_FILE_LIST$userId:*")
    }
    
    /**
     * 清除文件信息缓存
     */
    private fun clearFileInfoCache(fileId: Long) {
        redisUtil.delete("$CACHE_KEY_FILE_INFO$fileId")
    }
    
    /**
     * 清除所有缓存
     */
    fun clearAllCache() {
        logger.info("开始清除所有文件相关缓存")
        redisUtil.deleteByPattern("$CACHE_KEY_FILE_LIST*")
        redisUtil.deleteByPattern("$CACHE_KEY_FILE_INFO*")
        logger.info("所有文件相关缓存已清除")
    }
}
