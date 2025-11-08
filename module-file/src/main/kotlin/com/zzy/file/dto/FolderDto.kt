package com.zzy.file.dto

import com.zzy.file.entity.FileFolder
import com.zzy.file.util.DateFormatter
import com.zzy.file.util.FileSizeFormatter

/**
 * 文件夹DTO
 * @author ZZY
 * @date 2025-10-23
 */

/** 
 * 文件夹响应DTO
 * 注意：只返回公开ID，不暴露内部数据库ID
 */
data class FolderResponse(
    val id: String,  // 公开ID（对外暴露）
    val name: String,
    val parentId: String?,  // 父文件夹公开ID（对外暴露）
    val sortIndex: Int,
    val color: String?,
    val description: String?,
    val fileCount: Int = 0,
    val subFolderCount: Int = 0,
    val totalSize: Long = 0,
    val totalSizeFormatted: String = "0 B",
    val createdAt: String,
    val updatedAt: String,
    val children: List<FolderResponse>? = null
) {
    companion object {
        /**
         * 从实体转换为DTO
         * 注意：如果需要 parentId，应该在 Service 层查询父文件夹的 publicId
         */
        fun fromEntity(entity: FileFolder): FolderResponse {
            return FolderResponse(
                id = entity.publicId ?: "",  // 使用公开ID
                name = entity.name,
                parentId = null,  // TODO: 需要在 Service 层查询父文件夹的 publicId
                sortIndex = entity.sortIndex,
                color = entity.color,
                description = entity.description,
                fileCount = entity.fileCount ?: 0,
                subFolderCount = entity.subFolderCount ?: 0,
                totalSize = entity.totalSize ?: 0,
                totalSizeFormatted = FileSizeFormatter.format(entity.totalSize ?: 0),
                createdAt = DateFormatter.format(entity.createdAt),
                updatedAt = DateFormatter.format(entity.updatedAt),
                children = entity.children?.map { fromEntity(it) }
            )
        }
    }
}

/** 创建文件夹请求DTO */
data class CreateFolderRequest(
    val name: String,
    val parentId: String? = null,  // 父文件夹公开ID
    val color: String? = null,
    val description: String? = null
)

/** 更新文件夹请求DTO */
data class UpdateFolderRequest(
    val name: String? = null,
    val parentId: String? = null,  // 父文件夹公开ID
    val color: String? = null,
    val description: String? = null,
    val sortIndex: Int? = null
)

/** 移动文件夹请求DTO */
data class MoveFolderRequest(
    val targetParentId: String?  // 目标父文件夹公开ID，null表示移动到根目录
)

/** 批量排序请求DTO */
data class BatchSortFoldersRequest(
    val items: List<FolderSortItem>
)

data class FolderSortItem(
    val id: String,  // 文件夹公开ID
    val sortIndex: Int
)

/** 文件夹树响应（包含完整树形结构） */
data class FolderTreeResponse(
    val rootFolders: List<FolderTreeNode>,
    val totalFolders: Int,
    val totalFiles: Int,
    val totalSize: Long,
    val totalSizeFormatted: String
)

/** 文件夹路径响应DTO（面包屑导航） */
data class FolderPathResponse(
    val path: List<FolderPathItem>
)

/** 文件夹路径项 */
data class FolderPathItem(
    val id: String,  // 文件夹公开ID
    val name: String
)

/**
 * 文件夹树节点DTO
 * 用于树形结构响应，包含children，不包含parentId
 */
data class FolderTreeNode(
    val id: String,
    val name: String,
    val sortIndex: Int,
    val color: String?,
    val description: String?,
    val fileCount: Int = 0,
    val subFolderCount: Int = 0,
    val totalSize: Long = 0,
    val totalSizeFormatted: String = "0 B",
    val createdAt: String,
    val updatedAt: String,
    val children: List<FolderTreeNode> = emptyList()
) {
    companion object {
        /**
         * 从实体转换为树节点
         */
        fun fromEntity(entity: FileFolder): FolderTreeNode {
            return FolderTreeNode(
                id = entity.publicId ?: "",
                name = entity.name,
                sortIndex = entity.sortIndex,
                color = entity.color,
                description = entity.description,
                fileCount = entity.fileCount ?: 0,
                subFolderCount = entity.subFolderCount ?: 0,
                totalSize = entity.totalSize ?: 0,
                totalSizeFormatted = FileSizeFormatter.format(entity.totalSize ?: 0),
                createdAt = DateFormatter.format(entity.createdAt),
                updatedAt = DateFormatter.format(entity.updatedAt),
                children = entity.children?.map { fromEntity(it) } ?: emptyList()
            )
        }
    }
}

/**
 * 文件夹详情DTO
 * 用于单个文件夹详情响应，包含parentId，不包含children
 */
data class FolderDetail(
    val id: String,
    val name: String,
    val parentId: String?,
    val sortIndex: Int,
    val color: String?,
    val description: String?,
    val fileCount: Int = 0,
    val subFolderCount: Int = 0,
    val totalSize: Long = 0,
    val totalSizeFormatted: String = "0 B",
    val createdAt: String,
    val updatedAt: String
) {
    companion object {
        /**
         * 从实体转换为详情DTO
         */
        fun fromEntity(entity: FileFolder, parentPublicId: String? = null): FolderDetail {
            return FolderDetail(
                id = entity.publicId ?: "",
                name = entity.name,
                parentId = parentPublicId,
                sortIndex = entity.sortIndex,
                color = entity.color,
                description = entity.description,
                fileCount = entity.fileCount ?: 0,
                subFolderCount = entity.subFolderCount ?: 0,
                totalSize = entity.totalSize ?: 0,
                totalSizeFormatted = FileSizeFormatter.format(entity.totalSize ?: 0),
                createdAt = DateFormatter.format(entity.createdAt),
                updatedAt = DateFormatter.format(entity.updatedAt)
            )
        }
    }
}

