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
    
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        // 从请求头获取令牌
        val token = extractToken(request)
        
        if (token != null) {
            // 验证令牌
            if (!jwtUtil.validateToken(token)) {
                throw UnauthorizedException("令牌无效或已过期")
            }
            
            // 解析令牌
            val role = jwtUtil.getRoleFromToken(token)
            
            when (role) {
                "USER" -> {
                    val userId = jwtUtil.getUserIdFromToken(token)
                    if (userId != null) {
                        AuthContextHolder.setAuthUser(
                            AuthUser(
                                role = "USER",
                                currentUserId = userId,
                                targetUserId = null
                            )
                        )
                        logger.debug("设置用户上下文: userId={}", userId)
                    }
                }
                "VISITOR" -> {
                    val targetUserId = jwtUtil.getTargetUserIdFromToken(token)
                    if (targetUserId != null) {
                        AuthContextHolder.setAuthUser(
                            AuthUser(
                                role = "VISITOR",
                                currentUserId = null,
                                targetUserId = targetUserId
                            )
                        )
                        logger.debug("设置游客上下文: targetUserId={}", targetUserId)
                    }
                }
                else -> {
                    throw UnauthorizedException("未知的角色类型")
                }
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

