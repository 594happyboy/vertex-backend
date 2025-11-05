package com.zzy.blog.entity

import com.baomidou.mybatisplus.annotation.*
import com.zzy.common.pagination.HasId
import java.time.LocalDateTime

/**
 * 分组实体
 * @author ZZY
 * @date 2025-10-18
 */
@TableName("blog_groups")
data class Group(
    /** 分组ID */
    @TableId(type = IdType.AUTO)
    override var id: Long? = null,
    
    /** 用户ID */
    var userId: Long,
    
    /** 分组名称 */
    var name: String,
    
    /** 父分组ID */
    var parentId: Long? = null,
    
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
) : HasId {

    /** 子分组（不映射到数据库） */
    @TableField(exist = false)
    var children: MutableList<Group>? = null
}

