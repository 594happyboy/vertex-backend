package com.zzy.common.interceptor

import com.zzy.common.context.AuthContextHolder
import com.zzy.common.context.AuthUser
import com.zzy.common.exception.UnauthorizedException
import com.zzy.common.util.JwtUtil
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.web.servlet.HandlerInterceptor

/**
 * 基础认证拦截器（抽象基类）
 * 
 * ## 核心功能
 * - 从请求头提取并验证 AccessToken
 * - 解析 Token 获取用户ID
 * - 设置用户上下文（AuthContextHolder）
 * - 请求结束后清理上下文
 * 
 * ## 扩展机制
 * - 子类可以覆盖 `handleTokenValidation` 方法实现 Token 刷新等增强功能
 * - 子类可以覆盖 `extractAccessToken` 方法自定义 Token 提取逻辑
 * 
 * ## 使用方式
 * 1. 直接实例化使用（简单场景）
 * 2. 继承并扩展功能（需要增强功能时）
 * 
 * Controller 中通过 `AuthContextHolder.getCurrentUserId()` 获取当前用户ID
 * 
 * @author ZZY
 * @date 2025-11-08
 */
open class BaseAuthInterceptor(
    protected val jwtUtil: JwtUtil
) : HandlerInterceptor {
    
    protected val logger = LoggerFactory.getLogger(javaClass)
    
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        // 1. 提取 AccessToken
        var accessToken = extractAccessToken(request)
        
        // 2. 验证 Token（子类可以覆盖此方法实现 Token 刷新）
        accessToken = handleTokenValidation(accessToken, request, response)
        
        if (accessToken == null) {
            logger.warn("❌ 无法获取有效的 AccessToken: path={}", request.requestURI)
            throw UnauthorizedException("请先登录")
        }
        
        // 3. 解析 Token 获取用户ID
        val userId = jwtUtil.getUserIdFromToken(accessToken)
        if (userId == null) {
            logger.warn("❌ Token中缺少用户信息: path={}", request.requestURI)
            throw UnauthorizedException("令牌无效")
        }
        
        // 4. 设置用户上下文
        AuthContextHolder.setAuthUser(AuthUser(userId = userId))
        logger.debug("✅ 设置用户上下文: userId={}, path={}", userId, request.requestURI)
        
        return true
    }
    
    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?
    ) {
        // 清除上下文，防止内存泄漏
        AuthContextHolder.clear()
    }
    
    /**
     * 从请求头中提取 AccessToken
     * 子类可以覆盖此方法自定义提取逻辑
     * 
     * @return AccessToken 或 null
     */
    protected open fun extractAccessToken(request: HttpServletRequest): String? {
        val authHeader = request.getHeader("Authorization")
        return if (authHeader != null && authHeader.startsWith("Bearer ")) {
            authHeader.substring(7)
        } else {
            null
        }
    }
    
    /**
     * 处理 Token 验证
     * 默认实现：简单验证 Token 是否有效
     * 子类可以覆盖此方法实现更复杂的逻辑（如 Token 自动刷新）
     * 
     * @param accessToken 提取的 AccessToken
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @return 有效的 AccessToken 或 null
     */
    protected open fun handleTokenValidation(
        accessToken: String?,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): String? {
        // 默认实现：简单验证
        if (accessToken == null) {
            return null
        }
        
        if (!jwtUtil.validateToken(accessToken)) {
            logger.warn("❌ AccessToken无效或已过期: path={}", request.requestURI)
            throw UnauthorizedException("登录已过期，请重新登录")
        }
        
        return accessToken
    }
}

