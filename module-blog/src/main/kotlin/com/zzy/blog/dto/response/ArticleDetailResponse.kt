package com.zzy.blog.dto.response

import java.time.LocalDateTime

/**
 * 文章详情响应 DTO
 */
data class ArticleDetailResponse(
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
     * 内容类型
     */
    val contentType: String,
    
    /**
     * Markdown 原始内容
     */
    val contentText: String?,
    
    /**
     * PDF 文件URL
     */
    val contentUrl: String?,
    
    /**
     * 封面图URL
     */
    val coverUrl: String?,
    
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
 * 评论响应 DTO
 */
data class CommentResponse(
    /**
     * 评论ID
     */
    val id: Long,
    
    /**
     * 文章ID
     */
    val articleId: Long,
    
    /**
     * 昵称
     */
    val nickname: String,
    
    /**
     * 评论内容
     */
    val content: String,
    
    /**
     * 创建时间
     */
    val createdAt: LocalDateTime
)

/**
 * 分组响应 DTO
 */
data class GroupResponse(
    /**
     * 分组ID
     */
    val id: Long,
    
    /**
     * 分组名称
     */
    val name: String,
    
    /**
     * URL slug
     */
    val slug: String,
    
    /**
     * 分组描述
     */
    val description: String?,
    
    /**
     * 排序序号
     */
    val orderIndex: Int,
    
    /**
     * 文章数量
     */
    val articleCount: Int = 0
)

/**
 * 登录响应 DTO
 */
data class LoginResponse(
    /**
     * JWT Token
     */
    val token: String,
    
    /**
     * 用户信息
     */
    val user: UserInfo
)

/**
 * 用户信息 DTO
 */
data class UserInfo(
    /**
     * 用户ID
     */
    val id: Long,
    
    /**
     * 用户名
     */
    val username: String,
    
    /**
     * 头像URL
     */
    val avatar: String?
)

