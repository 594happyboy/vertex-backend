package com.zzy.blog.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.baomidou.mybatisplus.core.metadata.IPage
import com.baomidou.mybatisplus.extension.plugins.pagination.Page
import com.zzy.blog.entity.Article
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update

/**
 * 文章 Mapper
 */
@Mapper
interface ArticleMapper : BaseMapper<Article> {
    
    /**
     * 增加文章浏览量
     */
    @Update("UPDATE blog_articles SET views = views + 1 WHERE id = #{id}")
    fun incrementViews(@Param("id") id: Long): Int
    
    /**
     * 更新文章评论数
     */
    @Update("UPDATE blog_articles SET comment_count = #{count} WHERE id = #{articleId}")
    fun updateCommentCount(@Param("articleId") articleId: Long, @Param("count") count: Int): Int
    
    /**
     * 分页查询文章列表（带分组名称）
     */
    @Select("""
        <script>
        SELECT a.*, g.name as group_name 
        FROM blog_articles a 
        LEFT JOIN blog_groups g ON a.group_id = g.id 
        WHERE 1=1
        <if test="status != null">
            AND a.status = #{status}
        </if>
        <if test="groupId != null">
            AND a.group_id = #{groupId}
        </if>
        <if test="keyword != null and keyword != ''">
            AND (a.title LIKE CONCAT('%', #{keyword}, '%') 
                 OR a.summary LIKE CONCAT('%', #{keyword}, '%'))
        </if>
        ORDER BY 
        <choose>
            <when test="orderBy == 'views'">
                a.views DESC
            </when>
            <when test="orderBy == 'comments'">
                a.comment_count DESC
            </when>
            <otherwise>
                a.publish_time DESC, a.created_at DESC
            </otherwise>
        </choose>
        </script>
    """)
    fun selectArticlePageWithGroup(
        page: Page<Map<String, Any>>,
        @Param("status") status: String?,
        @Param("groupId") groupId: Long?,
        @Param("keyword") keyword: String?,
        @Param("orderBy") orderBy: String?
    ): IPage<Map<String, Any>>
    
    /**
     * 查询文章详情（带分组名称）
     */
    @Select("""
        SELECT a.*, g.name as group_name 
        FROM blog_articles a 
        LEFT JOIN blog_groups g ON a.group_id = g.id 
        WHERE a.id = #{id}
    """)
    fun selectArticleWithGroup(@Param("id") id: Long): Map<String, Any>?
    
    /**
     * 根据 slug 查询文章详情（带分组名称）
     */
    @Select("""
        SELECT a.*, g.name as group_name 
        FROM blog_articles a 
        LEFT JOIN blog_groups g ON a.group_id = g.id 
        WHERE a.slug = #{slug}
    """)
    fun selectArticleBySlugWithGroup(@Param("slug") slug: String): Map<String, Any>?
    
    /**
     * 统计各状态文章数
     */
    @Select("SELECT COUNT(*) FROM blog_articles WHERE status = #{status}")
    fun countByStatus(@Param("status") status: String): Long
    
    /**
     * 统计总浏览量
     */
    @Select("SELECT IFNULL(SUM(views), 0) FROM blog_articles")
    fun sumTotalViews(): Long
}

