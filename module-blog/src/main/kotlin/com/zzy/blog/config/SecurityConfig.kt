package com.zzy.blog.config

import com.zzy.blog.interceptor.AuthInterceptor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * 安全配置类
 */
@Configuration
class SecurityConfig(
    private val authInterceptor: AuthInterceptor
) : WebMvcConfigurer {
    
    /**
     * 注册拦截器
     */
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(authInterceptor)
            .addPathPatterns("/api/admin/**") // 拦截所有后台接口
            .excludePathPatterns(
                "/api/auth/login",  // 排除登录接口
                "/api/auth/logout"  // 排除登出接口
            )
    }
    
    /**
     * 密码编码器（BCrypt）
     */
    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }
}

