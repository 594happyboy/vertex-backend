package com.zzy.file.service

import com.zzy.common.exception.BusinessException
import com.zzy.file.dto.pagination.CursorParams
import com.zzy.file.dto.pagination.CursorUtil
import com.zzy.file.dto.pagination.PaginatedResponse
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
        
        // 解析游标
        var lastId: Long? = null
        var lastDeletedAt: String? = null
        
        if (cursor != null) {
            val cursorParams = CursorUtil.decodeCursor(cursor)
            if (cursorParams != null) {
                lastId = cursorParams.lastId
                lastDeletedAt = cursorParams.lastSortValue
            }
        }
        
        // 查询回收站文件（按删除时间倒序）
        val files = fileMapper.selectRecycleBinWithCursor(
            userId = userId,
            lastId = lastId,
            lastDeletedAt = lastDeletedAt,
            limit = limit + 1
        )
        
        // 判断是否还有更多
        val hasMore = files.size > limit
        val items = files.take(limit).map { FileResource.fromEntity(it) }
        
        // 生成下一页游标
        val nextCursor = if (hasMore && items.isNotEmpty()) {
            val lastItem = files[limit - 1]
            CursorUtil.encodeCursor(
                CursorParams(
                    lastId = lastItem.id!!,
                    lastSortValue = lastItem.deletedAt?.toString() ?: "",
                    sortField = "deleted_at",
                    sortOrder = "desc"
                )
            )
        } else {
            null
        }
        
        return PaginatedResponse.of(
            items = items,
            limit = limit,
            nextCursor = nextCursor,
            hasMore = hasMore
        )
    }
    
    /**
     * 恢复文件
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
     * 永久删除文件
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

