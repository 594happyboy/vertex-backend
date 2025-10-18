package com.zzy.blog.controller

import com.zzy.blog.dto.DocumentSortRequest
import com.zzy.blog.dto.GroupSortRequest
import com.zzy.blog.service.SortService
import com.zzy.common.dto.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*

/**
 * 排序控制器
 * @author ZZY
 * @date 2025-10-18
 */
@Tag(name = "排序管理", description = "分组和文档的批量排序与移动")
@RestController
@RequestMapping("/api/sort")
class SortController(
    private val sortService: SortService
) {
    
    /**
     * 批量更新分组排序
     */
    @Operation(summary = "批量更新分组排序", description = "批量更新分组的父分组和排序索引")
    @PostMapping("/groups")
    fun sortGroups(@RequestBody request: GroupSortRequest): ApiResponse<Nothing> {
        sortService.sortGroups(request)
        return ApiResponse.success(message = "排序更新成功")
    }
    
    /**
     * 批量更新文档排序
     */
    @Operation(summary = "批量更新文档排序", description = "批量更新文档的分组和排序索引")
    @PostMapping("/documents")
    fun sortDocuments(@RequestBody request: DocumentSortRequest): ApiResponse<Nothing> {
        sortService.sortDocuments(request)
        return ApiResponse.success(message = "排序更新成功")
    }
}

