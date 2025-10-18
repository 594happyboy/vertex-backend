package com.zzy.blog.controller

import com.zzy.common.dto.ApiResponse
import com.zzy.blog.dto.request.ArticleRequest
import com.zzy.blog.dto.response.ArticleDetailResponse
import com.zzy.blog.dto.response.ArticleListResponse
import com.zzy.blog.dto.response.PageResponse
import com.zzy.blog.entity.Article
import com.zzy.blog.service.ArticleService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*

/**
 * 文章管理接口控制器
 */
@Tag(name = "文章管理接口")
@RestController
@RequestMapping("/api/admin/articles")
class ArticleAdminController(
    private val articleService: ArticleService
) {
    
    /**
     * 获取所有文章列表（含草稿）
     */
    @Operation(summary = "获取所有文章列表")
    @GetMapping
    fun getAllArticles(
        @Parameter(description = "页码") @RequestParam(defaultValue = "1") page: Int,
        @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") size: Int,
        @Parameter(description = "状态") @RequestParam(required = false) status: String?,
        @Parameter(description = "分组ID") @RequestParam(required = false) groupId: Long?,
        @Parameter(description = "关键词") @RequestParam(required = false) keyword: String?
    ): ApiResponse<PageResponse<ArticleListResponse>> {
        val result = articleService.getArticleList(
            page = page,
            size = size,
            status = status,
            groupId = groupId,
            keyword = keyword
        )
        return ApiResponse.success(result)
    }
    
    /**
     * 根据 ID 获取文章详情
     */
    @Operation(summary = "获取文章详情")
    @GetMapping("/{id}")
    fun getArticleById(
        @Parameter(description = "文章ID") @PathVariable id: Long
    ): ApiResponse<ArticleDetailResponse> {
        val article = articleService.getArticleById(id)
        return ApiResponse.success(article)
    }
    
    /**
     * 创建文章
     */
    @Operation(summary = "创建文章")
    @PostMapping
    fun createArticle(
        @RequestBody request: ArticleRequest
    ): ApiResponse<Map<String, Any>> {
        val article = articleService.createArticle(request)
        return ApiResponse.success(
            mapOf(
                "id" to article.id!!,
                "slug" to article.slug
            ),
            "创建成功"
        )
    }
    
    /**
     * 更新文章
     */
    @Operation(summary = "更新文章")
    @PutMapping("/{id}")
    fun updateArticle(
        @Parameter(description = "文章ID") @PathVariable id: Long,
        @RequestBody request: ArticleRequest
    ): ApiResponse<Map<String, Any>> {
        val article = articleService.updateArticle(id, request)
        return ApiResponse.success(
            mapOf(
                "id" to article.id!!,
                "slug" to article.slug
            ),
            "更新成功"
        )
    }
    
    /**
     * 删除文章
     */
    @Operation(summary = "删除文章")
    @DeleteMapping("/{id}")
    fun deleteArticle(
        @Parameter(description = "文章ID") @PathVariable id: Long
    ): ApiResponse<Unit> {
        articleService.deleteArticle(id)
        return ApiResponse.success(message = "删除成功")
    }
}
