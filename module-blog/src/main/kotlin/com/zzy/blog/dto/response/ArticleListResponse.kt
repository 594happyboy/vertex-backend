package com.zzy.blog.dto.response

import java.time.LocalDateTime

/**
 * 文章列表响应 DTO
 */
data class ArticleListResponse(
    /**
     * 文章ID
     */
    val id: Long,
    
    /**
     * 文章标题
     */
    val title: String,
    
    /**
     * URL slug
     */
    val slug: String,
    
    /**
     * 文章摘要
     */
    val summary: String?,
    
    /**
     * 封面图URL
     */
    val coverUrl: String?,
    
    /**
     * 内容类型
     */
    val contentType: String,
    
    /**
     * 分组名称
     */
    val groupName: String?,
    
    /**
     * 标签列表
     */
    val tags: List<String>,
    
    /**
     * 发布时间
     */
    val publishTime: LocalDateTime?,
    
    /**
     * 浏览量
     */
    val views: Long,
    
    /**
     * 评论数
     */
    val commentCount: Int
)

/**
 * 分页列表响应 DTO
 */
data class PageResponse<T>(
    /**
     * 总数
     */
    val total: Long,
    
    /**
     * 当前页码
     */
    val page: Int,
    
    /**
     * 每页数量
     */
    val size: Int,
    
    /**
     * 数据列表
     */
    val items: List<T>
)

