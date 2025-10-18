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
 * 登录响应
 */
data class LoginResponse(
    val accessToken: String,
    val user: UserInfo
)

/**
 * 游客令牌请求
 */
data class VisitorTokenRequest(
    val targetUser: String? = null
)

/**
 * 游客令牌响应
 */
data class VisitorTokenResponse(
    val visitorToken: String,
    val targetUser: UserInfo
)

/**
 * 用户信息
 */
data class UserInfo(
    val id: Long,
    val username: String,
    val nickname: String?
)

/**
 * 刷新令牌请求（可选）
 */
data class RefreshTokenRequest(
    val refreshToken: String
)

/**
 * 刷新令牌响应（可选）
 */
data class RefreshTokenResponse(
    val accessToken: String
)

