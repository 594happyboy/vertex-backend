package com.zzy.blog.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

/**
 * 文章分组实体类
 */
@TableName("blog_groups")
data class Group(
    /**
     * 分组ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    var id: Long? = null,
    
    /**
     * 分组名称
     */
    var name: String,
    
    /**
     * URL slug
     */
    var slug: String,
    
    /**
     * 分组描述
     */
    var description: String? = null,
    
    /**
     * 排序序号
     */
    var orderIndex: Int = 0,
    
    /**
     * 创建时间
     */
    var createdAt: LocalDateTime? = null,
    
    /**
     * 更新时间
     */
    var updatedAt: LocalDateTime? = null
)

