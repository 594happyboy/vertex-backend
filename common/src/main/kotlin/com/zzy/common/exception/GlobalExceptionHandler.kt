package com.zzy.common.exception

import com.zzy.common.dto.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * 全局异常处理器
 * @author ZZY
 * @date 2025-10-09
 */
@RestControllerAdvice
class GlobalExceptionHandler {
    
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    
    /**
     * 业务异常处理
     */
    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(e: BusinessException): ApiResponse<Nothing> {
        logger.error("业务异常: {}", e.message)
        return ApiResponse.error(e.code, e.message)
    }
    
    /**
     * 文件大小超限异常
     */
    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleMaxUploadSizeExceededException(e: MaxUploadSizeExceededException): ApiResponse<Nothing> {
        logger.error("文件大小超限: {}", e.message)
        return ApiResponse.error(400, "文件大小超过限制，最大支持100MB")
    }
    
    /**
     * 资源未找到异常
     */
    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFoundException(e: NoResourceFoundException): ApiResponse<Nothing> {
        logger.error("资源未找到: {}", e.message)
        return ApiResponse.notFound("请求的资源不存在")
    }
    
    /**
     * 非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(e: IllegalArgumentException): ApiResponse<Nothing> {
        logger.error("参数异常: {}", e.message)
        return ApiResponse.badRequest(e.message ?: "参数错误")
    }
    
    /**
     * 通用异常处理
     */
    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ApiResponse<Nothing> {
        logger.error("系统异常: ", e)
        return ApiResponse.error(500, "系统异常: ${e.message}")
    }
}

