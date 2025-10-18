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
            .addPathPatterns("/api/**")
            .excludePathPatterns(
                "/api/auth/login",      // 登录接口
                "/api/auth/visitor",    // 游客令牌接口
                "/doc.html",            // Swagger文档
                "/swagger-ui/**",       // Swagger UI
                "/v3/api-docs/**",      // OpenAPI文档
                "/swagger-resources/**",
                "/webjars/**"
            )
    }
}

