package com.zzy.blog.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.time.Duration

/**
 * Token 刷新配置（分布式锁方案）
 * 
 * ## 核心功能
 * 使用 Redis 分布式锁确保并发请求只刷新一次 Token
 * 
 * @author ZZY
 * @date 2025-10-30
 */
@Configuration
@ConfigurationProperties(prefix = "jwt.refresh")
data class TokenConfig(
    /** 分布式锁超时（防止死锁） */
    var lockTimeout: Duration = Duration.ofSeconds(5),
    
    /** Token 缓存时长（供并发请求共享） */
    var tokenCacheTtl: Duration = Duration.ofSeconds(5),

    /** RefreshToken 有效期 */
    var refreshTokenTtl: Duration = Duration.ofDays(7),

    /** RefreshToken 轮换宽限期（并发窗口内旧 token 短暂可用） */
    var gracePeriod: Duration = Duration.ofSeconds(20)
)
