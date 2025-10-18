package com.zzy.file.dto

import com.zzy.file.entity.FileMetadata
import java.time.format.DateTimeFormatter

/**
 * 文件响应DTO
 * @author ZZY
 * @date 2025-10-09
 */
data class FileResponse(
    /** 文件ID */
    val id: Long,
    
    /** 文件名 */
    val fileName: String,
    
    /** 文件大小(字节) */
    val fileSize: Long,
    
    /** 文件大小(格式化) */
    val fileSizeFormatted: String,
    
    /** 文件类型 */
    val fileType: String,
    
    /** 文件扩展名 */
    val fileExtension: String,
    
    /** 上传时间 */
    val uploadTime: String,
    
    /** 下载次数 */
    val downloadCount: Int,
    
    /** 下载URL */
    val downloadUrl: String
) {
    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        
        /**
         * 从FileMetadata转换为FileResponse
         */
        fun fromEntity(entity: FileMetadata): FileResponse {
            return FileResponse(
                id = entity.id ?: 0,
                fileName = entity.fileName ?: "",
                fileSize = entity.fileSize ?: 0,
                fileSizeFormatted = formatFileSize(entity.fileSize ?: 0),
                fileType = entity.fileType ?: "unknown",
                fileExtension = entity.fileExtension ?: "",
                uploadTime = entity.uploadTime?.format(DATE_FORMATTER) ?: "",
                downloadCount = entity.downloadCount,
                downloadUrl = "/api/files/${entity.id}/download"
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

