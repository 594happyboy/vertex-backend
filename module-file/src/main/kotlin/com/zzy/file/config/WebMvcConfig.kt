package com.zzy.file.config

import com.zzy.common.interceptor.BaseAuthInterceptor
import com.zzy.common.util.JwtUtil
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * File 模块 Web MVC 配置
 * 
 * 使用 common 的基础认证拦截器（简单 Token 验证，无自动刷新）
 * 
 * @author ZZY
 * @date 2025-11-08
 */
@Configuration
class WebMvcConfig(
    private val jwtUtil: JwtUtil
) : WebMvcConfigurer {
    
    /**
     * 创建认证拦截器 Bean
     * 使用不同的 Bean 名称避免与其他模块冲突
     */
    @Bean
    fun fileAuthInterceptor(): BaseAuthInterceptor {
        return BaseAuthInterceptor(jwtUtil)
    }
    
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(fileAuthInterceptor())
            .addPathPatterns("/api/files/**", "/api/folders/**")
            .excludePathPatterns(
                "/api/files/download/**",      // 文件下载（公开访问）
                "/swagger-ui/**",              // Swagger UI
                "/v3/api-docs/**",             // OpenAPI文档
                "/swagger-resources/**",
                "/webjars/**",
                "/actuator/**"                 // 健康检查
            )
    }
}

