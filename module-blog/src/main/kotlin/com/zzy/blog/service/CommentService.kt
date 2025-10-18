package com.zzy.blog.service

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.extension.plugins.pagination.Page
import com.zzy.common.exception.BusinessException
import com.zzy.blog.dto.request.CommentRequest
import com.zzy.blog.dto.response.CommentResponse
import com.zzy.blog.dto.response.PageResponse
import com.zzy.blog.entity.Comment
import com.zzy.blog.mapper.ArticleMapper
import com.zzy.blog.mapper.CommentMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 评论服务
 */
@Service
class CommentService(
    private val commentMapper: CommentMapper,
    private val articleMapper: ArticleMapper
) {
    
    /**
     * 获取文章的评论列表（分页）
     */
    fun getCommentsByArticle(articleId: Long, page: Int, size: Int): PageResponse<CommentResponse> {
        val pageParam = Page<Map<String, Any>>(page.toLong(), size.toLong())
        val result = commentMapper.selectCommentPageByArticle(pageParam, articleId)
        
        val items = result.records.map { map ->
            CommentResponse(
                id = map["id"] as Long,
                articleId = map["article_id"] as Long,
                nickname = map["nickname"] as String,
                content = map["content"] as String,
                createdAt = map["created_at"] as LocalDateTime
            )
        }
        
        return PageResponse(
            total = result.total,
            page = page,
            size = size,
            items = items
        )
    }
    
    /**
     * 获取所有评论列表（管理后台）
     */
    fun getAllComments(page: Int, size: Int): PageResponse<Map<String, Any>> {
        val pageParam = Page<Map<String, Any>>(page.toLong(), size.toLong())
        val result = commentMapper.selectAllCommentsWithArticle(pageParam)
        
        return PageResponse(
            total = result.total,
            page = page,
            size = size,
            items = result.records
        )
    }
    
    /**
     * 创建评论
     */
    @Transactional
    fun createComment(articleId: Long, request: CommentRequest): CommentResponse {
        // 检查文章是否存在
        val article = articleMapper.selectById(articleId)
            ?: throw BusinessException(message = "文章不存在")
        
        // 创建评论
        val comment = Comment(
            articleId = articleId,
            nickname = request.nickname ?: "访客",
            content = request.content
        )
        commentMapper.insert(comment)
        
        // 更新文章评论数
        val commentCount = commentMapper.countByArticleId(articleId)
        articleMapper.updateCommentCount(articleId, commentCount)
        
        return CommentResponse(
            id = comment.id!!,
            articleId = comment.articleId,
            nickname = comment.nickname,
            content = comment.content,
            createdAt = comment.createdAt!!
        )
    }
    
    /**
     * 删除评论
     */
    @Transactional
    fun deleteComment(id: Long) {
        val comment = commentMapper.selectById(id)
            ?: throw BusinessException(message = "评论不存在")
        
        val articleId = comment.articleId
        
        // 删除评论
        commentMapper.deleteById(id)
        
        // 更新文章评论数
        val commentCount = commentMapper.countByArticleId(articleId)
        articleMapper.updateCommentCount(articleId, commentCount)
    }
    
    /**
     * 统计总评论数
     */
    fun getTotalCommentCount(): Long {
        return commentMapper.selectCount(QueryWrapper())
    }
}
