package com.zzy.blog.service

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.zzy.blog.dto.*
import com.zzy.blog.entity.User
import com.zzy.blog.exception.PasswordIncorrectException
import com.zzy.blog.exception.UserNotFoundException
import com.zzy.blog.mapper.UserMapper
import com.zzy.blog.util.JwtUtil
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.bcrypt.BCrypt
import org.springframework.stereotype.Service

/**
 * 认证服务（分布式锁方案）
 * 
 * ## 核心功能
 * - 用户登录（生成双Token）
 * - 用户登出（撤销所有RefreshToken）
 * 
 * @author ZZY
 * @date 2025-10-30
 */
@Service
class AuthService(
    private val userMapper: UserMapper,
    private val jwtUtil: JwtUtil,
    private val refreshTokenService: RefreshTokenService
) {
    
    private val logger = LoggerFactory.getLogger(AuthService::class.java)
    
    /**
     * 用户登录（双Token模式）
     * 
     * @param request 登录请求
     * @param httpRequest HTTP请求（用于获取IP和User-Agent）
     * @return 登录响应（包含accessToken和refreshToken）
     */
    fun login(request: LoginRequest, httpRequest: HttpServletRequest): LoginResponse {
        logger.info("用户登录: {}", request.username)
        
        // 1. 查询用户
        val user = userMapper.selectOne(
            QueryWrapper<User>().eq("username", request.username)
        ) ?: throw UserNotFoundException("用户不存在")
        
        // 2. 检查用户状态
        if (user.status == 0) {
            throw PasswordIncorrectException("用户已被禁用")
        }
        
        // 3. 验证密码
        if (!BCrypt.checkpw(request.password, user.passwordHash)) {
            throw PasswordIncorrectException("密码错误")
        }
        
        // 4. 生成 AccessToken（短期，30分钟）
        val accessToken = jwtUtil.generateAccessToken(user.id!!, user.username)
        
        // 5. 生成 RefreshToken（长期，7天），存储到Redis
        val ipAddress = getClientIp(httpRequest)
        val userAgent = httpRequest.getHeader("User-Agent")
        val refreshToken = refreshTokenService.generateRefreshToken(user.id!!, ipAddress, userAgent)
        
        logger.info("✅ 用户登录成功: username={}, userId={}, IP={}", user.username, user.id, ipAddress)
        
        return LoginResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            user = UserInfo(
                id = user.id!!,
                username = user.username,
                nickname = user.nickname
            )
        )
    }
    
    /**
     * 用户登出（撤销所有RefreshToken）
     * 
     * @param userId 用户ID
     */
    fun logout(userId: Long) {
        refreshTokenService.revokeAllUserTokens(userId)
        logger.info("✅ 用户登出: userId={}", userId)
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
