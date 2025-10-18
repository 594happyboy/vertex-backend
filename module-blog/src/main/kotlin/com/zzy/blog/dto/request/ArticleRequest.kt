package com.zzy.blog.dto.request

/**
 * 文章请求 DTO
 */
data class ArticleRequest(
    /**
     * 文章标题
     */
    val title: String,
    
    /**
     * URL slug（可选，不传则自动生成）
     */
    val slug: String? = null,
    
    /**
     * 文章摘要
     */
    val summary: String? = null,
    
    /**
     * 内容类型（markdown/pdf）
     */
    val contentType: String,
    
    /**
     * Markdown 原始内容
     */
    val contentText: String? = null,
    
    /**
     * PDF 文件URL
     */
    val contentUrl: String? = null,
    
    /**
     * 封面图URL
     */
    val coverUrl: String? = null,
    
    /**
     * 所属分组ID
     */
    val groupId: Long? = null,
    
    /**
     * 标签列表
     */
    val tags: List<String>? = null,
    
    /**
     * 文章状态（draft/published）
     */
    val status: String? = "draft"
)

/**
 * 登录请求 DTO
 */
data class LoginRequest(
    /**
     * 用户名
     */
    val username: String,
    
    /**
     * 密码
     */
    val password: String
)

/**
 * 分组请求 DTO
 */
data class GroupRequest(
    /**
     * 分组名称
     */
    val name: String,
    
    /**
     * URL slug（可选，不传则自动生成）
     */
    val slug: String? = null,
    
    /**
     * 分组描述
     */
    val description: String? = null,
    
    /**
     * 排序序号
     */
    val orderIndex: Int = 0
)

