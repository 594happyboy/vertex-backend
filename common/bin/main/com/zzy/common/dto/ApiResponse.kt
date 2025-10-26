package com.zzy.common.dto

/**
 * 统一响应结果类
 * @author ZZY
 * @date 2025-10-09
 */
data class ApiResponse<T>(
    /** 状态码 */
    val code: Int,
    
    /** 提示信息 */
    val message: String,
    
    /** 数据 */
    val data: T? = null,
    
    /** 时间戳 */
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * 成功响应
         */
        fun <T> success(data: T? = null, message: String = "操作成功"): ApiResponse<T> {
            return ApiResponse(200, message, data)
        }
        
        /**
         * 失败响应
         */
        fun <T> error(code: Int = 500, message: String = "操作失败", data: T? = null): ApiResponse<T> {
            return ApiResponse(code, message, data)
        }
        
        /**
         * 参数错误
         */
        fun <T> badRequest(message: String = "参数错误"): ApiResponse<T> {
            return ApiResponse(400, message, null)
        }
        
        /**
         * 未找到
         */
        fun <T> notFound(message: String = "资源不存在"): ApiResponse<T> {
            return ApiResponse(404, message, null)
        }
    }
}

