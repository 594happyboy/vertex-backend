package com.zzy.blog.dto.response

/**
 * 统计数据响应 DTO
 */
data class StatsResponse(
    /**
     * 文章总数
     */
    val articleCount: Long,
    
    /**
     * 已发布文章数
     */
    val publishedCount: Long,
    
    /**
     * 草稿数
     */
    val draftCount: Long,
    
    /**
     * 评论总数
     */
    val commentCount: Long,
    
    /**
     * 总浏览量
     */
    val totalViews: Long,
    
    /**
     * 今日浏览量
     */
    val todayViews: Long
)

