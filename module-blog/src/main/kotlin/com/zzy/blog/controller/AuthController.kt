package com.zzy.blog.controller

import com.zzy.blog.dto.*
import com.zzy.blog.service.AuthService
import com.zzy.common.constants.AuthConstants
import com.zzy.common.context.AuthContextHolder
import com.zzy.common.dto.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.*

/**
 * 认证控制器（双Token模式）
 * @author ZZY
 * @date 2025-10-18
 */
@Tag(name = "认证管理", description = "用户登录、登出等认证相关接口")
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService
) {
    
    /**
     * 用户登录（双Token模式）
     * 
     * 返回：
     * - accessToken: 在响应体中返回（前端存储在内存）
     * - refreshToken: 在HttpOnly Cookie中返回（浏览器自动管理）
     */
    @Operation(
        summary = "用户登录", 
        description = "使用用户名和密码登录。返回accessToken（存内存）和refreshToken（HttpOnly Cookie）"
    )
    @PostMapping("/login")
    fun login(
        @RequestBody request: LoginRequest,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse
    ): ApiResponse<LoginResponse> {
        val response = authService.login(request, httpRequest)
        
        // 将 RefreshToken 设置到 HttpOnly Cookie（防XSS）
        val cookie = Cookie(AuthConstants.REFRESH_TOKEN_COOKIE_NAME, response.refreshToken).apply {
            isHttpOnly = true      // 防止JS访问
            secure = false         // 生产环境应设为true（仅HTTPS）
            path = "/"
            maxAge = AuthConstants.COOKIE_MAX_AGE
        }
        httpResponse.addCookie(cookie)
        
        // 返回响应（不在响应体中暴露refreshToken，仅返回accessToken）
        return ApiResponse.success(
            LoginResponse(
                accessToken = response.accessToken,
                refreshToken = "[已设置到HttpOnly Cookie]",  // 提示信息
                user = response.user
            ),
            "登录成功"
        )
    }

    /**
     * 用户登出
     * 
     * 功能：
     * - 撤销该用户所有的RefreshToken（Redis）
     * - 清除RefreshToken Cookie
     */
    @Operation(
        summary = "用户登出",
        description = "撤销所有RefreshToken，清除Cookie。前端需同时清除accessToken"
    )
    @PostMapping("/logout")
    fun logout(httpResponse: HttpServletResponse): ApiResponse<Unit> {
        // 从上下文获取当前用户ID
        val userId = AuthContextHolder.getAuthUser()?.userId
            ?: return ApiResponse.error(401, "未登录")
        
        // 撤销所有RefreshToken
        authService.logout(userId)
        
        // 清除Cookie
        val cookie = Cookie(AuthConstants.REFRESH_TOKEN_COOKIE_NAME, "").apply {
            isHttpOnly = true
            secure = false
            path = "/"
            maxAge = 0  // 立即过期
        }
        httpResponse.addCookie(cookie)
        
        return ApiResponse.success(null, "登出成功")
    }

}

