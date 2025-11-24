package com.zzy.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.CorsFilter

/**
 * CORS跨域资源共享配置
 * 
 * ## 什么是CORS？
 * CORS (Cross-Origin Resource Sharing) 是一种机制，允许Web应用从不同的域访问资源。
 * 浏览器的同源策略限制了跨域请求，CORS提供了一种安全的方式来突破这个限制。
 * 
 * ## 使用场景
 * - 前后端分离开发（前端localhost:3000，后端localhost:8080）
 * - 多个子域名访问同一个API
 * - 移动端H5页面调用API
 * 
 * ## 当前配置说明
 * - **开发环境**：允许所有来源访问（便于开发调试）
 * - **生产环境**：应该配置具体的前端域名白名单
 * 
 * ## 生产环境配置建议
 * ```kotlin
 * // 替换 allowedOriginPatterns = listOf("*")
 * config.allowedOrigins = listOf(
 *     "https://www.yourdomain.com",
 *     "https://admin.yourdomain.com"
 * )
 * ```
 * 
 * @author ZZY
 * @date 2025-10-09
 */
@Configuration
class CorsConfig {
    
    /**
     * 配置CORS过滤器
     */
    @Bean
    fun corsFilter(): CorsFilter {
        val config = CorsConfiguration()
        
        // 1. 允许的来源
        // ⚠️ 开发环境：允许所有来源
        // ⚠️ 生产环境：应该配置具体的域名白名单
        config.allowedOriginPatterns = listOf("*")
        
        // 2. 允许的请求头
        // 允许前端发送任意请求头（包括自定义头）
        config.allowedHeaders = listOf("*")
        
        // 3. 允许的HTTP方法
        config.allowedMethods = listOf(
            "GET",      // 查询
            "POST",     // 创建
            "PUT",      // 更新（全量）
            "PATCH",    // 更新（部分）
            "DELETE",   // 删除
            "OPTIONS"   // 预检请求
        )
        
        // 4. 允许携带认证信息（Cookie、Authorization等）
        config.allowCredentials = true
        
        // 5. 暴露给前端的响应头
        // 前端JavaScript只能访问这里列出的响应头
        config.exposedHeaders = listOf(
            "Authorization",    // JWT令牌
            "Content-Type",     // 内容类型
            "X-Total-Count",    // 总数（用于分页）
            "Content-Disposition",  // 文件下载时的文件名
            "X-New-Access-Token"    // Token刷新时的新AccessToken
        )
        
        // 6. 预检请求的缓存时间（秒）
        // 浏览器会缓存OPTIONS预检请求的结果，避免频繁发送
        config.maxAge = 3600L  // 1小时
        
        // 注册CORS配置到所有路径
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        
        return CorsFilter(source)
    }
}

