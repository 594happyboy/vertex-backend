package com.zzy.file.dto

import com.zzy.file.entity.FileMetadata
import java.time.format.DateTimeFormatter

/**
 * 文件DTO（重构版）
 * @author ZZY
 * @date 2025-10-23
 */

/** 文件响应DTO */
data class FileResponse(
    val id: Long,
    val fileName: String,
    val fileSize: Long,
    val fileSizeFormatted: String,
    val fileType: String,
    val fileExtension: String,
    val folderId: Long?,
    val folderName: String? = null,
    val tags: List<String>,
    val description: String?,
    val uploadTime: String,
    val updateTime: String,
    val downloadCount: Int,
    val downloadUrl: String,
    val previewUrl: String?,
    val deletedAt: String? = null,
    val daysUntilPermanentDeletion: Long? = null
) {
    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        
        /**
         * 从实体转换为DTO
         */
        fun fromEntity(entity: FileMetadata, retentionDays: Int = 30): FileResponse {
            // 计算剩余天数
            val daysUntilDeletion = entity.deletedAt?.let { deletedAt ->
                val daysPassed = java.time.Duration.between(deletedAt, java.time.LocalDateTime.now()).toDays()
                val remaining = retentionDays - daysPassed
                if (remaining > 0) remaining else 0
            }
            
            // 解析标签
            val tagList = entity.tags?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
            
            return FileResponse(
                id = entity.id ?: 0,
                fileName = entity.fileName ?: "",
                fileSize = entity.fileSize ?: 0,
                fileSizeFormatted = formatFileSize(entity.fileSize ?: 0),
                fileType = entity.fileType ?: "unknown",
                fileExtension = entity.fileExtension ?: "",
                folderId = entity.folderId,
                tags = tagList,
                description = entity.description,
                uploadTime = entity.uploadTime?.format(DATE_FORMATTER) ?: "",
                updateTime = entity.updateTime?.format(DATE_FORMATTER) ?: "",
                downloadCount = entity.downloadCount,
                downloadUrl = "/api/files/${entity.id}/download",
                previewUrl = if (isPreviewable(entity.fileExtension)) "/api/files/${entity.id}/preview" else null,
                deletedAt = entity.deletedAt?.format(DATE_FORMATTER),
                daysUntilPermanentDeletion = daysUntilDeletion
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
        
        /**
         * 判断文件是否支持预览
         */
        private fun isPreviewable(extension: String?): Boolean {
            val previewableExts = setOf("jpg", "jpeg", "png", "gif", "pdf", "txt", "md")
            return extension?.lowercase() in previewableExts
        }
    }
}

/** 文件列表响应DTO */
data class FileListResponse(
    val total: Long,
    val page: Int,
    val size: Int,
    val files: List<FileResponse>,
    val currentFolder: FolderBreadcrumb? = null
)

data class FolderBreadcrumb(
    val id: Long?,
    val name: String,
    val path: List<com.zzy.file.dto.FolderPathItem>
)

/** 文件上传请求DTO */
data class FileUploadRequest(
    val folderId: Long? = null,
    val tags: String? = null,
    val description: String? = null
)

/** 更新文件信息请求DTO */
data class UpdateFileRequest(
    val fileName: String? = null,
    val folderId: Long? = null,
    val tags: String? = null,
    val description: String? = null
)

/** 移动文件请求DTO */
data class MoveFileRequest(
    val targetFolderId: Long?  // null表示移动到根目录
)

/** 批量移动文件请求DTO */
data class BatchMoveFilesRequest(
    val fileIds: List<Long>,
    val targetFolderId: Long?
)

/** 批量删除文件请求DTO */
data class BatchDeleteFilesRequest(
    val fileIds: List<Long>
)

/** 文件搜索请求DTO */
data class FileSearchRequest(
    val keyword: String? = null,
    val folderId: Long? = null,
    val fileTypes: List<String>? = null,  // 如：["pdf", "jpg"]
    val tags: List<String>? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val minSize: Long? = null,
    val maxSize: Long? = null,
    val sortBy: String = "uploadTime",
    val order: String = "desc",
    val page: Int = 1,
    val size: Int = 20
)

/** 文件统计响应DTO */
data class FileStatisticsResponse(
    val totalFiles: Int,
    val totalSize: Long,
    val totalSizeFormatted: String,
    val fileTypeDistribution: Map<String, Int>,
    val recentUploads: List<FileResponse>
)

