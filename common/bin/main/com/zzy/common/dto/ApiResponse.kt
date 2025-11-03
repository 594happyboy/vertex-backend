package com.zzy.common.dto

/**
 * 统一API响应格式
 * 
 * ## 设计目的
 * 为所有API接口提供统一的响应格式，便于前端统一处理响应结果。
 * 
 * ## 响应格式
 * ```json
 * {
 *   "code": 200,
 *   "message": "操作成功",
 *   "data": { ... },
 *   "timestamp": 1699999999999
 * }
 * ```
 * 
 * ## 状态码约定
 * - 200: 成功
 * - 400: 客户端错误（参数错误、业务逻辑错误）
 * - 401: 未认证（未登录、令牌无效）
 * - 403: 无权限（已登录但权限不足）
 * - 404: 资源不存在
 * - 500: 服务器错误
 * 
 * ## 使用示例
 * ```kotlin
 * // 成功响应（带数据）
 * return ApiResponse.success(user)
 * 
 * // 成功响应（无数据）
 * return ApiResponse.success<Nothing>(message = "删除成功")
 * 
 * // 错误响应
 * return ApiResponse.error(400, "参数错误")
 * 
 * // 快捷方法
 * return ApiResponse.badRequest("用户名不能为空")
 * return ApiResponse.notFound("用户不存在")
 * ```
 * 
 * @param T 响应数据的类型
 * @property code 状态码
 * @property message 提示信息
 * @property data 响应数据（可为null）
 * @property timestamp 时间戳（毫秒）
 * 
 * @author ZZY
 * @date 2025-10-09
 */
data class ApiResponse<T>(
    /** 状态码：200成功，400-499客户端错误，500-599服务器错误 */
    val code: Int,
    
    /** 提示信息：用于前端显示给用户 */
    val message: String,
    
    /** 响应数据：业务数据，可能为null */
    val data: T? = null,
    
    /** 时间戳：服务器响应时间（毫秒） */
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * 成功响应
         * 
         * @param data 响应数据
         * @param message 提示信息，默认"操作成功"
         * @return 成功的ApiResponse对象
         */
        fun <T> success(data: T? = null, message: String = "操作成功"): ApiResponse<T> {
            return ApiResponse(200, message, data)
        }
        
        /**
         * 失败响应
         * 
         * @param code 错误码
         * @param message 错误信息
         * @param data 附加数据（可选）
         * @return 失败的ApiResponse对象
         */
        fun <T> error(code: Int = 500, message: String = "操作失败", data: T? = null): ApiResponse<T> {
            return ApiResponse(code, message, data)
        }
        
        /**
         * 参数错误响应（400）
         * 
         * @param message 错误信息
         * @return 400错误的ApiResponse对象
         */
        fun <T> badRequest(message: String = "参数错误"): ApiResponse<T> {
            return ApiResponse(400, message, null)
        }
        
        /**
         * 资源不存在响应（404）
         * 
         * @param message 错误信息
         * @return 404错误的ApiResponse对象
         */
        fun <T> notFound(message: String = "资源不存在"): ApiResponse<T> {
            return ApiResponse(404, message, null)
        }
        
        /**
         * 未授权响应（401）
         * 
         * @param message 错误信息
         * @return 401错误的ApiResponse对象
         */
        fun <T> unauthorized(message: String = "未授权访问"): ApiResponse<T> {
            return ApiResponse(401, message, null)
        }
        
        /**
         * 禁止访问响应（403）
         * 
         * @param message 错误信息
         * @return 403错误的ApiResponse对象
         */
        fun <T> forbidden(message: String = "无权限访问"): ApiResponse<T> {
            return ApiResponse(403, message, null)
        }
    }
}

