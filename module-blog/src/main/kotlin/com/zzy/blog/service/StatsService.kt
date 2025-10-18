package com.zzy.blog.service

import com.zzy.blog.dto.response.StatsResponse
import com.zzy.blog.enums.ArticleStatus
import com.zzy.blog.mapper.ArticleMapper
import com.zzy.blog.mapper.CommentMapper
import org.springframework.stereotype.Service

/**
 * 统计服务
 */
@Service
class StatsService(
    private val articleMapper: ArticleMapper,
    private val commentMapper: CommentMapper
) {
    
    /**
     * 获取仪表盘统计数据
     */
    fun getDashboardStats(): StatsResponse {
        // 文章统计
        val totalArticles = articleMapper.selectCount(null)
        val publishedCount = articleMapper.countByStatus(ArticleStatus.PUBLISHED.value)
        val draftCount = articleMapper.countByStatus(ArticleStatus.DRAFT.value)
        
        // 评论统计
        val totalComments = commentMapper.selectCount(null)
        
        // 浏览量统计
        val totalViews = articleMapper.sumTotalViews()
        
        // TODO: 实现今日浏览量统计（需要记录每日浏览量）
        val todayViews = 0L
        
        return StatsResponse(
            articleCount = totalArticles,
            publishedCount = publishedCount,
            draftCount = draftCount,
            commentCount = totalComments,
            totalViews = totalViews,
            todayViews = todayViews
        )
    }
}
