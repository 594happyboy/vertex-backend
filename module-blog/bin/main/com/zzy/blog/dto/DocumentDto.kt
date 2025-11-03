package com.zzy.blog.dto

import com.zzy.blog.entity.Document
import java.time.LocalDateTime

/**
 * 文档相关 DTO
 * @author ZZY
 * @date 2025-10-18
 */

/**
 * 创建文档请求
 * 注意：文件通过MultipartFile上传，不在此DTO中
 */
data class CreateDocumentRequest(
    val title: String,
    val groupId: Long? = null
    // type 将从上传的文件自动推断
    // file 通过 @RequestParam("file") file: MultipartFile 传递
)

/**
 * 更新文档请求
 */
data class UpdateDocumentRequest(
    val title: String? = null,
    val groupId: Long? = null,
    val sortIndex: Int? = null
    // 文件更新通过单独的接口处理
)

/**
 * 文档列表项
 */
data class DocumentItem(
    val id: Long,
    val title: String,
    val type: String,
    val groupId: Long?,
    val sortIndex: Int,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?
)

/**
 * 文档详情
 */
data class DocumentDetail(
    val id: Long,
    val userId: Long,
    val groupId: Long?,
    val title: String,
    val type: String,
    val fileId: Long?,
    val filePath: String?,
    val sortIndex: Int,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?
) {
    companion object {
        fun fromEntity(document: Document): DocumentDetail {
            return DocumentDetail(
                id = document.id!!,
                userId = document.userId,
                groupId = document.groupId,
                title = document.title,
                type = document.type,
                fileId = document.fileId,
                filePath = document.filePath,
                sortIndex = document.sortIndex,
                createdAt = document.createdAt,
                updatedAt = document.updatedAt
            )
        }
    }
}

/**
 * 文档列表查询参数（游标分页）
 */
data class DocumentQueryRequest(
    val q: String? = null,           // 搜索关键词
    val groupId: Long? = null,       // 分组过滤
    val cursor: String? = null,      // 游标
    val limit: Int = 20,             // 每页大小
    val sortBy: String = "default",  // 排序字段：default, title, createdAt, updatedAt
    val order: String = "asc"        // 排序方向：asc, desc
)

/**
 * 文档列表响应（已废弃，使用 PaginatedResponse<DocumentItem> 替代）
 */
@Deprecated("使用 com.zzy.common.pagination.PaginatedResponse<DocumentItem> 替代")
data class DocumentListResponse(
    val items: List<DocumentItem>,
    val total: Long
)

/**
 * 批量上传请求参数（通过query parameter传递）
 * 文件通过MultipartFile上传
 */
data class BatchUploadRequest(
    val parentGroupId: Long? = null  // 父分组ID，null表示根目录
)

/**
 * 批量上传结果项
 */
data class BatchUploadResultItem(
    val type: String,  // group 或 document
    val name: String,
    val path: String,  // 在zip中的路径
    val id: Long?,     // 创建后的ID
    val success: Boolean,
    val message: String? = null
)

/**
 * 批量上传响应
 */
data class BatchUploadResponse(
    val success: Boolean,
    val totalFiles: Int,
    val totalFolders: Int,
    val successCount: Int,
    val failedCount: Int,
    val items: List<BatchUploadResultItem>,
    val message: String
)

