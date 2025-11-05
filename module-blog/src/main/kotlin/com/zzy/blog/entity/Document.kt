package com.zzy.blog.entity

import com.baomidou.mybatisplus.annotation.*
import com.zzy.common.pagination.HasId
import java.time.LocalDateTime

/**
 * 文档实体
 * @author ZZY
 * @date 2025-10-18
 */
@TableName("documents")
data class Document(
    /** 文档ID */
    @TableId(type = IdType.AUTO)
    override var id: Long? = null,
    
    /** 用户ID */
    var userId: Long,
    
    /** 分组ID */
    var groupId: Long? = null,
    
    /** 标题 */
    var title: String,
    
    /** 类型: md/pdf/txt */
    var type: String,
    
    /** 文件ID（关联file_metadata表） */
    var fileId: Long? = null,
    
    /** 文件访问路径 */
    var filePath: String? = null,
    
    /** 排序索引 */
    var sortIndex: Int = 0,
    
    /** 是否删除 */
    var deleted: Boolean = false,
    
    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    var createdAt: LocalDateTime? = null,
    
    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    var updatedAt: LocalDateTime? = null
) : HasId

/**
 * 文档类型枚举
 */
enum class DocType(val value: String) {
    MD("md"),
    PDF("pdf"),
    TXT("txt")
}

