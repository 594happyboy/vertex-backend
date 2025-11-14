package com.zzy.file.dto.resource

import com.zzy.file.entity.FileMetadata
import com.zzy.file.util.DateFormatter
import com.zzy.file.util.FileSizeFormatter
import com.zzy.file.constants.FileConstants

/**
 * 文件资源DTO (v2规范)
 * @author ZZY
 * @date 2025-11-02
 */
data class FileResource(
    override val id: String,
    override val type: ResourceType = ResourceType.file,
    override val name: String,
    override val parentId: String?,
    override val owner: Owner,
    override val createdAt: String,
    override val updatedAt: String,
    override val metadata: Map<String, Any>? = null,
    
    /** 文件大小（字节） */
    val size: Long,
    
    /** 格式化文件大小 */
    val sizeFormatted: String,
    
    /** MIME类型 */
    val mimeType: String,
    
    /** 文件扩展名 */
    val extension: String,
    
    /** 缩略图URL */
    val thumbnailUrl: String? = null,
    
    /** 下载URL */
    val downloadUrl: String,
    
    /** 预览URL（可选） */
    val previewUrl: String? = null,
    
    /** 所属文件夹ID */
    val folderId: String?,
    
    /** 标签（可选） */
    val tags: List<String>? = null,
    
    /** 描述（可选） */
    val description: String? = null
) : BaseResource {
    
    companion object {
        /**
         * 从实体转换为资源DTO (v2规范)
         */
        fun fromEntity(entity: FileMetadata): FileResource {
            val fileSize = entity.fileSize ?: 0
            val extension = entity.fileExtension ?: ""
            val publicId = entity.publicId ?: throw IllegalStateException("文件缺少公开ID")
            
            return FileResource(
                id = publicId,  // 使用公开ID
                name = entity.fileName ?: "unknown",
                parentId = null,  // TODO: 需要查询父文件夹的publicId，应在Service层处理
                owner = Owner(
                    id = entity.userId.toString(),
                    name = "User${entity.userId}" // TODO: 从用户服务获取用户名
                ),
                size = fileSize,
                sizeFormatted = FileSizeFormatter.format(fileSize),
                mimeType = entity.fileType ?: "application/octet-stream",
                extension = extension,
                thumbnailUrl = if (isPreviewable(extension)) "/api/files/thumbnail/$publicId" else null,
                downloadUrl = "/api/files/download/$publicId",
                previewUrl = if (isPreviewable(extension)) "/api/files/preview/$publicId" else null,
                folderId = null,  // 不暴露内部文件夹ID，前端应通过文件夹树接口获取层级关系
                tags = null, // TODO: 实现标签功能
                description = entity.description,
                createdAt = DateFormatter.formatIso(entity.uploadTime),
                updatedAt = DateFormatter.formatIso(entity.updateTime),
                metadata = buildMap {
                    put("downloadCount", entity.downloadCount)
                    entity.deletedAt?.let { 
                        put("deletedAt", DateFormatter.formatIso(it))
                    }
                }
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

