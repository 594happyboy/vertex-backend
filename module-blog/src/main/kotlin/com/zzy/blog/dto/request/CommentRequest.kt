package com.zzy.blog.dto.request

/**
 * 评论请求 DTO
 */
data class CommentRequest(
    /**
     * 昵称（可选，默认"访客"）
     */
    val nickname: String? = "访客",
    
    /**
     * 评论内容
     */
    val content: String
)

