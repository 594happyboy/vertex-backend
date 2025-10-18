package com.zzy.blog.controller

import com.zzy.blog.dto.*
import com.zzy.blog.service.DocumentService
import com.zzy.common.dto.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*

/**
 * 文档控制器
 * @author ZZY
 * @date 2025-10-18
 */
@Tag(name = "文档管理", description = "文档的增删改查、发布状态管理")
@RestController
@RequestMapping("/api/documents")
class DocumentController(
    private val documentService: DocumentService
) {
    
    /**
     * 查询文档列表
     */
    @Operation(summary = "查询文档列表", description = "分页查询文档，支持搜索、筛选")
    @GetMapping
    fun getDocuments(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) groupId: Long?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ApiResponse<DocumentListResponse> {
        val request = DocumentQueryRequest(
            q = q,
            status = status,
            groupId = groupId,
            page = page,
            size = size
        )
        val result = documentService.getDocuments(request)
        return ApiResponse.success(result)
    }
    
    /**
     * 获取文档详情
     */
    @Operation(summary = "获取文档详情", description = "根据ID获取文档详细信息")
    @GetMapping("/{id}")
    fun getDocument(@PathVariable id: Long): ApiResponse<DocumentDetail> {
        val document = documentService.getDocument(id)
        return ApiResponse.success(document)
    }
    
    /**
     * 创建文档
     */
    @Operation(summary = "创建文档", description = "创建新的文档（Markdown或PDF）")
    @PostMapping
    fun createDocument(@RequestBody request: CreateDocumentRequest): ApiResponse<DocumentDetail> {
        val document = documentService.createDocument(request)
        return ApiResponse.success(document, "创建成功")
    }
    
    /**
     * 更新文档
     */
    @Operation(summary = "更新文档", description = "更新文档信息，包括内容、状态、分组等")
    @PatchMapping("/{id}")
    fun updateDocument(
        @PathVariable id: Long,
        @RequestBody request: UpdateDocumentRequest
    ): ApiResponse<DocumentDetail> {
        val document = documentService.updateDocument(id, request)
        return ApiResponse.success(document, "更新成功")
    }
    
    /**
     * 删除文档
     */
    @Operation(summary = "删除文档", description = "删除文档（软删除）")
    @DeleteMapping("/{id}")
    fun deleteDocument(@PathVariable id: Long): ApiResponse<Nothing> {
        documentService.deleteDocument(id)
        return ApiResponse.success(message = "删除成功")
    }
}

