package com.zzy.file.dto.resource

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * 基础资源接口（文件夹与文件的统一抽象）
 * @author ZZY
 * @date 2025-11-02
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = FolderResource::class, name = "folder"),
    JsonSubTypes.Type(value = FileResource::class, name = "file")
)
interface BaseResource {
    val id: String
    val type: ResourceType
    val name: String
    val parentId: String?
    val owner: Owner
    val createdAt: String
    val updatedAt: String
    val metadata: Map<String, Any>?
}

/**
 * 资源类型
 */
enum class ResourceType {
    folder, file
}

/**
 * 所有者信息
 */
data class Owner(
    val id: String,
    val name: String,
    val avatar: String? = null
)

