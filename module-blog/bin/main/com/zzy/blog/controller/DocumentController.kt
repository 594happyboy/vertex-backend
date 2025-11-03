package com.zzy.blog.controller

import com.zzy.blog.dto.*
import com.zzy.blog.exception.ResourceNotFoundException
import com.zzy.blog.service.BatchUploadService
import com.zzy.blog.service.DocumentService
import com.zzy.common.dto.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

/**
 * 文档控制器
 * @author ZZY
 * @date 2025-10-18
 */
@Tag(name = "文档管理", description = "文档的增删改查、发布状态管理")
@RestController
@RequestMapping("/api/documents")
class DocumentController(
    private val documentService: DocumentService,
    private val batchUploadService: BatchUploadService
) {
    
    /**
     * 查询文档列表（游标分页）
     */
    @Operation(summary = "查询文档列表", description = "游标分页查询文档，支持搜索、筛选、排序")
    @GetMapping
    fun getDocuments(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) groupId: Long?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(defaultValue = "default") sortBy: String,
        @RequestParam(defaultValue = "asc") order: String
    ): ApiResponse<com.zzy.common.pagination.PaginatedResponse<com.zzy.blog.dto.DocumentItem>> {
        val request = DocumentQueryRequest(
            q = q,
            groupId = groupId,
            cursor = cursor,
            limit = limit,
            sortBy = sortBy,
            order = order
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
    @Operation(summary = "创建文档", description = "上传文件创建新的文档（支持md/pdf/txt）")
    @PostMapping(consumes = ["multipart/form-data"])
    fun createDocument(
        @Parameter(description = "文档标题", required = true)
        @RequestParam("title") title: String,
        
        @Parameter(description = "分组ID（可选）")
        @RequestParam("groupId", required = false) groupId: Long?,
        
        @Parameter(description = "文档文件", required = true)
        @RequestPart("file") file: MultipartFile
    ): ApiResponse<DocumentDetail> {
        val request = CreateDocumentRequest(title = title, groupId = groupId)
        val document = documentService.createDocument(request, file)
        return ApiResponse.success(document, "创建成功")
    }
    
    /**
     * 更新文档
     */
    @Operation(summary = "更新文档", description = "更新文档信息，包括内容、分组等")
    @PatchMapping("/{id}")
    fun updateDocument(
        @PathVariable id: Long,
        @RequestBody request: UpdateDocumentRequest
    ): ApiResponse<DocumentDetail> {
        val document = documentService.updateDocument(id, request)
        return ApiResponse.success(document, "更新成功")
    }
    
    /**
     * 更新文档文件
     */
    @Operation(summary = "更新文档文件", description = "替换文档的文件内容")
    @PatchMapping("/{id}/file", consumes = ["multipart/form-data"])
    fun updateDocumentFile(
        @PathVariable id: Long,
        @Parameter(description = "新的文档文件", required = true)
        @RequestPart("file") file: MultipartFile
    ): ApiResponse<DocumentDetail> {
        val document = documentService.updateDocumentFile(id, file)
        return ApiResponse.success(document, "文件更新成功")
    }
    
    /**
     * 下载文档文件
     * 注意：建议前端直接使用 filePath 字段下载文件（更高效）
     * 此接口作为备用方案，会增加下载统计
     */
    @Operation(summary = "下载文档文件", description = "下载文档的源文件（建议直接使用filePath下载）")
    @GetMapping("/{id}/download")
    fun downloadDocument(@PathVariable id: Long): ApiResponse<String> {
        val document = documentService.getDocument(id)
        
        if (document.filePath == null) {
            throw ResourceNotFoundException("文档没有关联文件")
        }
        
        // 返回文件路径，前端通过路径直接下载
        // 或者重定向到文件服务的下载接口
        return ApiResponse.success(
            data = document.filePath,
            message = "请使用返回的路径下载文件"
        )
    }
    
    /**
     * 删除文档
     */
    @Operation(summary = "删除文档", description = "删除文档（软删除，同时删除关联文件）")
    @DeleteMapping("/{id}")
    fun deleteDocument(@PathVariable id: Long): ApiResponse<Nothing> {
        documentService.deleteDocument(id)
        return ApiResponse.success(message = "删除成功")
    }
    
    /**
     * 批量上传
     */
    @Operation(
        summary = "批量上传文档",
        description = """
            上传一个ZIP压缩包进行批量导入。
            - ZIP包内的文件夹会被创建为分组（Group）
            - 支持嵌套文件夹结构
            - 文件会被创建为文档（Document），支持 .md, .txt, .pdf 格式
            - parentGroupId 指定父分组ID，不传或传null表示在根目录上传
            - 同一父分组下的同名分组会自动合并
            - 如果处理失败会自动回滚所有更改
        """
    )
    @PostMapping("/batch-upload")
    fun batchUpload(
        @Parameter(description = "ZIP压缩包文件", required = true)
        @RequestParam("file") file: MultipartFile,
        
        @Parameter(description = "父分组ID，不传或传null表示在根目录上传")
        @RequestParam("parentGroupId", required = false) parentGroupId: Long?
    ): ApiResponse<BatchUploadResponse> {
        val result = batchUploadService.batchUpload(file, parentGroupId)
        return ApiResponse.success(result, result.message)
    }
}

