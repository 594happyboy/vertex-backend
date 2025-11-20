package com.zzy.blog.controller

import com.zzy.blog.service.RebuildResult
import com.zzy.blog.service.SearchIndexRebuildService
import com.zzy.common.dto.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 搜索索引管理控制器
 * @author ZZY
 */
@Tag(name = "搜索索引管理")
@RestController
@RequestMapping("/admin/search-index")
class SearchIndexController(
    private val searchIndexRebuildService: SearchIndexRebuildService
) {
    
    /**
     * 重建搜索索引
     */
    @Operation(summary = "重建搜索索引", description = "全量重建所有文档的搜索索引（仅管理员）")
    @PostMapping("/rebuild")
    fun rebuildIndex(): ApiResponse<RebuildResult> {
        val result = searchIndexRebuildService.rebuildAll()
        return ApiResponse.success(result)
    }
}
