package com.zzy.blog.controller

import com.zzy.blog.dto.request.LoginRequest
import com.zzy.blog.dto.response.LoginResponse
import com.zzy.blog.dto.response.UserInfo
import com.zzy.blog.service.UserService
import com.zzy.blog.util.JwtUtil
import com.zzy.common.dto.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*

/** 认证控制器 */
@Tag(name = "认证接口")
@RestController
@RequestMapping("/api/auth")
class AuthController(
        private val userService: UserService,
        private val jwtUtil: JwtUtil,
        private val passwordEncoder: PasswordEncoder
) {

    /** 登录接口 */
    @Operation(summary = "管理员登录")
    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ApiResponse<LoginResponse> {
        // 查询用户
        val user =
                userService.getUserByUsername(request.username)
                        ?: return ApiResponse.error(401, "用户名或密码错误")

        // 验证密码
        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            return ApiResponse.error(401, "用户名或密码错误")
        }

        // 生成 Token
        val token = jwtUtil.generateToken(user.id!!, user.username)

        // 更新最后登录时间
        userService.updateLastLoginTime(user.id!!)

        val response =
                LoginResponse(
                        token = token,
                        user =
                                UserInfo(
                                        id = user.id!!,
                                        username = user.username,
                                        avatar = user.avatarUrl
                                )
                )

        return ApiResponse.success(response, "登录成功")
    }

    /** 登出接口 */
    @Operation(summary = "登出")
    @PostMapping("/logout")
    fun logout(): ApiResponse<Unit> {
        // 简化版本：客户端删除 Token 即可
        return ApiResponse.success(message = "登出成功")
    }
}
