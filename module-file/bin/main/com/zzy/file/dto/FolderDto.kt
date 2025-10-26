package com.zzy.file.dto

import com.zzy.file.entity.FileFolder
import java.time.format.DateTimeFormatter

/**
 * 文件夹DTO
 * @author ZZY
 * @date 2025-10-23
 */

/** 文件夹响应DTO */
data class FolderResponse(
    val id: Long,
    val name: String,
    val parentId: Long?,
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
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        
        /**
         * 从实体转换为DTO
         */
        fun fromEntity(entity: FileFolder): FolderResponse {
            return FolderResponse(
                id = entity.id ?: 0,
                name = entity.name,
                parentId = entity.parentId,
                sortIndex = entity.sortIndex,
                color = entity.color,
                description = entity.description,
                fileCount = entity.fileCount ?: 0,
                subFolderCount = entity.subFolderCount ?: 0,
                totalSize = entity.totalSize ?: 0,
                totalSizeFormatted = formatFileSize(entity.totalSize ?: 0),
                createdAt = entity.createdAt?.format(DATE_FORMATTER) ?: "",
                updatedAt = entity.updatedAt?.format(DATE_FORMATTER) ?: "",
                children = entity.children?.map { fromEntity(it) }
            )
        }
        
        /**
         * 格式化文件大小
         */
        private fun formatFileSize(size: Long): String {
            return when {
                size < 1024 -> "$size B"
                size < 1024 * 1024 -> String.format("%.2f KB", size / 1024.0)
                size < 1024 * 1024 * 1024 -> String.format("%.2f MB", size / (1024.0 * 1024))
                else -> String.format("%.2f GB", size / (1024.0 * 1024 * 1024))
            }
        }
    }
}

/** 创建文件夹请求DTO */
data class CreateFolderRequest(
    val name: String,
    val parentId: Long? = null,
    val color: String? = null,
    val description: String? = null
)

/** 更新文件夹请求DTO */
data class UpdateFolderRequest(
    val name: String? = null,
    val parentId: Long? = null,
    val color: String? = null,
    val description: String? = null,
    val sortIndex: Int? = null
)

/** 移动文件夹请求DTO */
data class MoveFolderRequest(
    val targetParentId: Long?  // null表示移动到根目录
)

/** 批量排序请求DTO */
data class BatchSortFoldersRequest(
    val items: List<FolderSortItem>
)

data class FolderSortItem(
    val id: Long,
    val sortIndex: Int
)

/** 文件夹树响应（包含完整树形结构） */
data class FolderTreeResponse(
    val rootFolders: List<FolderResponse>,
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
    val id: Long,
    val name: String
)

