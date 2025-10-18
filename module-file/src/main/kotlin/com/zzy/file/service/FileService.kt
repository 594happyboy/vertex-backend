package com.zzy.file.service

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.extension.plugins.pagination.Page
import com.zzy.file.dto.FileListResponse
import com.zzy.file.dto.FileResponse
import com.zzy.file.entity.FileMetadata
import com.zzy.common.exception.FileNotFoundException
import com.zzy.common.exception.FileSizeExceededException
import com.zzy.common.exception.FileTypeNotSupportedException
import com.zzy.common.exception.FileUploadException
import com.zzy.file.mapper.FileMapper
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
 * 文件服务
 * @author ZZY
 * @date 2025-10-09
 */
@Service
class FileService(
    private val fileMapper: FileMapper,
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
        
        /**
         * 排序字段白名单映射表（驼峰 -> 数据库字段名）
         * 优点：
         * 1. 安全：白名单机制，防止SQL注入
         * 2. 可维护：字段名集中管理
         * 3. 清晰：明确哪些字段可以排序
         */
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
    fun uploadFile(file: MultipartFile, userId: Long? = null): FileResponse {
        // 1. 验证文件
        validateFile(file)
        
        // 2. 计算MD5（用于秒传）
        val md5 = FileUtil.calculateMd5(file)
        
        // 3. 检查是否存在相同文件（秒传）
        val existingFile = fileMapper.selectByMd5(md5)
        if (existingFile != null && existingFile.status == 1) {
            logger.info("文件已存在，执行秒传: {}", file.originalFilename)
            return FileResponse.fromEntity(existingFile)
        }
        
        // 4. 生成存储文件名
        val originalFilename = file.originalFilename ?: "unknown"
        val storedName = FileUtil.generateUniqueFileName(originalFilename)
        val fileExtension = FileUtil.getFileExtension(originalFilename)
        
        // 5. 上传到MinIO
        try {
            storageService.uploadFile(file, storedName)
        } catch (e: Exception) {
            logger.error("文件上传到存储失败", e)
            throw FileUploadException("文件上传失败")
        }
        
        // 6. 保存元数据到数据库
        val fileMetadata = FileMetadata(
            fileName = originalFilename,
            storedName = storedName,
            fileSize = file.size,
            fileType = file.contentType ?: "application/octet-stream",
            fileExtension = fileExtension,
            filePath = storedName,
            fileMd5 = md5,
            uploadTime = LocalDateTime.now(),
            updateTime = LocalDateTime.now(),
            userId = userId
        )
        
        try {
            fileMapper.insert(fileMetadata)
            logger.info("文件元数据保存成功: {}", originalFilename)
        } catch (e: Exception) {
            // 如果数据库保存失败，删除已上传的文件
            storageService.deleteFile(storedName)
            logger.error("文件元数据保存失败", e)
            throw FileUploadException("文件信息保存失败")
        }
        
        // 7. 清除缓存
        clearFileListCache()
        
        return FileResponse.fromEntity(fileMetadata)
    }
    
    /**
     * 获取文件列表（分页）
     */
    fun getFileList(
        page: Int = 1,
        size: Int = 10,
        keyword: String? = null,
        sortBy: String = "uploadTime",
        order: String = "desc"
    ): FileListResponse {
        // 尝试从缓存获取
        val cacheKey = "$CACHE_KEY_FILE_LIST$page:$size:$keyword:$sortBy:$order"
        val cached = redisUtil.get(cacheKey, FileListResponse::class.java)
        if (cached != null) {
            logger.debug("从缓存获取文件列表")
            return cached
        }
        
        // 白名单验证：从映射表获取数据库字段名
        val dbSortBy = SORTABLE_FIELDS[sortBy] ?: run {
            logger.warn("检测到无效的排序字段: {}, 使用默认排序字段: {}", sortBy, DEFAULT_SORT_FIELD)
            DEFAULT_SORT_FIELD
        }
        
        // 构建查询条件
        val queryWrapper = QueryWrapper<FileMetadata>()
            .eq("status", 1)
            .apply {
                if (!keyword.isNullOrBlank()) {
                    like("file_name", keyword)
                }
            }
            .orderBy(true, order.equals("asc", ignoreCase = true), dbSortBy)
        
        // 分页查询
        val pageObj = Page<FileMetadata>(page.toLong(), size.toLong())
        val pageResult = fileMapper.selectPage(pageObj, queryWrapper)
        
        // 转换为响应对象
        val fileResponses = pageResult.records.map { FileResponse.fromEntity(it) }
        val response = FileListResponse(
            total = pageResult.total,
            page = page,
            size = size,
            files = fileResponses
        )
        
        // 缓存结果
        redisUtil.set(cacheKey, response, CACHE_EXPIRE_TIME, TimeUnit.SECONDS)
        
        return response
    }
    
    /**
     * 获取文件详情
     */
    fun getFileInfo(id: Long): FileResponse {
        // 尝试从缓存获取
        val cacheKey = "$CACHE_KEY_FILE_INFO$id"
        val cached = redisUtil.get(cacheKey, FileResponse::class.java)
        if (cached != null) {
            logger.debug("从缓存获取文件信息: {}", id)
            return cached
        }
        
        val fileMetadata = fileMapper.selectById(id)
            ?: throw FileNotFoundException("文件不存在")
        
        if (fileMetadata.status == 0) {
            throw FileNotFoundException("文件已被删除")
        }
        
        val response = FileResponse.fromEntity(fileMetadata)
        
        // 缓存结果
        redisUtil.set(cacheKey, response, CACHE_EXPIRE_TIME * 2, TimeUnit.SECONDS)
        
        return response
    }
    
    /**
     * 下载文件
     */
    fun downloadFile(id: Long): Pair<InputStream, FileMetadata> {
        val fileMetadata = fileMapper.selectById(id)
            ?: throw FileNotFoundException("文件不存在")
        
        if (fileMetadata.status == 0) {
            throw FileNotFoundException("文件已被删除")
        }
        
        // 从存储获取文件流
        val inputStream = storageService.downloadFile(fileMetadata.storedName ?: "")
        
        // 增加下载次数
        fileMapper.increaseDownloadCount(id)
        
        // 清除文件信息缓存
        redisUtil.delete("$CACHE_KEY_FILE_INFO$id")
        
        return Pair(inputStream, fileMetadata)
    }
    
    /**
     * 删除文件
     */
    @Transactional(rollbackFor = [Exception::class])
    fun deleteFile(id: Long): Boolean {
        val fileMetadata = fileMapper.selectById(id)
            ?: throw FileNotFoundException("文件不存在")
        
        // 逻辑删除数据库记录
        fileMetadata.status = 0
        fileMetadata.updateTime = LocalDateTime.now()
        fileMapper.updateById(fileMetadata)
        
        // 从存储删除文件
        storageService.deleteFile(fileMetadata.storedName ?: "")
        
        // 清除缓存
        redisUtil.delete("$CACHE_KEY_FILE_INFO$id")
        clearFileListCache()
        
        logger.info("文件删除成功: {}", fileMetadata.fileName)
        return true
    }
    
    /**
     * 批量删除文件
     */
    @Transactional(rollbackFor = [Exception::class])
    fun batchDeleteFiles(ids: List<Long>): Int {
        var count = 0
        ids.forEach { id ->
            try {
                deleteFile(id)
                count++
            } catch (e: Exception) {
                logger.error("删除文件失败: id={}", id, e)
            }
        }
        return count
    }
    
    /**
     * 验证文件
     */
    private fun validateFile(file: MultipartFile) {
        val filename = file.originalFilename ?: throw IllegalArgumentException("文件名不能为空")
        
        // 验证文件大小
        if (!FileUtil.validateFileSize(file.size, maxFileSize)) {
            throw FileSizeExceededException("文件大小超过限制，最大支持 ${FileUtil.formatFileSize(maxFileSize)}")
        }
        
        // 验证文件类型
        val allowedTypeList = allowedTypes.split(",").map { it.trim() }
        if (!FileUtil.validateFileType(filename, allowedTypeList)) {
            throw FileTypeNotSupportedException("不支持的文件类型，仅支持: $allowedTypes")
        }
    }
    
    /**
     * 清除文件列表缓存
     */
    private fun clearFileListCache() {
        redisUtil.deleteByPattern("$CACHE_KEY_FILE_LIST*")
    }
    
    /**
     * 清除所有文件相关缓存（管理功能）
     */
    fun clearAllCache() {
        logger.info("开始清除所有文件相关缓存")
        redisUtil.deleteByPattern("$CACHE_KEY_FILE_LIST*")
        redisUtil.deleteByPattern("$CACHE_KEY_FILE_INFO*")
        logger.info("所有文件相关缓存已清除")
    }
}

