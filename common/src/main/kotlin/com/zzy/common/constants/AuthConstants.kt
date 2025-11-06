package com.zzy.common.constants

/**
 * 认证相关常量
 * @author ZZY
 * @date 2025-11-06
 */
object AuthConstants {
    /**
     * RefreshToken Cookie 名称
     */
    const val REFRESH_TOKEN_COOKIE_NAME = "refreshToken"
    
    /**
     * 新 AccessToken 响应头名称
     */
    const val NEW_ACCESS_TOKEN_HEADER = "X-New-Access-Token"
    
    /**
     * Cookie 最大有效期（7天，单位：秒）
     */
    const val COOKIE_MAX_AGE = 7 * 24 * 60 * 60
    
    /**
     * 未知值标识
     */
    const val UNKNOWN = "unknown"
}

