package com.zzy.blog.dto

import com.zzy.blog.entity.Group
import java.time.LocalDateTime

/**
 * 分组相关 DTO
 * @author ZZY
 * @date 2025-10-18
 */

/**
 * 创建分组请求
 */
data class CreateGroupRequest(
    val name: String,
    val parentId: Long? = null
)

/**
 * 更新分组请求
 */
data class UpdateGroupRequest(
    val name: String? = null,
    val parentId: Long? = null,
    val sortIndex: Int? = null
)

/**
 * 分组响应（包含子分组）
 */
data class GroupResponse(
    val id: Long,
    val userId: Long,
    val name: String,
    val parentId: Long?,
    val sortIndex: Int,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
    var children: MutableList<GroupResponse>?
) {
    companion object {
        fun fromEntity(group: Group): GroupResponse {
            return GroupResponse(
                id = group.id!!,
                userId = group.userId,
                name = group.name,
                parentId = group.parentId,
                sortIndex = group.sortIndex,
                createdAt = group.createdAt,
                updatedAt = group.updatedAt,
                children = group.children?.map { fromEntity(it) }?.toMutableList()
            )
        }
    }
}

