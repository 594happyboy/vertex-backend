package com.zzy.file.service

import com.zzy.common.exception.BusinessException
import com.zzy.common.util.RedisUtil
import com.zzy.file.constants.FileConstants
import com.zzy.file.util.FileSizeFormatter
import com.zzy.file.dto.*
import com.zzy.file.dto.pagination.CursorParams
import com.zzy.file.dto.pagination.CursorUtil
import com.zzy.file.dto.pagination.PaginatedResponse
import com.zzy.file.dto.resource.BaseResource
import com.zzy.file.dto.resource.FileResource
import com.zzy.file.dto.resource.FolderResource
import com.zzy.file.entity.FileFolder
import com.zzy.file.entity.FileMetadata
import com.zzy.file.mapper.FileMapper
import com.zzy.file.mapper.FolderMapper
import com.zzy.file.service.common.ValidationService
import com.zzy.file.service.common.PathBuilderService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 文件夹浏览器服务（重构版）
 * 实现游标分页、混合资源查询等功能
 * @author ZZY
 * @date 2025-11-02
 */
@Service
class FolderExplorerService(
    private val folderMapper: FolderMapper,
    private val fileMapper: FileMapper,
    private val redisUtil: RedisUtil,
    private val validationService: ValidationService,
    private val pathBuilderService: PathBuilderService
) {
    
    private val logger = LoggerFactory.getLogger(FolderExplorerService::class.java)
    
    /**
     * 获取根目录信息
     */
    fun getRootFolder(userId: Long, includeChildren: Boolean = true): RootFolderResponse {
        logger.debug("获取根目录信息: userId={}, includeChildren={}", userId, includeChildren)
        
        return RootFolderResponse(
            childFolderCount = folderMapper.countRootFolders(userId),
            childFileCount = folderMapper.countRootFiles(userId),
            children = if (includeChildren) {
                getSubFolders(userId, null, null, FileConstants.Pagination.DEFAULT_LIMIT)
            } else null
        )
    }
    
    /**
     * 获取目录元信息（包含祖先路径）
     */
    fun getFolderInfo(folderId: Long, userId: Long): FolderInfoResponse {
        logger.debug("获取文件夹元信息: folderId={}, userId={}", folderId, userId)
        
        val folder = validationService.validateAndGetFolder(folderId, userId)
        val totalSize = folderMapper.calculateTotalSize(folderId)
        
        return FolderInfoResponse(
            id = folder.id.toString(),
            name = folder.name,
            parentId = folder.parentId?.toString(),
            childFolderCount = folderMapper.countSubFoldersByFolderId(folderId),
            childFileCount = folderMapper.countFilesByFolderId(folderId),
            color = folder.color,
            ancestors = pathBuilderService.buildAncestors(folderId, userId),
            statistics = FolderStatistics(
                totalSize = totalSize,
                totalSizeFormatted = FileSizeFormatter.format(totalSize)
            ),
            createdAt = folder.createdAt?.toString() ?: "",
            updatedAt = folder.updatedAt?.toString() ?: ""
        )
    }
    
    /**
     * 获取目录子项（文件夹+文件混合，支持游标分页）
     */
    fun getFolderChildren(
        folderId: Long?,
        userId: Long,
        request: FolderChildrenRequest
    ): PaginatedResponse<BaseResource> {
        logger.debug("获取目录子项: folderId={}, userId={}, request={}", folderId, userId, request)
        
        // 验证限制
        val limit = request.limit.coerceIn(
            FileConstants.Pagination.MIN_LIMIT,
            FileConstants.Pagination.MAX_LIMIT
        )
        
        // 解析游标
        var lastId: Long? = null
        var lastSortValue: String? = null
        
        if (request.cursor != null) {
            val cursorParams = CursorUtil.decodeCursor(request.cursor)
            if (cursorParams != null) {
                // 验证游标参数是否匹配当前请求
                if (!CursorUtil.validateCursorParams(
                        cursorParams,
                        request.orderBy,
                        request.order,
                        request.keyword,
                        request.type
                    )
                ) {
                    throw BusinessException(410, "游标已失效，请重新请求")
                }
                lastId = cursorParams.lastId
                lastSortValue = cursorParams.lastSortValue
            }
        }
        
        // 查询文件夹和文件
        val items = mutableListOf<BaseResource>()
        val sortField = mapSortField(request.orderBy)
        
        when (request.type) {
            "folder" -> {
                // 仅查询文件夹
                val folders = folderMapper.selectFoldersWithCursor(
                    userId = userId,
                    parentId = folderId,
                    keyword = request.keyword,
                    sortField = sortField,
                    sortOrder = request.order,
                    lastId = lastId,
                    lastSortValue = lastSortValue,
                    limit = limit + 1 // 多查一个用于判断是否还有更多
                )
                
                // 填充统计信息
                folders.forEach { folder ->
                    folder.fileCount = folderMapper.countFilesByFolderId(folder.id!!)
                    folder.subFolderCount = folderMapper.countSubFoldersByFolderId(folder.id!!)
                }
                
                items.addAll(folders.take(limit).map { FolderResource.fromEntity(it, userId) })
                
                // 判断是否还有更多
                val hasMore = folders.size > limit
                val nextCursor = if (hasMore && items.isNotEmpty()) {
                    val lastItem = folders[limit - 1]
                    encodeCursor(lastItem.id!!, getSortValue(lastItem, request.orderBy), request)
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
            "file" -> {
                // 仅查询文件
                val files = fileMapper.selectFilesWithCursor(
                    userId = userId,
                    folderId = folderId,
                    keyword = request.keyword,
                    sortField = mapFileSortField(request.orderBy),
                    sortOrder = request.order,
                    lastId = lastId,
                    lastSortValue = lastSortValue,
                    limit = limit + 1
                )
                
                items.addAll(files.take(limit).map { FileResource.fromEntity(it) })
                
                val hasMore = files.size > limit
                val nextCursor = if (hasMore && items.isNotEmpty()) {
                    val lastItem = files[limit - 1]
                    encodeCursor(lastItem.id!!, getFileSortValue(lastItem, request.orderBy), request)
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
            else -> {
                // 混合查询（文件夹优先）
                return getMixedChildren(folderId, userId, request, lastId, lastSortValue, limit)
            }
        }
    }
    
    /**
     * 获取子文件夹（树节点懒加载）
     */
    fun getSubFolders(
        userId: Long,
        parentId: Long?,
        cursor: String?,
        limit: Int = 100
    ): PaginatedResponse<FolderResource> {
        logger.debug("获取子文件夹: userId={}, parentId={}, cursor={}", userId, parentId, cursor)
        
        val validLimit = limit.coerceIn(
            FileConstants.Pagination.MIN_LIMIT,
            FileConstants.Pagination.MAX_LIMIT
        )
        
        // 解析游标
        var lastId: Long? = null
        var lastSortValue: String? = null
        
        if (cursor != null) {
            val cursorParams = CursorUtil.decodeCursor(cursor)
            if (cursorParams != null) {
                lastId = cursorParams.lastId
                lastSortValue = cursorParams.lastSortValue
            }
        }
        
        // 查询子文件夹（按名称排序）
        val folders = folderMapper.selectFoldersWithCursor(
            userId = userId,
            parentId = parentId,
            keyword = null,
            sortField = "name",
            sortOrder = "asc",
            lastId = lastId,
            lastSortValue = lastSortValue,
            limit = validLimit + 1
        )
        
        // 填充统计信息
        folders.forEach { folder ->
            folder.fileCount = folderMapper.countFilesByFolderId(folder.id!!)
            folder.subFolderCount = folderMapper.countSubFoldersByFolderId(folder.id!!)
        }
        
        val items = folders.take(validLimit).map { FolderResource.fromEntity(it, userId) }
        val hasMore = folders.size > validLimit
        
        val nextCursor = if (hasMore && items.isNotEmpty()) {
            val lastItem = folders[validLimit - 1]
            CursorUtil.encodeCursor(
                CursorParams(
                    lastId = lastItem.id!!,
                    lastSortValue = lastItem.name,
                    sortField = "name",
                    sortOrder = "asc"
                )
            )
        } else {
            null
        }
        
        return PaginatedResponse.of(
            items = items,
            limit = validLimit,
            nextCursor = nextCursor,
            hasMore = hasMore,
            total = if (!hasMore) items.size.toLong() else null
        )
    }
    
    /**
     * 搜索目录内容
     */
    fun searchInFolder(
        folderId: Long?,
        userId: Long,
        request: FolderChildrenRequest
    ): PaginatedResponse<BaseResource> {
        logger.info("搜索目录: folderId={}, userId={}, keyword={}", folderId, userId, request.keyword)
        
        if (request.keyword.isNullOrBlank()) {
            throw BusinessException(400, "搜索关键词不能为空")
        }
        
        // 搜索逻辑与 getFolderChildren 类似，但可以扩展更复杂的搜索条件
        return getFolderChildren(folderId, userId, request)
    }
    
    /**
     * 混合查询文件夹和文件（文件夹优先）
     */
    private fun getMixedChildren(
        folderId: Long?,
        userId: Long,
        request: FolderChildrenRequest,
        lastId: Long?,
        lastSortValue: String?,
        limit: Int
    ): PaginatedResponse<BaseResource> {
        val items = mutableListOf<BaseResource>()
        val sortField = mapSortField(request.orderBy)
        
        // 先查询文件夹
        val folders = folderMapper.selectFoldersWithCursor(
            userId = userId,
            parentId = folderId,
            keyword = request.keyword,
            sortField = sortField,
            sortOrder = request.order,
            lastId = lastId,
            lastSortValue = lastSortValue,
            limit = limit + 1
        )
        
        // 填充文件夹统计信息
        folders.forEach { folder ->
            folder.fileCount = folderMapper.countFilesByFolderId(folder.id!!)
            folder.subFolderCount = folderMapper.countSubFoldersByFolderId(folder.id!!)
        }
        
        items.addAll(folders.take(limit).map { FolderResource.fromEntity(it, userId) })
        
        // 如果文件夹不足 limit，再查询文件
        if (items.size < limit) {
            val remainingLimit = limit - items.size
            val files = fileMapper.selectFilesWithCursor(
                userId = userId,
                folderId = folderId,
                keyword = request.keyword,
                sortField = mapFileSortField(request.orderBy),
                sortOrder = request.order,
                lastId = null,
                lastSortValue = null,
                limit = remainingLimit + 1
            )
            
            items.addAll(files.take(remainingLimit).map { FileResource.fromEntity(it) })
            
            val hasMoreFiles = files.size > remainingLimit
            val hasMore = folders.size > limit || hasMoreFiles
            
            val nextCursor = if (hasMore && items.isNotEmpty()) {
                val lastItem = if (hasMoreFiles) {
                    val lastFile = files[remainingLimit - 1]
                    encodeCursor(lastFile.id!!, getFileSortValue(lastFile, request.orderBy), request)
                } else {
                    val lastFolder = folders.last()
                    encodeCursor(lastFolder.id!!, getSortValue(lastFolder, request.orderBy), request)
                }
                lastItem
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
        
        // 只有文件夹
        val hasMore = folders.size > limit
        val nextCursor = if (hasMore) {
            val lastFolder = folders[limit - 1]
            encodeCursor(lastFolder.id!!, getSortValue(lastFolder, request.orderBy), request)
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
     * 编码游标
     */
    private fun encodeCursor(lastId: Long, lastSortValue: String, request: FolderChildrenRequest): String {
        return CursorUtil.encodeCursor(
            CursorParams(
                lastId = lastId,
                lastSortValue = lastSortValue,
                sortField = request.orderBy,
                sortOrder = request.order,
                keyword = request.keyword,
                type = request.type
            )
        )
    }
    
    /**
     * 获取文件夹的排序字段值
     */
    private fun getSortValue(folder: FileFolder, orderBy: String): String {
        return when (orderBy) {
            "name" -> folder.name
            "updatedAt" -> folder.updatedAt?.toString() ?: ""
            else -> folder.name
        }
    }
    
    /**
     * 获取文件的排序字段值
     */
    private fun getFileSortValue(file: FileMetadata, orderBy: String): String {
        return when (orderBy) {
            "name" -> file.fileName ?: ""
            "size" -> file.fileSize?.toString() ?: "0"
            "updatedAt" -> file.updateTime?.toString() ?: ""
            else -> file.fileName ?: ""
        }
    }
    
    /**
     * 映射排序字段（文件夹）
     */
    private fun mapSortField(orderBy: String): String = 
        FileConstants.SortFields.FOLDER_FIELDS[orderBy] ?: "name"
    
    /**
     * 映射排序字段（文件）
     */
    private fun mapFileSortField(orderBy: String): String = 
        FileConstants.SortFields.FILE_FIELDS[orderBy] ?: "file_name"
}

