package com.zzy.file.dto

import com.zzy.common.pagination.CursorPageRequest
import com.zzy.common.pagination.PaginatedResponse
import com.zzy.file.dto.resource.BaseResource
import com.zzy.file.dto.resource.FolderResource
import com.zzy.file.dto.resource.FileResource

/**
 * 文件夹浏览器相关DTO（重构版）
 * @author ZZY
 * @date 2025-11-02
 */

/** 根目录响应DTO */
data class RootFolderResponse(
    val id: String = "root",
    val type: String = "folder",
    val name: String = "我的文件",
    val childFolderCount: Int,
    val childFileCount: Int,
    val children: PaginatedResponse<FolderResource>? = null
)

/** 分离式目录内容响应（文件夹+文件） */
data class FolderChildrenResponse(
    /** 文件夹列表（当前页） */
    val folders: List<FolderResource>,
    
    /** 文件列表（当前页） */
    val files: List<FileResource>,
    
    /** 分页信息 */
    val pagination: ChildrenPaginationInfo
)

/** 目录内容分页信息 */
data class ChildrenPaginationInfo(
    /** 每页大小 */
    val limit: Int,
    
    /** 下一页游标 */
    val nextCursor: String?,
    
    /** 是否有更多数据 */
    val hasMore: Boolean,
    
    /** 统计信息 */
    val stats: ChildrenStats
)

/** 目录内容统计信息 */
data class ChildrenStats(
    /** 文件夹总数 */
    val totalFolders: Long,
    
    /** 文件总数 */
    val totalFiles: Long
)

/** 目录子项查询请求参数 */
data class FolderChildrenRequest(
    override val cursor: String? = null,
    override val limit: Int = 50,
    override val keyword: String? = null,
    val orderBy: String = "name",  // name | size | updatedAt | type
    val order: String = "asc",      // asc | desc
    override val type: String? = "all"        // all | folder | file
) : CursorPageRequest {
    override val sortField: String get() = orderBy
    override val sortOrder: String get() = order
}

/** 目录元信息响应DTO（包含祖先路径） */
data class FolderInfoResponse(
    val id: String,
    val type: String = "folder",
    val name: String,
    val parentId: String?,
    val childFolderCount: Int,
    val childFileCount: Int,
    val color: String?,
    val ancestors: List<FolderAncestor>,
    val statistics: FolderStatistics?,
    val createdAt: String,
    val updatedAt: String
)

/** 祖先路径项 */
data class FolderAncestor(
    val id: String,
    val name: String
)

/** 文件夹统计信息 */
data class FolderStatistics(
    val totalSize: Long,
    val totalSizeFormatted: String
)

/** 批量操作请求DTO */
data class BatchOperationRequest(
    val action: BatchAction,         // move | delete | restore | tag
    val fileIds: List<Long>,
    val targetFolderId: Long? = null, // move时必需
    val tags: List<String>? = null    // tag时必需
)

/** 批量操作类型 */
enum class BatchAction {
    move, delete, restore, tag
}

/** 批量操作响应DTO */
data class BatchOperationResponse(
    val success: List<String>,
    val failed: List<BatchOperationFailure>
)

/** 批量操作失败项 */
data class BatchOperationFailure(
    val id: String,
    val reason: String
)

