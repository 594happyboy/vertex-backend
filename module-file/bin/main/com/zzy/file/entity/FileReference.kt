package com.zzy.file.entity

import com.baomidou.mybatisplus.annotation.*
import java.time.LocalDateTime

/**
 * 文件引用关系实体
 * 记录文件被哪些对象引用
 * @author ZZY
 * @date 2025-11-03
 */
@TableName("file_references")
data class FileReference(
    /** 引用ID */
    @TableId(type = IdType.AUTO)
    var id: Long? = null,
    
    /** 文件ID */
    var fileId: Long,
    
    /** 引用类型 */
    var referenceType: String,
    
    /** 引用对象ID */
    var referenceId: Long,
    
    /** 引用字段 */
    var referenceField: String? = null,
    
    /** 创建时间 */
    var createdAt: LocalDateTime? = null
)

/**
 * 引用类型枚举
 */
enum class ReferenceType(val value: String) {
    /** 文档直接关联（通过document.file_id字段） */
    DOCUMENT("document"),
    
    /** 文档内容中的引用（MD/HTML内容中的图片、附件链接） */
    DOCUMENT_CONTENT("document_content"),
    
    /** 评论中的引用 */
    COMMENT("comment"),
    
    /** 用户资料引用 */
    PROFILE("profile"),
    
    /** 通用附件引用 */
    ATTACHMENT("attachment")
}

