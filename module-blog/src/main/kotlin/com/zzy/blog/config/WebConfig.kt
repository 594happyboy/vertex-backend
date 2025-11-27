package com.zzy.blog.config

import com.zzy.blog.interceptor.AuthInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Web 配置
 * @author ZZY
 * @date 2025-10-18
 */
@Configuration
class WebConfig(
    private val authInterceptor: AuthInterceptor
) : WebMvcConfigurer {
    
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(authInterceptor)
            .addPathPatterns(
                "/api/auth/**",           // 认证接口
                "/api/documents/**",      // 文档接口
                "/api/user/**",           // 用户接口
                "/api/tree/**",           // 目录树接口（含重排）
                "/api/groups/**"          // 分组接口
            )
            .excludePathPatterns(
                "/api/auth/login",        // 登录接口
                "/doc.html",              // Swagger文档
                "/swagger-ui/**",         // Swagger UI
                "/v3/api-docs/**",        // OpenAPI文档
                "/swagger-resources/**",
                "/webjars/**"
            )
    }
}
