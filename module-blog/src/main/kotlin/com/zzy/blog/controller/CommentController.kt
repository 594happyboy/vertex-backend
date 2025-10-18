package com.zzy.blog.controller

import com.zzy.common.dto.ApiResponse
import com.zzy.blog.dto.request.CommentRequest
import com.zzy.blog.dto.response.CommentResponse
import com.zzy.blog.dto.response.PageResponse
import com.zzy.blog.service.CommentService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*

/**
 * 评论公共接口控制器
 */
@Tag(name = "评论公共接口")
@RestController
@RequestMapping("/api/articles")
class CommentController(
    private val commentService: CommentService
) {
    
    /**
     * 获取文章的评论列表
     */
    @Operation(summary = "获取文章评论列表")
    @GetMapping("/{articleId}/comments")
    fun getComments(
        @Parameter(description = "文章ID") @PathVariable articleId: Long,
        @Parameter(description = "页码") @RequestParam(defaultValue = "1") page: Int,
        @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") size: Int
    ): ApiResponse<PageResponse<CommentResponse>> {
        val result = commentService.getCommentsByArticle(articleId, page, size)
        return ApiResponse.success(result)
    }
    
    /**
     * 提交评论
     */
    @Operation(summary = "提交评论")
    @PostMapping("/{articleId}/comments")
    fun createComment(
        @Parameter(description = "文章ID") @PathVariable articleId: Long,
        @RequestBody request: CommentRequest
    ): ApiResponse<CommentResponse> {
        val comment = commentService.createComment(articleId, request)
        return ApiResponse.success(comment, "评论成功")
    }
}
