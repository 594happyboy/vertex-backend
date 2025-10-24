package com.zzy.common.filter

import com.fasterxml.jackson.databind.ObjectMapper
import com.zzy.common.dto.ApiResponse
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.multipart.MaxUploadSizeExceededException

/**
 * 文件上传异常过滤器
 * 在最外层捕获文件大小超限异常，确保响应能正确返回给前端
 * @author ZZY
 * @date 2025-10-23
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class MultipartExceptionFilter(
    private val objectMapper: ObjectMapper,
    @Value("\${spring.servlet.multipart.max-file-size:100MB}")
    private val maxFileSize: String
) : OncePerRequestFilter() {
    
    private val logger = LoggerFactory.getLogger(javaClass)
    
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            filterChain.doFilter(request, response)
        } catch (e: Throwable) {
            if (e.findCause<MaxUploadSizeExceededException>() != null) {
                writeErrorResponse(response)
            } else {
                throw e
            }
        }
    }
    
    private fun writeErrorResponse(response: HttpServletResponse) {
        if (response.isCommitted) {
            logger.warn("响应已提交，无法写入错误信息")
            return
        }
        
        logger.warn("文件大小超限，最大支持: $maxFileSize")
        
        response.apply {
            status = HttpServletResponse.SC_BAD_REQUEST
            contentType = MediaType.APPLICATION_JSON_VALUE
            characterEncoding = "UTF-8"
            
            runCatching {
                val apiResponse = ApiResponse.error<Nothing>(400, "文件大小超过限制，最大支持$maxFileSize")
                writer.write(objectMapper.writeValueAsString(apiResponse))
                writer.flush()
            }.onFailure { 
                logger.error("写入JSON响应失败", it)
            }
        }
    }
    
    private inline fun <reified T : Throwable> Throwable.findCause(): T? {
        var current: Throwable? = this
        repeat(10) {
            when (current) {
                null -> return null
                is T -> return current as T
                else -> current = current?.cause
            }
        }
        return null
    }
}

