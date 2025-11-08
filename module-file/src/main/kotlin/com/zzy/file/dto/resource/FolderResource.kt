package com.zzy.file.dto.resource

import com.zzy.file.entity.FileFolder
import com.zzy.file.util.DateFormatter

/**
 * 文件夹资源DTO
 * @author ZZY
 * @date 2025-11-02
 */
data class FolderResource(
    override val id: String,
    override val type: ResourceType = ResourceType.folder,
    override val name: String,
    override val parentId: String?,
    override val owner: Owner,
    override val createdAt: String,
    override val updatedAt: String,
    override val metadata: Map<String, Any>? = null,
    
    /** 子文件夹数量 */
    val childFolderCount: Int,
    
    /** 子文件数量 */
    val childFileCount: Int,
    
    /** 自定义颜色 */
    val color: String?,
    
    /** 自定义图标 */
    val icon: String? = null,
    
    /** 描述 */
    val description: String? = null
) : BaseResource {
    
    companion object {
        /**
         * 从实体转换为资源DTO
         */
        fun fromEntity(entity: FileFolder): FolderResource {
            val publicId = entity.publicId ?: throw IllegalStateException("文件夹缺少公开ID")
            
            return FolderResource(
                id = publicId,  // 使用公开ID
                name = entity.name,
                parentId = null,  // TODO: 需要查询父文件夹的publicId，应在Service层处理
                owner = Owner(
                    id = entity.userId.toString(),
                    name = "User${entity.userId}" // TODO: 从用户服务获取用户名
                ),
                childFolderCount = entity.subFolderCount ?: 0,
                childFileCount = entity.fileCount ?: 0,
                color = entity.color,
                description = entity.description,
                createdAt = DateFormatter.formatIso(entity.createdAt),
                updatedAt = DateFormatter.formatIso(entity.updatedAt),
                metadata = buildMap {
                    entity.color?.let { put("color", it) }
                    entity.description?.let { put("description", it) }
                }
            )
        }
    }
}

