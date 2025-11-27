package com.zzy.blog.dto

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import com.zzy.blog.entity.Document
import com.zzy.blog.entity.Group
import java.time.LocalDateTime

/**
 * 目录树相关 DTO
 * @author ZZY
 * @date 2025-10-18
 */

/** 目录树节点类型 */
enum class NodeType(@get:JsonValue val value: String) {
    GROUP("group"),
    DOCUMENT("document");

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromValue(value: String?): NodeType {
            if (value == null) throw IllegalArgumentException("nodeType is required")
            return values().firstOrNull { it.value.equals(value, ignoreCase = true) || it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("unsupported nodeType: $value")
        }
    }
}

/**
 * 目录树节点（分组 + 文档统一视图）
 * orderIndex 表示同一父节点下的排序序号，实际排序规则为：
 *  - 先按 nodeType（GROUP 优先）
 *  - 再按 orderIndex
 */
data class DirectoryTreeNode(
    val id: Long,
    val nodeType: NodeType,
    val name: String,
    val parentId: Long? = null,        // 分组的父分组；文档则为所属分组
    val orderIndex: Int,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
    val type: String? = null,          // 文档类型: md/pdf
    var children: MutableList<DirectoryTreeNode>? = null
) {
    companion object {
        fun fromGroup(group: Group): DirectoryTreeNode {
            return DirectoryTreeNode(
                id = group.id!!,
                nodeType = NodeType.GROUP,
                name = group.name,
                parentId = group.parentId,
                orderIndex = group.sortIndex,
                createdAt = group.createdAt,
                updatedAt = group.updatedAt,
                children = mutableListOf()
            )
        }

        fun fromDocument(document: Document): DirectoryTreeNode {
            return DirectoryTreeNode(
                id = document.id!!,
                nodeType = NodeType.DOCUMENT,
                name = document.title,
                parentId = document.groupId,
                orderIndex = document.sortIndex,
                createdAt = document.createdAt,
                updatedAt = document.updatedAt,
                type = document.type
            )
        }
    }
}

/** 目录树响应 */
data class DirectoryTreeResponse(
    val tree: List<DirectoryTreeNode>,
    val cached: Boolean = false
)

/** 目录树重排请求条目 */
data class TreeReorderItem(
    val nodeId: Long,
    val nodeType: NodeType,
    val orderIndex: Int
)

/** 目录树重排请求体 */
data class TreeReorderRequest(
    val parentId: Long?,
    val items: List<TreeReorderItem>
)
