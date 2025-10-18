package com.zzy.blog.service

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.zzy.blog.entity.User
import com.zzy.blog.mapper.UserMapper
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 用户服务
 */
@Service
class UserService(
    private val userMapper: UserMapper
) {
    
    /**
     * 根据用户名查询用户
     */
    fun getUserByUsername(username: String): User? {
        return userMapper.selectOne(
            QueryWrapper<User>().eq("username", username)
        )
    }
    
    /**
     * 更新最后登录时间
     */
    fun updateLastLoginTime(userId: Long) {
        val user = User(
            id = userId,
            username = "",
            passwordHash = ""
        )
        user.lastLoginAt = LocalDateTime.now()
        userMapper.updateById(user)
    }
}
