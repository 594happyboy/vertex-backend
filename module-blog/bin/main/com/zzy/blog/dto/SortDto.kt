package com.zzy.blog.dto

/**
 * 排序相关 DTO
 * @author ZZY
 * @date 2025-10-18
 */

/**
 * 分组排序项
 */
data class GroupSortItem(
    val id: Long,
    val parentId: Long?,
    val sortIndex: Int
)

/**
 * 分组批量排序请求
 */
data class GroupSortRequest(
    val items: List<GroupSortItem>
)

/**
 * 文档排序项
 */
data class DocumentSortItem(
    val id: Long,
    val groupId: Long?,
    val sortIndex: Int
)

/**
 * 文档批量排序请求
 */
data class DocumentSortRequest(
    val items: List<DocumentSortItem>
)

