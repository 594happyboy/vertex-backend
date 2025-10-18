package com.zzy.blog.controller

import com.zzy.common.dto.ApiResponse
import com.zzy.blog.dto.response.ArticleDetailResponse
import com.zzy.blog.dto.response.ArticleListResponse
import com.zzy.blog.dto.response.PageResponse
import com.zzy.blog.enums.ArticleStatus
import com.zzy.blog.service.ArticleService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*

/**
 * 文章公共接口控制器
 */
@Tag(name = "文章公共接口")
@RestController
@RequestMapping("/api/articles")
class ArticleController(
    private val articleService: ArticleService
) {
    
    /**
     * 获取文章列表（仅已发布）
     */
    @Operation(summary = "获取文章列表")
    @GetMapping
    fun getArticles(
        @Parameter(description = "页码") @RequestParam(defaultValue = "1") page: Int,
        @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") size: Int,
        @Parameter(description = "分组ID") @RequestParam(required = false) group: Long?,
        @Parameter(description = "关键词") @RequestParam(required = false) keyword: String?,
        @Parameter(description = "排序方式（views, comments）") @RequestParam(required = false) orderBy: String?
    ): ApiResponse<PageResponse<ArticleListResponse>> {
        val result = articleService.getArticleList(
            page = page,
            size = size,
            status = ArticleStatus.PUBLISHED.value,
            groupId = group,
            keyword = keyword,
            orderBy = orderBy
        )
        return ApiResponse.success(result)
    }
    
    /**
     * 根据 slug 获取文章详情
     */
    @Operation(summary = "获取文章详情")
    @GetMapping("/{slug}")
    fun getArticleBySlug(
        @Parameter(description = "文章 slug") @PathVariable slug: String
    ): ApiResponse<ArticleDetailResponse> {
        val article = articleService.getArticleBySlug(slug, incrementView = true)
        return ApiResponse.success(article)
    }
}
