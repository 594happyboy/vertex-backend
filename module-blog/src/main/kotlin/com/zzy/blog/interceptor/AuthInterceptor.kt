package com.zzy.blog.interceptor

import com.zzy.blog.context.AuthContextHolder
import com.zzy.blog.context.AuthUser
import com.zzy.blog.exception.UnauthorizedException
import com.zzy.blog.util.JwtUtil
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

/**
 * 认证拦截器
 * @author ZZY
 * @date 2025-10-18
 */
@Component
class AuthInterceptor(
    private val jwtUtil: JwtUtil
) : HandlerInterceptor {
    
    private val logger = LoggerFactory.getLogger(AuthInterceptor::class.java)
    
    companion object {
        const val NEW_TOKEN_HEADER = "X-New-Token"  // 新token的响应头名称
    }
    
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        // 从请求头获取令牌
        val token = extractToken(request)
        
        // 如果没有token，拒绝访问
        if (token == null) {
            logger.warn("请求被拒绝: 缺少认证令牌, path={}", request.requestURI)
            throw UnauthorizedException("请先登录，访问该接口需要认证")
        }
        
        // 验证令牌
        if (!jwtUtil.validateToken(token)) {
            logger.warn("请求被拒绝: 令牌无效或已过期, path={}", request.requestURI)
            throw UnauthorizedException("令牌无效或已过期")
        }
        
        // 解析令牌
        val role = jwtUtil.getRoleFromToken(token)
        
        if (role == "USER") {
            val userId = jwtUtil.getUserIdFromToken(token)
            if (userId != null) {
                AuthContextHolder.setAuthUser(
                    AuthUser(
                        userId = userId
                    )
                )
                logger.debug("设置用户上下文: userId={}", userId)
            } else {
                throw UnauthorizedException("令牌中缺少用户信息")
            }
        } else {
            throw UnauthorizedException("未知的角色类型: $role")
        }
        
        // 检查是否需要刷新token
        if (jwtUtil.shouldRefreshToken(token)) {
            val newToken = jwtUtil.refreshToken(token)
            if (newToken != null) {
                // 将新token放入响应头
                response.setHeader(NEW_TOKEN_HEADER, newToken)
                logger.debug("Token即将过期，已自动刷新并返回新token")
            }
        }
        
        return true
    }
    
    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?
    ) {
        // 清除上下文
        AuthContextHolder.clear()
    }
    
    /**
     * 从请求中提取令牌
     */
    private fun extractToken(request: HttpServletRequest): String? {
        val authHeader = request.getHeader("Authorization")
        return if (authHeader != null && authHeader.startsWith("Bearer ")) {
            authHeader.substring(7)
        } else {
            null
        }
    }
}

