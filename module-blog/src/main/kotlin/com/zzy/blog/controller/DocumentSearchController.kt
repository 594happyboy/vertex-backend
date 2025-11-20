package com.zzy.blog.controller

import com.zzy.blog.dto.DocumentSearchRequest
import com.zzy.blog.dto.DocumentSearchResponse
import com.zzy.blog.service.DocumentSearchService
import com.zzy.common.dto.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 文档搜索控制器
 * @author ZZY
 */
@Tag(name = "文档搜索")
@RestController
@RequestMapping("/api/documents")
class DocumentSearchController(
    private val documentSearchService: DocumentSearchService
) {
    
    /**
     * 搜索文档
     */
    @Operation(summary = "搜索文档", description = "全文搜索文档标题和正文")
    @GetMapping("/search")
    fun searchDocuments(
        @RequestParam q: String,
        @RequestParam(required = false) groupId: Long?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ApiResponse<DocumentSearchResponse> {
        val request = DocumentSearchRequest(
            q = q,
            groupId = groupId,
            page = page,
            size = size
        )
        val result = documentSearchService.searchDocuments(request)
        return ApiResponse.success(result)
    }
}
