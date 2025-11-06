package com.zzy.common.exception

/**
 * 自定义业务异常
 * @author ZZY
 * @date 2025-10-09
 */
open class BusinessException(
    val code: Int = 500,
    override val message: String = "业务异常"
) : RuntimeException(message)

/**
 * 文件异常
 */
open class FileException(message: String) : BusinessException(500, message)

/**
 * 文件未找到异常
 */
class FileNotFoundException(message: String = "文件不存在") : BusinessException(404, message)

/**
 * 文件上传异常
 */
class FileUploadException(message: String = "文件上传失败") : BusinessException(500, message)

/**
 * 文件大小超限异常
 */
class FileSizeExceededException(message: String = "文件大小超过限制") : BusinessException(400, message)

/**
 * 文件类型不支持异常
 */
class FileTypeNotSupportedException(message: String = "不支持的文件类型") : BusinessException(400, message)

/**
 * 认证异常
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

