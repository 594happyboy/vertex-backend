package com.zzy.file.service

import com.zzy.common.exception.BusinessException
import com.zzy.common.pagination.*
import com.zzy.file.dto.resource.FileResource
import com.zzy.file.entity.FileMetadata
import com.zzy.file.mapper.FileMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 回收站服务
 * @author ZZY
 * @date 2025-11-02
 */
@Service
class TrashService(
    private val fileMapper: FileMapper,
    private val storageService: StorageService
) {
    
    private val logger = LoggerFactory.getLogger(TrashService::class.java)
    
    companion object {
        private const val DEFAULT_RETENTION_DAYS = 30
    }
    
    /**
     * 获取回收站列表（游标分页）
     */
    fun getRecycleBinWithCursor(
        userId: Long,
        cursor: String?,
        limit: Int
    ): PaginatedResponse<FileResource> {
        logger.debug("获取回收站列表: userId={}, cursor={}, limit={}", userId, cursor, limit)
        
        val request = object : CursorPageRequest {
            override val cursor = cursor
            override val limit = limit
            override val sortField = "deleted_at"
            override val sortOrder = "desc"
            override val keyword: String? = null
            override val type: String? = null
        }
        
        return paginate(
            request = request,
            query = { params ->
                fileMapper.selectRecycleBinWithCursor(
                    userId = userId,
                    lastId = params.lastId,
                    lastDeletedAt = params.lastSortValue,
                    limit = params.limit
                )
            },
            mapper = { FileResource.fromEntity(it) },
            sortValueExtractor = { it.deletedAt?.toString() ?: "" }
        )
    }
    
    /**
     * 恢复文件（通过数字ID）
     */
    @Transactional(rollbackFor = [Exception::class])
    fun restoreFile(fileId: Long, userId: Long): Boolean {
        val file = fileMapper.selectByIdIncludeDeleted(fileId)
            ?: throw BusinessException(404, "文件不存在")
        
        if (file.userId != userId) {
            throw BusinessException(403, "无权恢复该文件")
        }
        
        if (!file.deleted) {
            logger.warn("文件未被删除，无需恢复: fileId={}", fileId)
            return false
        }
        
        fileMapper.restore(fileId)
        logger.info("文件已从回收站恢复: fileId={}, fileName={}", fileId, file.fileName)
        
        return true
    }
    
    /**
     * 恢复文件（通过公开ID - 对外接口）
     */
    @Transactional(rollbackFor = [Exception::class])
    fun restoreFileByPublicId(publicId: String, userId: Long): Boolean {
        val file = fileMapper.selectByPublicId(publicId)
            ?: throw BusinessException(404, "文件不存在")
        
        if (file.userId != userId) {
            throw BusinessException(403, "无权恢复该文件")
        }
        
        if (!file.deleted) {
            logger.warn("文件未被删除，无需恢复: publicId={}", publicId)
            return false
        }
        
        fileMapper.restore(file.id!!)
        logger.info("文件已从回收站恢复: publicId={}, fileName={}", publicId, file.fileName)
        
        return true
    }
    
    /**
     * 永久删除文件（通过数字ID）
     */
    @Transactional(rollbackFor = [Exception::class])
    fun permanentlyDeleteFile(fileId: Long, userId: Long): Boolean {
        val file = fileMapper.selectByIdIncludeDeleted(fileId)
            ?: throw BusinessException(404, "文件不存在")
        
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
        logger.info("文件永久删除成功: fileId={}, fileName={}", fileId, file.fileName)
        
        return true
    }
    
    /**
     * 永久删除文件（通过公开ID - 对外接口）
     */
    @Transactional(rollbackFor = [Exception::class])
    fun permanentlyDeleteFileByPublicId(publicId: String, userId: Long): Boolean {
        val file = fileMapper.selectByPublicId(publicId)
            ?: throw BusinessException(404, "文件不存在")
        
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
        
        // 从数据库物理删除（使用内部数字ID）
        fileMapper.hardDelete(file.id!!)
        logger.info("文件永久删除成功: publicId={}, fileName={}", publicId, file.fileName)
        
        return true
    }
    
    /**
     * 清空回收站
     */
    @Transactional(rollbackFor = [Exception::class])
    fun clearRecycleBin(userId: Long): Int {
        // 查询所有回收站文件
        val files = fileMapper.selectRecycleBinFiles(userId, 0, Long.MAX_VALUE)
        
        var deletedCount = 0
        files.forEach { file ->
            try {
                storageService.deleteFile(file.storedName ?: "")
                fileMapper.hardDelete(file.id!!)
                deletedCount++
            } catch (e: Exception) {
                logger.error("清空回收站失败: fileId={}, fileName={}", file.id, file.fileName, e)
            }
        }
        
        logger.info("清空回收站完成: userId={}, deletedCount={}", userId, deletedCount)
        return deletedCount
    }
}

