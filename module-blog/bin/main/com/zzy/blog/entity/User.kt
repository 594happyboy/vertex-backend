package com.zzy.blog.entity

import com.baomidou.mybatisplus.annotation.*
import java.time.LocalDateTime

/**
 * 用户实体
 * @author ZZY
 * @date 2025-10-18
 */
@TableName("users")
data class User(
    /** 用户ID */
    @TableId(type = IdType.AUTO)
    var id: Long? = null,
    
    /** 用户名 */
    var username: String,
    
    /** 密码哈希 */
    var passwordHash: String,
    
    /** 昵称 */
    var nickname: String? = null,
    
    /** 头像 */
    var avatar: String? = null,
    
    /** 状态(1:正常 0:禁用) */
    var status: Int = 1,
    
    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    var createdAt: LocalDateTime? = null,
    
    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    var updatedAt: LocalDateTime? = null
)

