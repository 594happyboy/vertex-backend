package com.zzy.blog.dto

/**
 * 认证相关 DTO
 * @author ZZY
 * @date 2025-10-18
 */

/**
 * 登录请求
 */
data class LoginRequest(
    val username: String,
    val password: String
)

/**
 * 登录响应（双Token模式）
 */
data class LoginResponse(
    val accessToken: String,      // 短期访问令牌（30分钟）
    val refreshToken: String,      // 长期刷新令牌（7天），存储在HttpOnly Cookie
    val user: UserInfo
)

/**
 * 用户信息
 */
data class UserInfo(
    val id: Long,
    val username: String,
    val nickname: String?
)

