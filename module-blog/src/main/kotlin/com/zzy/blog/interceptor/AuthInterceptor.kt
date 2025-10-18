package com.zzy.blog.interceptor

import com.zzy.blog.util.JwtUtil
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

/**
 * 认证拦截器
 */
@Component
class AuthInterceptor(
    private val jwtUtil: JwtUtil
) : HandlerInterceptor {
    
    companion object {
        /**
         * 用户ID 在请求属性中的 key
         */
        const val USER_ID_ATTRIBUTE = "userId"
        
        /**
         * 用户名 在请求属性中的 key
         */
        const val USERNAME_ATTRIBUTE = "username"
    }
    
    /**
     * 前置处理：验证 JWT Token
     */
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        // OPTIONS 请求直接放行
        if (request.method == "OPTIONS") {
            return true
        }
        
        // 获取 Authorization 头
        val authHeader = request.getHeader("Authorization")
        val token = jwtUtil.extractTokenFromHeader(authHeader)
        
        // Token 不存在或无效
        if (token == null || !jwtUtil.validateToken(token)) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = "application/json;charset=UTF-8"
            response.writer.write("""{"code":401,"message":"未授权，请先登录"}""")
            return false
        }
        
        // 从 Token 中提取用户信息并存入请求属性
        val userId = jwtUtil.getUserIdFromToken(token)
        val username = jwtUtil.getUsernameFromToken(token)
        
        if (userId == null || username == null) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = "application/json;charset=UTF-8"
            response.writer.write("""{"code":401,"message":"Token 无效"}""")
            return false
        }
        
        // 将用户信息存入请求属性
        request.setAttribute(USER_ID_ATTRIBUTE, userId)
        request.setAttribute(USERNAME_ATTRIBUTE, username)
        
        return true
    }
}

/**
 * 从请求中获取当前用户ID
 */
fun HttpServletRequest.getCurrentUserId(): Long? {
    return this.getAttribute(AuthInterceptor.USER_ID_ATTRIBUTE) as? Long
}

/**
 * 从请求中获取当前用户名
 */
fun HttpServletRequest.getCurrentUsername(): String? {
    return this.getAttribute(AuthInterceptor.USERNAME_ATTRIBUTE) as? String
}

