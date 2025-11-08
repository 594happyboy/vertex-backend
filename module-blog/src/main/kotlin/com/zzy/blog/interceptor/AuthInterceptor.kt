package com.zzy.blog.interceptor

import com.zzy.blog.service.TokenRefreshService
import com.zzy.common.constants.AuthConstants
import com.zzy.common.exception.UnauthorizedException
import com.zzy.common.util.JwtUtil
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component

/**
 * Blog 模块增强认证拦截器
 * 
 * 继承自 common 的基础认证拦截器，扩展了以下功能：
 * - 使用 RefreshToken 自动刷新过期的 AccessToken
 * - 分布式锁机制防止并发刷新
 * - 在响应头返回新Token
 * 
 * ## Token验证流程
 * ```
 * 1. 提取AccessToken和RefreshToken
 * 
 * 2. 验证AccessToken：
 *    ├─ 有效（未过期） → 直接通过 ✅
 *    └─ 无效或过期 → 尝试用RefreshToken刷新
 *                     ├─ RefreshToken有效 → 使用分布式锁刷新 ✅
 *                     └─ RefreshToken无效 → 返回401 ❌
 * 
 * 3. 设置用户上下文
 * ```
 * 
 * ## 分布式锁机制
 * 当多个请求并发到达时：
 * - 第一个请求获取锁并执行刷新
 * - 其他请求等待并从缓存获取新Token
 * - 确保只刷新一次，避免资源浪费
 * 
 * @author ZZY
 * @date 2025-10-30
 */
@Component
class AuthInterceptor(
    jwtUtil: JwtUtil,
    private val tokenRefreshService: TokenRefreshService
) : com.zzy.common.interceptor.BaseAuthInterceptor(jwtUtil) {
    
    /**
     * 覆盖父类的 Token 验证方法，增加自动刷新功能
     */
    override fun handleTokenValidation(
        accessToken: String?,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): String? {
        // 1. 检查 AccessToken 是否有效
        val isAccessTokenValid = accessToken != null && jwtUtil.validateToken(accessToken)
        
        // 2. 如果有效，直接返回
        if (isAccessTokenValid) {
            return accessToken
        }
        
        // 3. 如果无效或不存在，尝试使用 RefreshToken 自动刷新
        logger.debug("AccessToken不存在或已过期，尝试使用RefreshToken自动刷新")
        
        // 从Cookie获取 RefreshToken
        val refreshToken = extractRefreshTokenFromCookie(request)
        
        if (refreshToken != null) {
            // 获取用户ID（用于分布式锁）
            val userId = getUserIdFromRefreshToken(refreshToken, request)
            
            if (userId != null) {
                // 使用分布式锁刷新Token
                val ipAddress = getClientIp(request)
                val userAgent = request.getHeader("User-Agent")
                
                val tokenPair = tokenRefreshService.refreshWithLock(
                    userId = userId,
                    oldRefreshToken = refreshToken,
                    ipAddress = ipAddress,
                    userAgent = userAgent
                )
                
                if (tokenPair != null) {
                    // 刷新成功
                    // 将新的 AccessToken 放入响应头
                    response.setHeader(AuthConstants.NEW_ACCESS_TOKEN_HEADER, tokenPair.accessToken)
                    
                    // 将新的 RefreshToken 放入Cookie（如果有轮转）
                    if (tokenPair.refreshToken.isNotEmpty()) {
                        setRefreshTokenCookie(response, tokenPair.refreshToken)
                    }
                    
                    logger.info("✅ AccessToken已自动刷新: userId={}", userId)
                    return tokenPair.accessToken
                } else {
                    logger.warn("❌ RefreshToken无效或已过期")
                    throw UnauthorizedException("登录已过期，请重新登录")
                }
            } else {
                logger.warn("❌ 无法从RefreshToken获取用户ID")
                throw UnauthorizedException("登录已过期，请重新登录")
            }
        } else {
            logger.warn("❌ 未找到RefreshToken")
            throw UnauthorizedException("登录已过期，请重新登录")
        }
    }
    
    /**
     * 从Cookie中提取 RefreshToken
     */
    private fun extractRefreshTokenFromCookie(request: HttpServletRequest): String? {
        val cookies = request.cookies ?: return null
        return cookies.firstOrNull { it.name == AuthConstants.REFRESH_TOKEN_COOKIE_NAME }?.value
    }
    
    /**
     * 从RefreshToken获取用户ID（用于分布式锁的key）
     * 
     * 注意：这里需要查询Redis获取token信息，不过考虑到性能，
     * 我们直接解析token字符串获取userId（需要RefreshTokenService提供方法）
     */
    private fun getUserIdFromRefreshToken(refreshToken: String, request: HttpServletRequest): Long? {
        // 从Redis查询RefreshToken的用户信息
        val ipAddress = getClientIp(request)
        return tokenRefreshService.getUserIdFromRefreshToken(refreshToken, ipAddress)
    }
    
    /**
     * 设置 RefreshToken 到 HttpOnly Cookie
     */
    private fun setRefreshTokenCookie(response: HttpServletResponse, refreshToken: String) {
        val cookie = Cookie(AuthConstants.REFRESH_TOKEN_COOKIE_NAME, refreshToken).apply {
            isHttpOnly = true      // 防止JS访问（防XSS）
            secure = false         // 生产环境应设为true（仅HTTPS）
            path = "/"
            maxAge = AuthConstants.COOKIE_MAX_AGE
        }
        response.addCookie(cookie)
    }
    
    /**
     * 获取客户端IP地址
     */
    private fun getClientIp(request: HttpServletRequest): String {
        var ip = request.getHeader("X-Forwarded-For")
        if (ip.isNullOrEmpty() || "unknown".equals(ip, ignoreCase = true)) {
            ip = request.getHeader("X-Real-IP")
        }
        if (ip.isNullOrEmpty() || "unknown".equals(ip, ignoreCase = true)) {
            ip = request.remoteAddr
        }
        if (!ip.isNullOrEmpty() && ip.contains(",")) {
            ip = ip.split(",")[0].trim()
        }
        return ip ?: AuthConstants.UNKNOWN
    }
}
