package com.zzy.blog.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

/**
 * RefreshToken 持久化实体
 *
 * 使用随机字符串作为主键，保存每个 token 的生命周期和状态。
 */
@TableName("refresh_tokens")
data class RefreshTokenEntity(
    @TableId(value = "token", type = IdType.INPUT)
    var token: String? = null,

    var userId: Long? = null,
    var ipAddress: String? = null,
    var userAgent: String? = null,
    var createdAt: LocalDateTime? = null,
    var expiresAt: LocalDateTime? = null,
    var rotated: Boolean = false,
    var rotatedAt: LocalDateTime? = null,
    var replacedBy: String? = null,
    var graceExpiresAt: LocalDateTime? = null,
    var revoked: Boolean = false,
    var revokedAt: LocalDateTime? = null,
    var updatedAt: LocalDateTime? = null
)
