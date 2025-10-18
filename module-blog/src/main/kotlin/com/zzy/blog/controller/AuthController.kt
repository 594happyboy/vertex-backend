package com.zzy.blog.controller

import com.zzy.blog.dto.*
import com.zzy.blog.service.AuthService
import com.zzy.common.dto.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*

/**
 * 认证控制器
 * @author ZZY
 * @date 2025-10-18
 */
@Tag(name = "认证管理", description = "用户登录、游客令牌等认证相关接口")
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService
) {
    
    /**
     * 用户登录
     */
    @Operation(summary = "用户登录", description = "使用用户名和密码登录，返回JWT令牌")
    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ApiResponse<LoginResponse> {
        val response = authService.login(request)
        return ApiResponse.success(response, "登录成功")
    }
    
    /**
     * 获取游客令牌
     */
    @Operation(summary = "获取游客令牌", description = "获取只读访问令牌，用于访问已发布的内容")
    @PostMapping("/visitor")
    fun getVisitorToken(@RequestBody request: VisitorTokenRequest): ApiResponse<VisitorTokenResponse> {
        val response = authService.getVisitorToken(request)
        return ApiResponse.success(response, "获取游客令牌成功")
    }
    
    /**
     * 刷新令牌（可选）
     */
    @Operation(summary = "刷新令牌", description = "使用刷新令牌获取新的访问令牌")
    @PostMapping("/refresh")
    fun refreshToken(@RequestBody request: RefreshTokenRequest): ApiResponse<RefreshTokenResponse> {
        val response = authService.refreshToken(request)
        return ApiResponse.success(response, "刷新令牌成功")
    }
}

