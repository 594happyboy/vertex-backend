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
 */
data class CreateDocumentRequest(
    val title: String,
    val groupId: Long? = null,
    val type: String, // md or pdf
    val contentMd: String? = null
)

/**
 * 更新文档请求
 */
data class UpdateDocumentRequest(
    val title: String? = null,
    val groupId: Long? = null,
    val contentMd: String? = null,
    val status: String? = null,
    val sortIndex: Int? = null
)

/**
 * 文档列表项
 */
data class DocumentItem(
    val id: Long,
    val title: String,
    val type: String,
    val status: String,
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
    val status: String,
    val contentMd: String?,
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
                status = document.status,
                contentMd = document.contentMd,
                sortIndex = document.sortIndex,
                createdAt = document.createdAt,
                updatedAt = document.updatedAt
            )
        }
    }
}

/**
 * 文档列表查询参数
 */
data class DocumentQueryRequest(
    val q: String? = null,      // 搜索关键词
    val status: String? = null,  // 状态过滤
    val groupId: Long? = null,   // 分组过滤
    val page: Int = 1,           // 页码
    val size: Int = 20           // 每页大小
)

/**
 * 文档列表响应
 */
data class DocumentListResponse(
    val items: List<DocumentItem>,
    val total: Long
)

