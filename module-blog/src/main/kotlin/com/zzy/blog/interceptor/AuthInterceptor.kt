package com.zzy.blog.interceptor

import com.zzy.blog.context.AuthContextHolder
import com.zzy.blog.context.AuthUser
import com.zzy.blog.exception.UnauthorizedException
import com.zzy.blog.service.TokenRefreshService
import com.zzy.blog.util.JwtUtil
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

/**
 * 认证拦截器（方案4：分布式锁）
 * 
 * ## 核心功能
 * - 验证 AccessToken
 * - 使用 RefreshToken 自动刷新（带分布式锁）
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
    private val jwtUtil: JwtUtil,
    private val tokenRefreshService: TokenRefreshService
) : HandlerInterceptor {
    
    private val logger = LoggerFactory.getLogger(AuthInterceptor::class.java)
    
    companion object {
        const val NEW_ACCESS_TOKEN_HEADER = "X-New-Access-Token"
        const val REFRESH_TOKEN_COOKIE_NAME = "refreshToken"
    }
    
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        // 1. 从请求头获取 AccessToken
        var accessToken = extractAccessToken(request)
        
        // 2. 验证 AccessToken（如果存在）
        val isAccessTokenValid = accessToken != null && jwtUtil.validateToken(accessToken)
        
        // 3. 如果 AccessToken 不存在或已过期，尝试使用 RefreshToken 自动刷新
        if (!isAccessTokenValid) {
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
                        accessToken = tokenPair.accessToken
                        
                        // 将新的 AccessToken 放入响应头
                        response.setHeader(NEW_ACCESS_TOKEN_HEADER, tokenPair.accessToken)
                        
                        // 将新的 RefreshToken 放入Cookie（如果有轮转）
                        if (tokenPair.refreshToken.isNotEmpty()) {
                            setRefreshTokenCookie(response, tokenPair.refreshToken)
                        }
                        
                        logger.info("✅ AccessToken已自动刷新: userId={}", userId)
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
        
        // 4. 最后检查：如果 accessToken 仍然为null，说明没有有效的认证信息
        if (accessToken == null) {
            logger.warn("无有效的认证信息: path={}", request.requestURI)
            throw UnauthorizedException("请先登录")
        }
        
        // 5. 解析令牌并设置上下文
        val userId = jwtUtil.getUserIdFromToken(accessToken)
        if (userId != null) {
            AuthContextHolder.setAuthUser(
                AuthUser(userId = userId)
            )
            logger.debug("设置用户上下文: userId={}", userId)
        } else {
            throw UnauthorizedException("令牌中缺少用户信息")
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
     * 从请求头中提取 AccessToken
     */
    private fun extractAccessToken(request: HttpServletRequest): String? {
        val authHeader = request.getHeader("Authorization")
        return if (authHeader != null && authHeader.startsWith("Bearer ")) {
            authHeader.substring(7)
        } else {
            null
        }
    }
    
    /**
     * 从Cookie中提取 RefreshToken
     */
    private fun extractRefreshTokenFromCookie(request: HttpServletRequest): String? {
        val cookies = request.cookies ?: return null
        return cookies.firstOrNull { it.name == REFRESH_TOKEN_COOKIE_NAME }?.value
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
        val cookie = Cookie(REFRESH_TOKEN_COOKIE_NAME, refreshToken).apply {
            isHttpOnly = true      // 防止JS访问（防XSS）
            secure = false         // 生产环境应设为true（仅HTTPS）
            path = "/"
            maxAge = 7 * 24 * 60 * 60  // 7天
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
        return ip ?: "unknown"
    }
}
