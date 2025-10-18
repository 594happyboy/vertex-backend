package com.zzy.blog.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

/**
 * 用户实体类
 */
@TableName("users")
data class User(
    /**
     * 用户ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    var id: Long? = null,
    
    /**
     * 用户名
     */
    var username: String,
    
    /**
     * 密码哈希
     */
    var passwordHash: String,
    
    /**
     * 邮箱
     */
    var email: String? = null,
    
    /**
     * 头像URL
     */
    var avatarUrl: String? = null,
    
    /**
     * 存储配额（字节）
     */
    var storageQuota: Long? = 1073741824, // 默认 1GB
    
    /**
     * 已用存储（字节）
     */
    var usedStorage: Long? = 0,
    
    /**
     * 创建时间
     */
    var createdAt: LocalDateTime? = null,
    
    /**
     * 更新时间
     */
    var updatedAt: LocalDateTime? = null,
    
    /**
     * 最后登录时间
     */
    var lastLoginAt: LocalDateTime? = null,
    
    /**
     * 状态（1:正常 0:禁用）
     */
    var status: Int? = 1
)

