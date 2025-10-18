package com.zzy.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.CorsFilter

/**
 * 跨域配置
 * @author ZZY
 * @date 2025-10-09
 */
@Configuration
class CorsConfig {
    
    @Bean
    fun corsFilter(): CorsFilter {
        val config = CorsConfiguration()
        
        // 允许所有来源（开发环境）
        config.allowedOriginPatterns = listOf("*")
        
        // 允许所有请求头
        config.allowedHeaders = listOf("*")
        
        // 允许所有请求方法
        config.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
        
        // 允许携带凭证（cookies）
        config.allowCredentials = true
        
        // 暴露的响应头
        config.exposedHeaders = listOf(
            "Authorization",
            "Content-Type",
            "X-Total-Count"
        )
        
        // 预检请求的有效期（秒）
        config.maxAge = 3600L
        
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        
        return CorsFilter(source)
    }
}

