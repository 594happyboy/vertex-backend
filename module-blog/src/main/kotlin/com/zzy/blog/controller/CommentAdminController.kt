package com.zzy.blog.controller

import com.zzy.common.dto.ApiResponse
import com.zzy.blog.dto.response.PageResponse
import com.zzy.blog.service.CommentService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*

/**
 * 评论管理接口控制器
 */
@Tag(name = "评论管理接口")
@RestController
@RequestMapping("/api/admin/comments")
class CommentAdminController(
    private val commentService: CommentService
) {
    
    /**
     * 获取所有评论列表
     */
    @Operation(summary = "获取所有评论")
    @GetMapping
    fun getAllComments(
        @Parameter(description = "页码") @RequestParam(defaultValue = "1") page: Int,
        @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") size: Int
    ): ApiResponse<PageResponse<Map<String, Any>>> {
        val result = commentService.getAllComments(page, size)
        return ApiResponse.success(result)
    }
    
    /**
     * 删除评论
     */
    @Operation(summary = "删除评论")
    @DeleteMapping("/{id}")
    fun deleteComment(
        @Parameter(description = "评论ID") @PathVariable id: Long
    ): ApiResponse<Unit> {
        commentService.deleteComment(id)
        return ApiResponse.success(message = "删除成功")
    }
}
