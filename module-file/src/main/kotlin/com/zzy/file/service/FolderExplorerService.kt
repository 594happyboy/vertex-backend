package com.zzy.file.service

import com.zzy.common.exception.BusinessException
import com.zzy.common.util.RedisUtil
import com.zzy.file.constants.FileConstants
import com.zzy.file.util.FileSizeFormatter
import com.zzy.file.dto.*
import com.zzy.common.pagination.*
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
     * 获取目录子项（文件夹+文件分离，支持游标分页）
     */
    fun getFolderChildren(
        folderId: Long?,
        userId: Long,
        request: FolderChildrenRequest
    ): FolderChildrenResponse {
        logger.debug("获取目录子项: folderId={}, userId={}, request={}", folderId, userId, request)
        
        // 解析游标
        val cursor = request.cursor?.let { 
            CursorUtil.decodeCursor(it)?.also { params ->
                if (!params.matches(request)) {
                    throw CursorExpiredException("游标已失效，请重新请求")
                }
            }
        }
        
        // 根据游标中的resourceType判断当前查询阶段
        val phase = cursor?.resourceType ?: "folder"
        
        return if (phase == "folder") {
            queryFolderPhase(folderId, userId, request, cursor)
        } else {
            queryFilePhase(folderId, userId, request, cursor)
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
        
        val request = object : CursorPageRequest {
            override val cursor = cursor
            override val limit = validLimit
            override val sortField = "name"
            override val sortOrder = "asc"
            override val keyword: String? = null
            override val type: String? = null
        }
        
        return paginate(
            request = request,
            query = { params ->
                folderMapper.selectFoldersWithCursor(
                    userId = userId,
                    parentId = parentId,
                    keyword = null,
                    sortField = "name",
                    sortOrder = "asc",
                    lastId = params.lastId,
                    lastSortValue = params.lastSortValue,
                    limit = params.limit
                ).also { folders ->
                    // 填充统计信息
                    folders.forEach { folder ->
                        folder.fileCount = folderMapper.countFilesByFolderId(folder.id!!)
                        folder.subFolderCount = folderMapper.countSubFoldersByFolderId(folder.id!!)
                    }
                }
            },
            mapper = { FolderResource.fromEntity(it) },
            sortValueExtractor = { it.name }
        )
    }
    
    /**
     * 搜索目录内容
     */
    fun searchInFolder(
        folderId: Long?,
        userId: Long,
        request: FolderChildrenRequest
    ): FolderChildrenResponse {
        logger.info("搜索目录: folderId={}, userId={}, keyword={}", folderId, userId, request.keyword)
        
        if (request.keyword.isNullOrBlank()) {
            throw BusinessException(400, "搜索关键词不能为空")
        }
        
        // 搜索逻辑与 getFolderChildren 相同
        return getFolderChildren(folderId, userId, request)
    }
    
    /**
     * 查询文件夹阶段
     */
    private fun queryFolderPhase(
        folderId: Long?,
        userId: Long,
        request: FolderChildrenRequest,
        cursor: CursorParams?
    ): FolderChildrenResponse {
        val limit = request.limit
        val sortField = mapSortField(request.orderBy)
        
        // 查询文件夹（多查一个用于判断hasMore）
        val folders = folderMapper.selectFoldersWithCursor(
            userId = userId,
            parentId = folderId,
            keyword = request.keyword,
            sortField = sortField,
            sortOrder = request.order,
            lastId = cursor?.lastId,
            lastSortValue = cursor?.lastSortValue,
            limit = limit + 1
        )
        
        val hasMoreFolders = folders.size > limit
        val folderItems = folders.take(limit)
        
        // 填充文件夹统计信息
        folderItems.forEach { folder ->
            folder.fileCount = folderMapper.countFilesByFolderId(folder.id!!)
            folder.subFolderCount = folderMapper.countSubFoldersByFolderId(folder.id!!)
        }
        
        val folderResources = folderItems.map { FolderResource.fromEntity(it) }
        
        if (hasMoreFolders) {
            // 还有更多文件夹，继续文件夹阶段
            val lastFolder = folderItems.last()
            val nextCursor = CursorUtil.encodeCursor(
                CursorParams(
                    lastId = lastFolder.id!!,
                    lastSortValue = getSortValue(lastFolder, request.orderBy),
                    sortField = request.sortField,
                    sortOrder = request.sortOrder,
                    keyword = request.keyword,
                    type = request.type,
                    resourceType = "folder"
                )
            )
            
            return FolderChildrenResponse(
                folders = folderResources,
                files = emptyList(),
                pagination = PaginationInfo(
                    limit = limit,
                    nextCursor = nextCursor,
                    hasMore = true,
                    stats = buildStats(folderId, userId)
                )
            )
        } else {
            // 文件夹查完了，开始查文件
            val remainingLimit = limit - folderResources.size
            
            val files = if (remainingLimit > 0) {
                fileMapper.selectFilesWithCursor(
                    userId = userId,
                    folderId = folderId,
                    keyword = request.keyword,
                    sortField = mapFileSortField(request.orderBy),
                    sortOrder = request.order,
                    lastId = null,
                    lastSortValue = null,
                    limit = remainingLimit + 1
                )
            } else {
                emptyList()
            }
            
            val hasMoreFiles = files.size > remainingLimit
            val fileItems = files.take(remainingLimit)
            val fileResources = fileItems.map { FileResource.fromEntity(it) }
            
            val nextCursor = if (hasMoreFiles) {
                val lastFile = fileItems.last()
                CursorUtil.encodeCursor(
                    CursorParams(
                        lastId = lastFile.id!!,
                        lastSortValue = getFileSortValue(lastFile, request.orderBy),
                        sortField = request.sortField,
                        sortOrder = request.sortOrder,
                        keyword = request.keyword,
                        type = request.type,
                        resourceType = "file"
                    )
                )
            } else if (remainingLimit == 0) {
                // 文件夹刚好填满limit，但需要查询是否有文件
                val hasFiles = fileMapper.selectFilesWithCursor(
                    userId = userId,
                    folderId = folderId,
                    keyword = request.keyword,
                    sortField = mapFileSortField(request.orderBy),
                    sortOrder = request.order,
                    lastId = null,
                    lastSortValue = null,
                    limit = 1
                ).isNotEmpty()
                
                if (hasFiles) {
                    CursorUtil.encodeCursor(
                        CursorParams(
                            lastId = 0,
                            lastSortValue = "",
                            sortField = request.sortField,
                            sortOrder = request.sortOrder,
                            keyword = request.keyword,
                            type = request.type,
                            resourceType = "file"
                        )
                    )
                } else {
                    null
                }
            } else {
                null
            }
            
            return FolderChildrenResponse(
                folders = folderResources,
                files = fileResources,
                pagination = PaginationInfo(
                    limit = limit,
                    nextCursor = nextCursor,
                    hasMore = hasMoreFiles || (remainingLimit == 0 && nextCursor != null),
                    stats = buildStats(folderId, userId)
                )
            )
        }
    }
    
    /**
     * 查询文件阶段
     */
    private fun queryFilePhase(
        folderId: Long?,
        userId: Long,
        request: FolderChildrenRequest,
        cursor: CursorParams?
    ): FolderChildrenResponse {
        val limit = request.limit
        
        // 纯文件查询
        val files = fileMapper.selectFilesWithCursor(
            userId = userId,
            folderId = folderId,
            keyword = request.keyword,
            sortField = mapFileSortField(request.orderBy),
            sortOrder = request.order,
            lastId = cursor?.lastId,
            lastSortValue = cursor?.lastSortValue,
            limit = limit + 1
        )
        
        val hasMore = files.size > limit
        val fileItems = files.take(limit)
        val fileResources = fileItems.map { FileResource.fromEntity(it) }
        
        val nextCursor = if (hasMore) {
            val lastFile = fileItems.last()
            CursorUtil.encodeCursor(
                CursorParams(
                    lastId = lastFile.id!!,
                    lastSortValue = getFileSortValue(lastFile, request.orderBy),
                    sortField = request.sortField,
                    sortOrder = request.sortOrder,
                    keyword = request.keyword,
                    type = request.type,
                    resourceType = "file"
                )
            )
        } else {
            null
        }
        
        return FolderChildrenResponse(
            folders = emptyList(),
            files = fileResources,
            pagination = PaginationInfo(
                limit = limit,
                nextCursor = nextCursor,
                hasMore = hasMore,
                stats = buildStats(folderId, userId)
            )
        )
    }
    
    /**
     * 构建统计信息
     */
    private fun buildStats(folderId: Long?, userId: Long): PaginationStats {
        val totalFolders = if (folderId == null) {
            folderMapper.countRootFolders(userId).toLong()
        } else {
            folderMapper.countSubFoldersByFolderId(folderId).toLong()
        }
        
        val totalFiles = if (folderId == null) {
            folderMapper.countRootFiles(userId).toLong()
        } else {
            folderMapper.countFilesByFolderId(folderId).toLong()
        }
        
        return PaginationStats(
            totalItems = totalFolders + totalFiles,
            totalFolders = totalFolders,
            totalFiles = totalFiles
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

