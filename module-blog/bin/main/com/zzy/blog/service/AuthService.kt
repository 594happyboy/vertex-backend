package com.zzy.blog.service

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.zzy.blog.dto.*
import com.zzy.blog.entity.User
import com.zzy.blog.exception.PasswordIncorrectException
import com.zzy.blog.exception.UserNotFoundException
import com.zzy.blog.mapper.UserMapper
import com.zzy.blog.util.JwtUtil
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.bcrypt.BCrypt
import org.springframework.stereotype.Service

/**
 * 认证服务
 * @author ZZY
 * @date 2025-10-18
 */
@Service
class AuthService(
    private val userMapper: UserMapper,
    private val jwtUtil: JwtUtil
) {
    
    private val logger = LoggerFactory.getLogger(AuthService::class.java)
    
    /**
     * 用户登录
     */
    fun login(request: LoginRequest): LoginResponse {
        logger.info("用户登录: {}", request.username)
        
        // 查询用户
        val user = userMapper.selectOne(
            QueryWrapper<User>().eq("username", request.username)
        ) ?: throw UserNotFoundException("用户不存在")
        
        // 检查用户状态
        if (user.status == 0) {
            throw PasswordIncorrectException("用户已被禁用")
        }
        
        // 验证密码
        if (!BCrypt.checkpw(request.password, user.passwordHash)) {
            throw PasswordIncorrectException("密码错误")
        }
        
        // 生成令牌
        val token = jwtUtil.generateUserToken(user.id!!, user.username)
        
        logger.info("用户登录成功: {}", user.username)
        
        return LoginResponse(
            accessToken = token,
            user = UserInfo(
                id = user.id!!,
                username = user.username,
                nickname = user.nickname
            )
        )
    }
    
    /**
     * 刷新令牌（可选功能）
     */
    fun refreshToken(request: RefreshTokenRequest): RefreshTokenResponse {
        // 解析旧令牌
        val claims = jwtUtil.parseToken(request.refreshToken)
            ?: throw PasswordIncorrectException("无效的刷新令牌")
        
        val userId = claims.subject.toLongOrNull()
            ?: throw PasswordIncorrectException("无效的刷新令牌")
        
        // 查询用户
        val user = userMapper.selectById(userId)
            ?: throw UserNotFoundException("用户不存在")
        
        // 生成新令牌
        val newToken = jwtUtil.generateUserToken(user.id!!, user.username)
        
        return RefreshTokenResponse(accessToken = newToken)
    }
}

