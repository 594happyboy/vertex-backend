package com.zzy.blog.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.baomidou.mybatisplus.core.metadata.IPage
import com.baomidou.mybatisplus.extension.plugins.pagination.Page
import com.zzy.blog.entity.Comment
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select

/**
 * 评论 Mapper
 */
@Mapper
interface CommentMapper : BaseMapper<Comment> {
    
    /**
     * 分页查询评论列表（带文章标题）
     */
    @Select("""
        SELECT c.*, a.title as article_title 
        FROM blog_comments c 
        LEFT JOIN blog_articles a ON c.article_id = a.id 
        WHERE c.article_id = #{articleId}
        ORDER BY c.created_at DESC
    """)
    fun selectCommentPageByArticle(
        page: Page<Map<String, Any>>,
        @Param("articleId") articleId: Long
    ): IPage<Map<String, Any>>
    
    /**
     * 查询所有评论（管理后台）
     */
    @Select("""
        SELECT c.*, a.title as article_title 
        FROM blog_comments c 
        LEFT JOIN blog_articles a ON c.article_id = a.id 
        ORDER BY c.created_at DESC
    """)
    fun selectAllCommentsWithArticle(page: Page<Map<String, Any>>): IPage<Map<String, Any>>
    
    /**
     * 统计文章的评论数
     */
    @Select("SELECT COUNT(*) FROM blog_comments WHERE article_id = #{articleId}")
    fun countByArticleId(@Param("articleId") articleId: Long): Int
}



