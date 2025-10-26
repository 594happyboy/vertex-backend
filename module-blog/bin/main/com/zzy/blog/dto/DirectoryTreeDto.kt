package com.zzy.blog.dto

import com.zzy.blog.entity.Document
import com.zzy.blog.entity.Group
import java.time.LocalDateTime

/**
 * 目录树相关 DTO
 * @author ZZY
 * @date 2025-10-18
 */

/**
 * 目录树节点类型
 */
enum class NodeType(val value: String) {
    GROUP("group"),
    DOCUMENT("document")
}

/**
 * 目录树节点（整合分组和文档）
 */
data class DirectoryTreeNode(
    val id: Long,
    val nodeType: NodeType,           // 节点类型：group 或 document
    val name: String,                  // 分组名或文档标题
    val parentId: Long? = null,        // 父节点ID（仅分组有）
    val groupId: Long? = null,         // 所属分组ID（仅文档有）
    val sortIndex: Int,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
    
    // 文档专属字段
    val type: String? = null,          // 文档类型: md/pdf
    
    // 子节点（可以包含分组和文档）
    var children: MutableList<DirectoryTreeNode>? = null
) {
    companion object {
        /**
         * 从分组实体创建节点
         */
        fun fromGroup(group: Group): DirectoryTreeNode {
            return DirectoryTreeNode(
                id = group.id!!,
                nodeType = NodeType.GROUP,
                name = group.name,
                parentId = group.parentId,
                sortIndex = group.sortIndex,
                createdAt = group.createdAt,
                updatedAt = group.updatedAt,
                children = mutableListOf()
            )
        }
        
        /**
         * 从文档实体创建节点
         */
        fun fromDocument(document: Document): DirectoryTreeNode {
            return DirectoryTreeNode(
                id = document.id!!,
                nodeType = NodeType.DOCUMENT,
                name = document.title,
                groupId = document.groupId,
                sortIndex = document.sortIndex,
                createdAt = document.createdAt,
                updatedAt = document.updatedAt,
                type = document.type
            )
        }
    }
}

/**
 * 目录树响应
 */
data class DirectoryTreeResponse(
    val tree: List<DirectoryTreeNode>,
    val cached: Boolean = false        // 标识数据是否来自缓存
)


