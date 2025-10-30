package com.zzy.blog.service

import com.zzy.blog.dto.UserInfo
import com.zzy.blog.exception.UserNotFoundException
import com.zzy.blog.mapper.UserMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 用户服务
 * @author ZZY
 * @date 2025-10-29
 */
@Service
class UserService(
    private val userMapper: UserMapper
) {
    
    private val logger = LoggerFactory.getLogger(UserService::class.java)
    
    /**
     * 获取用户信息
     * 
     * @param userId 用户ID
     * @return 用户信息
     */
    fun getUserInfo(userId: Long): UserInfo {
        val user = userMapper.selectById(userId)
            ?: throw UserNotFoundException("用户不存在")
        
        logger.debug("获取用户信息: userId={}, username={}", userId, user.username)
        
        return UserInfo(
            id = user.id!!,
            username = user.username,
            nickname = user.nickname
        )
    }
}

