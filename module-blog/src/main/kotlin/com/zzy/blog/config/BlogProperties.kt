package com.zzy.blog.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * 博客模块配置属性
 */
@Component
@ConfigurationProperties(prefix = "blog")
data class BlogProperties(
    /**
     * JWT 配置
     */
    var jwt: JwtProperties = JwtProperties()
) {
    /**
     * JWT 配置属性
     */
    data class JwtProperties(
        /**
         * JWT 密钥
         */
        var secret: String = "your-secret-key-change-it-in-production-must-be-at-least-256-bits",
        
        /**
         * JWT 过期时间（毫秒）
         */
        var expiration: Long = 86400000 // 默认 24 小时
    )
}

