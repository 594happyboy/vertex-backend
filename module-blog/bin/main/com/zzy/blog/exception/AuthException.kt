package com.zzy.blog.exception

import com.zzy.common.exception.BusinessException

/**
 * 认证异常
 * @author ZZY
 * @date 2025-10-18
 */
open class AuthException(message: String, code: Int = 401) : BusinessException(code, message)

/**
 * 未授权异常
 */
class UnauthorizedException(message: String = "未授权访问") : AuthException(message, 401)

/**
 * 禁止访问异常
 */
class ForbiddenException(message: String = "禁止访问") : AuthException(message, 403)

/**
 * 令牌无效异常
 */
class InvalidTokenException(message: String = "令牌无效") : AuthException(message, 401)

/**
 * 令牌过期异常
 */
class TokenExpiredException(message: String = "令牌已过期") : AuthException(message, 401)

/**
 * 用户不存在异常
 */
class UserNotFoundException(message: String = "用户不存在") : BusinessException(404, message)

/**
 * 密码错误异常
 */
class PasswordIncorrectException(message: String = "密码错误") : AuthException(message, 401)

/**
 * 资源不存在异常
 */
class ResourceNotFoundException(message: String = "资源不存在") : BusinessException(404, message)

