package com.zzy.file.dto

import com.zzy.file.entity.FileMetadata
import com.zzy.file.util.DateFormatter
import com.zzy.file.util.FileSizeFormatter
import com.zzy.file.constants.FileConstants

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
        /**
         * 从实体转换为DTO
         */
        fun fromEntity(
            entity: FileMetadata, 
            retentionDays: Int = FileConstants.FileManagement.RETENTION_DAYS
        ): FileResponse {
            val daysUntilDeletion = entity.deletedAt?.let { deletedAt ->
                val daysPassed = java.time.Duration.between(deletedAt, java.time.LocalDateTime.now()).toDays()
                (retentionDays - daysPassed).coerceAtLeast(0)
            }
            
            val extension = entity.fileExtension ?: ""
            return FileResponse(
                id = entity.id ?: 0,
                fileName = entity.fileName ?: "",
                fileSize = entity.fileSize ?: 0,
                fileSizeFormatted = FileSizeFormatter.format(entity.fileSize ?: 0),
                fileType = entity.fileType ?: "unknown",
                fileExtension = extension,
                folderId = entity.folderId,
                description = entity.description,
                uploadTime = DateFormatter.format(entity.uploadTime),
                updateTime = DateFormatter.format(entity.updateTime),
                downloadCount = entity.downloadCount,
                downloadUrl = "/api/files/${entity.id}/download",
                previewUrl = if (isPreviewable(extension)) "/api/files/${entity.id}/preview" else null,
                deletedAt = DateFormatter.format(entity.deletedAt),
                daysUntilPermanentDeletion = daysUntilDeletion
            )
        }
        
        /**
         * 判断文件是否支持预览
         */
        private fun isPreviewable(extension: String): Boolean {
            return extension.lowercase() in FileConstants.PREVIEWABLE_EXTENSIONS
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
    val description: String? = null
)

/** 更新文件信息请求DTO */
data class UpdateFileRequest(
    val fileName: String? = null,
    val folderId: Long? = null,
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

