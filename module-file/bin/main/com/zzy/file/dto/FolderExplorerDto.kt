package com.zzy.file.dto

import com.zzy.file.dto.pagination.PaginatedResponse
import com.zzy.file.dto.resource.BaseResource
import com.zzy.file.dto.resource.FolderResource

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

/** 目录子项查询请求参数 */
data class FolderChildrenRequest(
    val cursor: String? = null,
    val limit: Int = 50,
    val keyword: String? = null,
    val orderBy: String = "name",  // name | size | updatedAt | type
    val order: String = "asc",      // asc | desc
    val type: String = "all"        // all | folder | file
)

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

