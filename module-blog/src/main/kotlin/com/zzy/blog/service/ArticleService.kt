package com.zzy.blog.service

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.extension.plugins.pagination.Page
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.zzy.common.exception.BusinessException
import com.zzy.blog.dto.request.ArticleRequest
import com.zzy.blog.dto.response.ArticleDetailResponse
import com.zzy.blog.dto.response.ArticleListResponse
import com.zzy.blog.dto.response.PageResponse
import com.zzy.blog.entity.Article
import com.zzy.blog.enums.ArticleStatus
import com.zzy.blog.enums.ContentType
import com.zzy.blog.mapper.ArticleMapper
import com.zzy.blog.util.SlugUtil
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 文章服务
 */
@Service
class ArticleService(
    private val articleMapper: ArticleMapper
) {
    
    private val objectMapper = jacksonObjectMapper()
    
    /**
     * 获取文章列表（分页）
     */
    fun getArticleList(
        page: Int,
        size: Int,
        status: String? = null,
        groupId: Long? = null,
        keyword: String? = null,
        orderBy: String? = null
    ): PageResponse<ArticleListResponse> {
        val pageParam = Page<Map<String, Any>>(page.toLong(), size.toLong())
        val result = articleMapper.selectArticlePageWithGroup(
            pageParam,
            status,
            groupId,
            keyword,
            orderBy
        )
        
        val items = result.records.map { map ->
            convertToArticleListResponse(map)
        }
        
        return PageResponse(
            total = result.total,
            page = page,
            size = size,
            items = items
        )
    }
    
    /**
     * 根据 slug 获取文章详情
     */
    @Transactional
    fun getArticleBySlug(slug: String, incrementView: Boolean = true): ArticleDetailResponse {
        val articleMap = articleMapper.selectArticleBySlugWithGroup(slug)?.toMutableMap()
            ?: throw BusinessException(message = "文章不存在")
        
        val articleId = articleMap["id"] as Long
        
        // 增加浏览量
        if (incrementView) {
            articleMapper.incrementViews(articleId)
            articleMap["views"] = (articleMap["views"] as Long) + 1
        }
        
        return convertToArticleDetailResponse(articleMap)
    }
    
    /**
     * 根据 ID 获取文章详情
     */
    fun getArticleById(id: Long): ArticleDetailResponse {
        val articleMap = articleMapper.selectArticleWithGroup(id)
            ?: throw BusinessException(message = "文章不存在")
        
        return convertToArticleDetailResponse(articleMap)
    }
    
    /**
     * 创建文章
     */
    @Transactional
    fun createArticle(request: ArticleRequest): Article {
        // 验证内容类型
        val contentType = ContentType.fromValue(request.contentType)
            ?: throw BusinessException(message = "无效的内容类型")
        
        // 验证内容
        when (contentType) {
            ContentType.MARKDOWN -> {
                if (request.contentText.isNullOrBlank()) {
                    throw BusinessException(message = "Markdown 内容不能为空")
                }
            }
            ContentType.PDF -> {
                if (request.contentUrl.isNullOrBlank()) {
                    throw BusinessException(message = "PDF 文件 URL 不能为空")
                }
            }
        }
        
        // 生成 slug
        val slug = generateUniqueSlug(request.slug, request.title)
        
        // 转换标签为 JSON 字符串
        val tagsJson = if (!request.tags.isNullOrEmpty()) {
            objectMapper.writeValueAsString(request.tags)
        } else {
            null
        }
        
        // 创建文章
        val article = Article(
            title = request.title,
            slug = slug,
            summary = request.summary,
            contentType = request.contentType,
            contentText = request.contentText,
            contentUrl = request.contentUrl,
            coverUrl = request.coverUrl,
            groupId = request.groupId,
            tags = tagsJson,
            status = request.status ?: ArticleStatus.DRAFT.value
        )
        
        // 如果状态是已发布，设置发布时间
        if (article.status == ArticleStatus.PUBLISHED.value) {
            article.publishTime = LocalDateTime.now()
        }
        
        articleMapper.insert(article)
        return article
    }
    
    /**
     * 更新文章
     */
    @Transactional
    fun updateArticle(id: Long, request: ArticleRequest): Article {
        val article = articleMapper.selectById(id)
            ?: throw BusinessException(message = "文章不存在")
        
        // 验证内容类型
        val contentType = ContentType.fromValue(request.contentType)
            ?: throw BusinessException(message = "无效的内容类型")
        
        // 验证内容
        when (contentType) {
            ContentType.MARKDOWN -> {
                if (request.contentText.isNullOrBlank()) {
                    throw BusinessException(message = "Markdown 内容不能为空")
                }
            }
            ContentType.PDF -> {
                if (request.contentUrl.isNullOrBlank()) {
                    throw BusinessException(message = "PDF 文件 URL 不能为空")
                }
            }
        }
        
        // 如果更新 slug，需要检查唯一性
        val newSlug = request.slug ?: SlugUtil.generateSlug(request.title)
        if (newSlug != article.slug) {
            val existing = articleMapper.selectOne(
                QueryWrapper<Article>().eq("slug", newSlug).ne("id", id)
            )
            if (existing != null) {
                throw BusinessException(message = "Slug '$newSlug' 已存在")
            }
        }
        
        // 转换标签为 JSON 字符串
        val tagsJson = if (!request.tags.isNullOrEmpty()) {
            objectMapper.writeValueAsString(request.tags)
        } else {
            null
        }
        
        // 更新文章
        val oldStatus = article.status
        article.title = request.title
        article.slug = newSlug
        article.summary = request.summary
        article.contentType = request.contentType
        article.contentText = request.contentText
        article.contentUrl = request.contentUrl
        article.coverUrl = request.coverUrl
        article.groupId = request.groupId
        article.tags = tagsJson
        article.status = request.status ?: ArticleStatus.DRAFT.value
        
        // 如果从草稿改为发布，设置发布时间
        if (oldStatus == ArticleStatus.DRAFT.value && 
            article.status == ArticleStatus.PUBLISHED.value) {
            article.publishTime = LocalDateTime.now()
        }
        
        articleMapper.updateById(article)
        return article
    }
    
    /**
     * 删除文章
     */
    @Transactional
    fun deleteArticle(id: Long) {
        val article = articleMapper.selectById(id)
            ?: throw BusinessException(message = "文章不存在")
        
        articleMapper.deleteById(id)
    }
    
    /**
     * 生成唯一的 slug
     */
    private fun generateUniqueSlug(customSlug: String?, title: String): String {
        var slug = customSlug ?: SlugUtil.generateSlug(title)
        
        // 检查 slug 是否已存在
        var suffix = 1
        var finalSlug = slug
        while (true) {
            val existing = articleMapper.selectOne(
                QueryWrapper<Article>().eq("slug", finalSlug)
            )
            if (existing == null) {
                break
            }
            finalSlug = SlugUtil.addSuffix(slug, suffix++)
        }
        
        return finalSlug
    }
    
    /**
     * 转换为文章列表响应
     */
    private fun convertToArticleListResponse(map: Map<String, Any>): ArticleListResponse {
        val tags = map["tags"]?.let { tagsStr ->
            if (tagsStr is String && tagsStr.isNotBlank()) {
                objectMapper.readValue(tagsStr, List::class.java) as List<String>
            } else {
                emptyList()
            }
        } ?: emptyList()
        
        return ArticleListResponse(
            id = map["id"] as Long,
            title = map["title"] as String,
            slug = map["slug"] as String,
            summary = map["summary"] as? String,
            coverUrl = map["cover_url"] as? String,
            contentType = map["content_type"] as String,
            groupName = map["group_name"] as? String,
            tags = tags,
            publishTime = map["publish_time"] as? LocalDateTime,
            views = map["views"] as Long,
            commentCount = (map["comment_count"] as? Number)?.toInt() ?: 0
        )
    }
    
    /**
     * 转换为文章详情响应
     */
    private fun convertToArticleDetailResponse(map: Map<String, Any>): ArticleDetailResponse {
        val tags = map["tags"]?.let { tagsStr ->
            if (tagsStr is String && tagsStr.isNotBlank()) {
                objectMapper.readValue(tagsStr, List::class.java) as List<String>
            } else {
                emptyList()
            }
        } ?: emptyList()
        
        return ArticleDetailResponse(
            id = map["id"] as Long,
            title = map["title"] as String,
            slug = map["slug"] as String,
            summary = map["summary"] as? String,
            contentType = map["content_type"] as String,
            contentText = map["content_text"] as? String,
            contentUrl = map["content_url"] as? String,
            coverUrl = map["cover_url"] as? String,
            groupName = map["group_name"] as? String,
            tags = tags,
            publishTime = map["publish_time"] as? LocalDateTime,
            views = map["views"] as Long,
            commentCount = (map["comment_count"] as? Number)?.toInt() ?: 0
        )
    }
}
