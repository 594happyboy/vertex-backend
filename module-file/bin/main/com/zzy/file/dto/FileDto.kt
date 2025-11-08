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

/** 
 * 文件响应DTO
 * 注意：只返回公开ID，不暴露内部数据库ID
 */
data class FileResponse(
    val id: String,  // 公开ID（对外暴露）
    val fileName: String,
    val fileSize: Long,
    val fileSizeFormatted: String,
    val fileType: String,
    val fileExtension: String,
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
                id = entity.publicId ?: "",  // 使用公开ID
                fileName = entity.fileName ?: "",
                fileSize = entity.fileSize ?: 0,
                fileSizeFormatted = FileSizeFormatter.format(entity.fileSize ?: 0),
                fileType = entity.fileType ?: "unknown",
                fileExtension = extension,
                description = entity.description,
                uploadTime = DateFormatter.format(entity.uploadTime),
                updateTime = DateFormatter.format(entity.updateTime),
                downloadCount = entity.downloadCount,
                downloadUrl = "/api/files/${entity.publicId}/download",  // 使用公开ID
                previewUrl = if (isPreviewable(extension)) "/api/files/${entity.publicId}/preview" else null,
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
    val id: String?,  // 文件夹公开ID
    val name: String,
    val path: List<com.zzy.file.dto.FolderPathItem>
)

/** 文件上传请求DTO */
data class FileUploadRequest(
    val folderId: String? = null,  // 文件夹公开ID
    val description: String? = null,
    val replaceFileId: String? = null  // 要替换的文件公开ID，如果提供则执行覆盖上传
)

/** 更新文件信息请求DTO */
data class UpdateFileRequest(
    val fileName: String? = null,
    val folderId: String? = null,  // 文件夹公开ID
    val description: String? = null
)

/** 移动文件请求DTO */
data class MoveFileRequest(
    val targetFolderId: String?  // 目标文件夹公开ID，null表示移动到根目录
)

/** 批量移动文件请求DTO */
data class BatchMoveFilesRequest(
    val fileIds: List<String>,  // 文件公开ID列表
    val targetFolderId: String?  // 目标文件夹公开ID
)

/** 批量删除文件请求DTO */
data class BatchDeleteFilesRequest(
    val fileIds: List<String>  // 文件公开ID列表
)

/** 文件搜索请求DTO */
data class FileSearchRequest(
    val keyword: String? = null,
    val folderId: String? = null,  // 文件夹公开ID
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

