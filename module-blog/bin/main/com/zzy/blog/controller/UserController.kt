package com.zzy.blog.controller

import com.zzy.blog.dto.UserInfo
import com.zzy.common.context.AuthContextHolder
import com.zzy.common.exception.UnauthorizedException
import com.zzy.blog.service.UserService
import com.zzy.common.dto.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*

/**
 * 用户控制器
 * @author ZZY
 * @date 2025-10-29
 */
@Tag(name = "用户管理", description = "用户信息查询、修改等接口")
@RestController
@RequestMapping("/api/user")
class UserController(
    private val userService: UserService
) {
    
    /**
     * 获取当前登录用户信息
     * 
     * 用途：
     * 1. 前端初始化时获取用户信息
     * 2. 刷新页面后恢复登录状态
     * 3. 如果 AccessToken 丢失但 RefreshToken 有效，会自动刷新
     */
    @Operation(
        summary = "获取当前用户信息",
        description = "获取当前登录用户的详细信息。如果 AccessToken 过期，会自动刷新。"
    )
    @GetMapping("/me")
    fun getCurrentUser(): ApiResponse<UserInfo> {
        // 从上下文获取当前用户ID（已由拦截器设置）
        val userId = AuthContextHolder.getAuthUser()?.userId
            ?: throw UnauthorizedException("未登录")
        
        // 查询用户信息
        val userInfo = userService.getUserInfo(userId)
        
        return ApiResponse.success(userInfo, "获取成功")
    }
}

