package com.zzy.common.exception

import com.zzy.common.dto.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.MultipartException
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * 全局异常处理器
 * @author ZZY
 * @date 2025-10-09
 */
@RestControllerAdvice
class GlobalExceptionHandler(
    @Value("\${spring.servlet.multipart.max-file-size:100MB}")
    private val maxFileSize: String
) {
    
    private val logger = LoggerFactory.getLogger(javaClass)
    
    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(e: BusinessException): ApiResponse<Nothing> {
        logger.error("业务异常: ${e.message}")
        return ApiResponse.error(e.code, e.message)
    }
    
    @ExceptionHandler(MultipartException::class, MaxUploadSizeExceededException::class)
    fun handleMultipartException(e: Exception): ResponseEntity<ApiResponse<Nothing>> {
        val message = if (e.findCause<MaxUploadSizeExceededException>() != null) {
            logger.warn("文件大小超限，最大支持: $maxFileSize")
            "文件大小超过限制，最大支持$maxFileSize"
        } else {
            logger.error("文件上传失败: ${e.message}")
            "文件上传失败"
        }
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(400, message))
    }
    
    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFoundException(e: NoResourceFoundException): ApiResponse<Nothing> {
        logger.warn("资源未找到: ${e.resourcePath}")
        return ApiResponse.notFound("请求的资源不存在")
    }
    
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(e: IllegalArgumentException): ApiResponse<Nothing> {
        logger.warn("参数异常: ${e.message}")
        return ApiResponse.badRequest(e.message ?: "参数错误")
    }
    
    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ApiResponse<Nothing> {
        logger.error("系统异常", e)
        return ApiResponse.error(500, "系统异常: ${e.message}")
    }
    
    private inline fun <reified T : Throwable> Throwable.findCause(): T? =
        generateSequence(this) { it.cause }
            .take(10)
            .filterIsInstance<T>()
            .firstOrNull()
}

