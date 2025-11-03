package com.zzy.file.service

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.extension.plugins.pagination.Page
import com.zzy.file.dto.*
import com.zzy.file.entity.FileMetadata
import com.zzy.common.exception.BusinessException
import com.zzy.file.mapper.FileMapper
import com.zzy.file.mapper.FolderMapper
import com.zzy.file.util.FileUtil
import com.zzy.file.util.FileSizeFormatter
import com.zzy.file.constants.FileConstants
import com.zzy.common.util.RedisUtil
import com.zzy.file.service.common.ValidationService
import com.zzy.file.service.common.PathBuilderService
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
    private val redisUtil: RedisUtil,
    private val systemFolderManager: SystemFolderManager,
    private val validationService: ValidationService,
    private val pathBuilderService: PathBuilderService
) {
    
    private val logger = LoggerFactory.getLogger(FileService::class.java)
    
    @Value("\${file.max-size}")
    private var maxFileSize: Long = 104857600 // 100MB
    
    @Value("\${file.allowed-types}")
    private lateinit var allowedTypes: String
    
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
     * 上传文件到系统文件夹
     * 
     * 此方法会自动将文件上传到指定的系统文件夹，系统会自动创建必要的文件夹结构。
     * 适用场景：
     * - 知识库文档上传
     * - 附件文件上传
     * 
     * @param userId 用户ID
     * @param file 文件
     * @param folderType 系统文件夹类型
     * @param description 文件描述（可选）
     * @return 文件响应
     */
    @Transactional(rollbackFor = [Exception::class])
    fun uploadToSystemFolder(
        userId: Long,
        file: MultipartFile,
        folderType: SystemFolderManager.SystemFolderType,
        description: String? = null
    ): FileResponse {
        logger.info("上传文件到系统文件夹: userId={}, type={}, fileName={}", 
            userId, folderType.name, file.originalFilename)
        
        // 1. 获取或创建对应的系统文件夹
        val folderId = systemFolderManager.getOrCreateSystemFolder(userId, folderType)
        
        // 2. 调用原有的上传方法
        return uploadFile(
            userId = userId,
            file = file,
            request = FileUploadRequest(
                folderId = folderId,
                description = description
            )
        )
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
        val cacheKey = "${FileConstants.Cache.KEY_FILE_LIST}$userId:$folderId:$page:$size:$keyword:$sortBy:$order"
        redisUtil.get(cacheKey, FileListResponse::class.java)?.let {
            logger.debug("从缓存获取文件列表")
            return it
        }
        
        // 白名单验证排序字段
        val dbSortBy = FileConstants.SortFields.FILE_FIELDS[sortBy] ?: run {
            logger.warn("检测到无效的排序字段: {}, 使用默认排序", sortBy)
            FileConstants.FileManagement.DEFAULT_SORT_FIELD
        }
        
        // 构建查询条件
        val queryWrapper = QueryWrapper<FileMetadata>()
            .eq("user_id", userId)
            .eq("deleted", false)
            .apply {
                if (folderId != null) eq("folder_id", folderId) else isNull("folder_id")
                if (!keyword.isNullOrBlank()) {
                    and { qw -> qw.like("file_name", keyword).or().like("description", keyword) }
                }
            }
            .orderBy(true, order.equals("asc", ignoreCase = true), dbSortBy)
        
        // 分页查询
        val pageObj = Page<FileMetadata>(page.toLong(), size.toLong())
        val pageResult = fileMapper.selectPage(pageObj, queryWrapper)
        
        // 构建响应
        val response = FileListResponse(
            total = pageResult.total,
            page = page,
            size = size,
            files = pageResult.records.map { FileResponse.fromEntity(it) },
            currentFolder = folderId?.let { buildFolderBreadcrumb(it, userId) }
        )
        
        // 缓存结果
        redisUtil.set(cacheKey, response, FileConstants.Cache.EXPIRE_SHORT, TimeUnit.SECONDS)
        return response
    }
    
    /**
     * 构建文件夹面包屑导航
     */
    private fun buildFolderBreadcrumb(folderId: Long, userId: Long): FolderBreadcrumb? {
        val folder = folderMapper.selectById(folderId) ?: return null
        if (folder.deleted || folder.userId != userId) return null
        return FolderBreadcrumb(
            id = folder.id,
            name = folder.name,
            path = pathBuilderService.buildFolderPath(folderId, userId)
        )
    }
    
    /**
     * 获取文件详情
     */
    fun getFileInfo(fileId: Long, userId: Long): FileResponse {
        // 尝试从缓存获取
        val cacheKey = "${FileConstants.Cache.KEY_FILE_INFO}$fileId"
        redisUtil.get(cacheKey, FileResponse::class.java)?.let {
            logger.debug("从缓存获取文件信息: fileId={}", fileId)
            return it
        }
        
        val fileMetadata = validationService.validateAndGetFile(fileId, userId)
        val response = FileResponse.fromEntity(fileMetadata)
        
        // 缓存结果（文件信息变化较少，缓存时间可以更长）
        redisUtil.set(cacheKey, response, FileConstants.Cache.EXPIRE_LONG, TimeUnit.SECONDS)
        return response
    }
    
    /**
     * 更新文件信息
     */
    @Transactional(rollbackFor = [Exception::class])
    fun updateFile(fileId: Long, userId: Long, request: UpdateFileRequest): FileResponse {
        val file = validationService.validateAndGetFile(fileId, userId)
        var updated = false
        
        // 更新文件名
        request.fileName?.takeIf { it != file.fileName }?.let {
            file.fileName = it
            updated = true
        }
        
        // 更新文件夹
        if (request.folderId != file.folderId) {
            validationService.validateTargetFolder(request.folderId, userId)
            file.folderId = request.folderId
            updated = true
        }
        
        // 更新描述
        request.description?.takeIf { it != file.description }?.let {
            file.description = it
            updated = true
        }
        
        if (updated) {
            file.updateTime = LocalDateTime.now()
            fileMapper.updateById(file)
            clearFileCache(fileId, userId)
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
        validationService.validateFileOwnership(files, userId)
        
        // 验证目标文件夹
        validationService.validateTargetFolder(request.targetFolderId, userId)
        
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
        val file = validationService.validateAndGetFile(fileId, userId)
        
        // 软删除
        fileMapper.softDelete(fileId)
        
        // 清除缓存
        clearFileCache(fileId, userId)
        
        logger.info("文件已移入回收站: fileId={}, fileName={}", fileId, file.fileName)
        
        return true
    }
    
    /**
     * 批量操作文件（统一接口）
     */
    @Transactional(rollbackFor = [Exception::class])
    fun batchOperation(userId: Long, request: BatchOperationRequest): BatchOperationResponse {
        val successList = mutableListOf<String>()
        val failureList = mutableListOf<BatchOperationFailure>()
        
        // 验证文件归属
        val files = fileMapper.selectBatchIds(request.fileIds)
        val fileMap = files.associateBy { it.id!! }
        
        request.fileIds.forEach { fileId ->
            try {
                val file = fileMap[fileId]
                if (file == null) {
                    failureList.add(BatchOperationFailure(fileId.toString(), "文件不存在"))
                    return@forEach
                }
                
                if (file.userId != userId) {
                    failureList.add(BatchOperationFailure(fileId.toString(), "无权操作该文件"))
                    return@forEach
                }
                
                when (request.action) {
                    BatchAction.move -> {
                        if (request.targetFolderId == null) {
                            failureList.add(BatchOperationFailure(fileId.toString(), "目标文件夹不能为空"))
                            return@forEach
                        }
                        // 验证目标文件夹
                        if (request.targetFolderId != 0L) {
                            val targetFolder = folderMapper.selectById(request.targetFolderId)
                            if (targetFolder == null || targetFolder.deleted) {
                                failureList.add(BatchOperationFailure(fileId.toString(), "目标文件夹不存在"))
                                return@forEach
                            }
                            if (targetFolder.userId != userId) {
                                failureList.add(BatchOperationFailure(fileId.toString(), "无权访问目标文件夹"))
                                return@forEach
                            }
                        }
                        // 移动文件
                        fileMapper.batchMoveFiles(listOf(fileId), request.targetFolderId)
                        clearFileInfoCache(fileId)
                        successList.add(fileId.toString())
                    }
                    BatchAction.delete -> {
                        if (!file.deleted) {
                            fileMapper.softDelete(fileId)
                            clearFileInfoCache(fileId)
                        }
                        successList.add(fileId.toString())
                    }
                    BatchAction.restore -> {
                        if (file.deleted) {
                            fileMapper.restore(fileId)
                            clearFileInfoCache(fileId)
                        }
                        successList.add(fileId.toString())
                    }
                    BatchAction.tag -> {
                        // TODO: 实现标签功能
                        failureList.add(BatchOperationFailure(fileId.toString(), "标签功能暂未实现"))
                    }
                }
            } catch (e: Exception) {
                logger.error("批量操作文件失败: fileId={}, action={}", fileId, request.action, e)
                failureList.add(BatchOperationFailure(fileId.toString(), e.message ?: "操作失败"))
            }
        }
        
        // 清除缓存
        clearFileListCache(userId)
        
        logger.info("批量操作完成: userId={}, action={}, success={}, failed={}", 
            userId, request.action, successList.size, failureList.size)
        
        return BatchOperationResponse(
            success = successList,
            failed = failureList
        )
    }
    
    /**
     * 批量删除文件
     */
    @Transactional(rollbackFor = [Exception::class])
    fun batchDeleteFiles(userId: Long, request: BatchDeleteFilesRequest): Int {
        // 验证文件归属
        val files = fileMapper.selectBatchIds(request.fileIds)
        validationService.validateFileOwnership(files, userId)
        
        // 批量软删除
        val count = fileMapper.batchSoftDelete(request.fileIds)
        
        // 清除缓存
        clearFileListCache(userId)
        request.fileIds.forEach { clearFileInfoCache(it) }
        
        logger.info("批量删除文件成功: userId={}, count={}", userId, count)
        
        return count
    }
    
    /**
     * 清理过期的回收站文件
     */
    @Transactional(rollbackFor = [Exception::class])
    fun cleanupExpiredFiles(retentionDays: Int = FileConstants.FileManagement.RETENTION_DAYS): Int {
        val threshold = LocalDateTime.now().minusDays(retentionDays.toLong())
        
        val expiredFiles = fileMapper.selectList(
            QueryWrapper<FileMetadata>()
                .eq("deleted", true)
                .isNotNull("deleted_at")
                .le("deleted_at", threshold)
        )
        
        val cleanedCount = expiredFiles.count { file ->
            try {
                file.storedName?.let { storageService.deleteFile(it) }
                fileMapper.hardDelete(file.id!!)
                logger.info("清理过期文件: fileId={}, fileName={}", file.id, file.fileName)
                true
            } catch (e: Exception) {
                logger.error("清理文件失败: fileId={}, fileName={}", file.id, file.fileName, e)
                false
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
            totalSizeFormatted = FileSizeFormatter.format(totalSize),
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
     * 清除文件相关缓存（统一方法）
     */
    private fun clearFileCache(fileId: Long, userId: Long) {
        clearFileInfoCache(fileId)
        clearFileListCache(userId)
    }
    
    /**
     * 清除文件列表缓存
     */
    private fun clearFileListCache(userId: Long) {
        redisUtil.deleteByPattern("${FileConstants.Cache.KEY_FILE_LIST}$userId:*")
    }
    
    /**
     * 清除文件信息缓存
     */
    private fun clearFileInfoCache(fileId: Long) {
        redisUtil.delete("${FileConstants.Cache.KEY_FILE_INFO}$fileId")
    }
    
    /**
     * 清除所有缓存
     */
    fun clearAllCache() {
        logger.info("开始清除所有文件相关缓存")
        redisUtil.deleteByPattern("${FileConstants.Cache.KEY_FILE_LIST}*")
        redisUtil.deleteByPattern("${FileConstants.Cache.KEY_FILE_INFO}*")
        logger.info("所有文件相关缓存已清除")
    }
}
