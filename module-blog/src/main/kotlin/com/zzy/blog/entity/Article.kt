package com.zzy.blog.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import com.zzy.blog.enums.ArticleStatus
import com.zzy.blog.enums.ContentType
import java.time.LocalDateTime

/**
 * 文章实体类
 */
@TableName("blog_articles")
data class Article(
    /**
     * 文章ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    var id: Long? = null,
    
    /**
     * 文章标题
     */
    var title: String,
    
    /**
     * URL slug
     */
    var slug: String,
    
    /**
     * 文章摘要
     */
    var summary: String? = null,
    
    /**
     * 内容类型（markdown/pdf）
     */
    var contentType: String,
    
    /**
     * Markdown 原始内容
     */
    var contentText: String? = null,
    
    /**
     * PDF 文件URL
     */
    var contentUrl: String? = null,
    
    /**
     * 封面图URL
     */
    var coverUrl: String? = null,
    
    /**
     * 所属分组ID
     */
    var groupId: Long? = null,
    
    /**
     * 标签（JSON数组字符串）
     */
    var tags: String? = null,
    
    /**
     * 文章状态（draft/published）
     */
    var status: String = ArticleStatus.DRAFT.value,
    
    /**
     * 发布时间
     */
    var publishTime: LocalDateTime? = null,
    
    /**
     * 浏览量
     */
    var views: Long = 0,
    
    /**
     * 评论数
     */
    var commentCount: Int = 0,
    
    /**
     * 创建时间
     */
    var createdAt: LocalDateTime? = null,
    
    /**
     * 更新时间
     */
    var updatedAt: LocalDateTime? = null
) {
    /**
     * 获取内容类型枚举
     */
    fun getContentTypeEnum(): ContentType? {
        return ContentType.fromValue(contentType)
    }
    
    /**
     * 获取状态枚举
     */
    fun getStatusEnum(): ArticleStatus? {
        return ArticleStatus.fromValue(status)
    }
    
    /**
     * 判断是否已发布
     */
    fun isPublished(): Boolean {
        return status == ArticleStatus.PUBLISHED.value
    }
    
    /**
     * 判断是否为 Markdown 文章
     */
    fun isMarkdown(): Boolean {
        return contentType == ContentType.MARKDOWN.value
    }
    
    /**
     * 判断是否为 PDF 文章
     */
    fun isPdf(): Boolean {
        return contentType == ContentType.PDF.value
    }
}

