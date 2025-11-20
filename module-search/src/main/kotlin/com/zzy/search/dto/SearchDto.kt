package com.zzy.search.dto

import java.time.LocalDateTime

/**
 * 搜索结果项
 */
data class SearchResult(
    val docId: Long,
    val userId: Long,
    val groupId: Long?,
    val title: String,
    val snippet: String,
    val score: Double,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?
)

/**
 * 搜索请求参数
 */
data class SearchRequest(
    val q: String,
    val groupId: Long? = null,
    val page: Int = 1,
    val size: Int = 20
)

/**
 * 搜索响应
 */
data class SearchResponse(
    val items: List<SearchResultItem>,
    val page: Int,
    val size: Int,
    val total: Int? = null
)

/**
 * 搜索结果项（前端展示）
 */
data class SearchResultItem(
    val id: Long,
    val title: String,
    val groupId: Long?,
    val snippet: String,
    val score: Double,
    val createdAt: String?,
    val updatedAt: String?
)
