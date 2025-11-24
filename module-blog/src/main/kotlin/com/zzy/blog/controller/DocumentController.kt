package com.zzy.blog.controller

import com.zzy.blog.dto.*
import com.zzy.blog.service.BatchUploadJobService
import com.zzy.blog.service.BatchUploadService
import com.zzy.blog.service.DocumentService
import com.zzy.common.dto.ApiResponse
import com.zzy.common.exception.ResourceNotFoundException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

/** 文档控制器 提供文档的创建、查询、更新、删除及文件上传/替换等能力 */
@Tag(name = "文档管理", description = "文档的增删改查、文件替换、下载、批量上传")
@RestController
@RequestMapping("/api/documents")
class DocumentController(
        private val documentService: DocumentService,
        private val batchUploadService: BatchUploadService,
        private val batchUploadJobService: BatchUploadJobService
) {

    /** 查询文档列表（游标分页） */
    @Operation(summary = "查询文档列表（游标分页）", description = "游标分页查询，支持关键字/分组/排序")
    @GetMapping("/query")
    fun getDocuments(
            @RequestParam(required = false) q: String?,
            @RequestParam(required = false) groupId: Long?,
            @RequestParam(required = false) cursor: String?,
            @RequestParam(defaultValue = "20") limit: Int,
            @RequestParam(defaultValue = "default") sortBy: String,
            @RequestParam(defaultValue = "asc") order: String
    ): ApiResponse<com.zzy.common.pagination.PaginatedResponse<com.zzy.blog.dto.DocumentItem>> {
        val request =
                DocumentQueryRequest(
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

    /** 获取文档详情 */
    @Operation(summary = "获取文档详情", description = "根据文档ID返回详情")
    @GetMapping("/details/{id}")
    fun getDocument(@PathVariable id: Long): ApiResponse<DocumentDetail> {
        val document = documentService.getDocument(id)
        return ApiResponse.success(document)
    }

    /** 创建文档 */
    @Operation(summary = "创建文档", description = "通过 multipart/form-data 上传文件并创建文档；支持 md/pdf/txt 等")
    @PostMapping("/create", consumes = ["multipart/form-data"])
    fun createDocument(
            @Parameter(description = "文档标题，必填", required = true)
            @RequestParam("title")
            title: String,
            @Parameter(description = "分组ID，可选", required = false)
            @RequestParam("groupId", required = false)
            groupId: Long?,
            @Parameter(description = "文档文件，必填", required = true)
            @RequestPart("file")
            file: MultipartFile
    ): ApiResponse<DocumentDetail> {
        val request = CreateDocumentRequest(title = title, groupId = groupId)
        val document = documentService.createDocument(request, file)
        return ApiResponse.success(document, "创建成功")
    }
    /** 上传单个文档（便捷接口） */
    @Operation(summary = "上传单个文档（便捷接口）", description = "仅上传一个文件；title 未提供时取文件名")
    @PostMapping("/upload", consumes = ["multipart/form-data"])
    fun uploadSingle(
            @Parameter(description = "文档文件，必填", required = true)
            @RequestPart("file")
            file: MultipartFile,
            @Parameter(description = "分组ID，可选", required = false)
            @RequestParam("groupId", required = false)
            groupId: Long?,
            @Parameter(description = "文档标题，可选；不传则使用文件名", required = false)
            @RequestParam("title", required = false)
            title: String?
    ): ApiResponse<DocumentDetail> {
        val docTitle =
                if (!title.isNullOrBlank()) title
                else (file.originalFilename?.substringBeforeLast('.') ?: "Untitled")
        val request = CreateDocumentRequest(title = docTitle, groupId = groupId)
        val document = documentService.createDocument(request, file)
        return ApiResponse.success(document, "上传成功")
    }

    /** 更新文档信息 */
    @Operation(summary = "更新文档信息", description = "更新文档元数据（标题/分组/排序）")
    @PatchMapping("/update/{id}")
    fun updateDocument(
            @PathVariable id: Long,
            @RequestBody request: UpdateDocumentRequest
    ): ApiResponse<DocumentDetail> {
        val document = documentService.updateDocument(id, request)
        return ApiResponse.success(document, "更新成功")
    }

    /** 替换文档文件 */
    @Operation(summary = "替换文档文件", description = "仅替换文档文件，校验文件类型")
    @PatchMapping("/replace-file/{id}", consumes = ["multipart/form-data"])
    fun updateDocumentFile(
            @PathVariable id: Long,
            @Parameter(description = "新文件，必填", required = true)
            @RequestPart("file")
            file: MultipartFile
    ): ApiResponse<DocumentDetail> {
        val document = documentService.updateDocumentFile(id, file)
        return ApiResponse.success(document, "文件更新成功")
    }

    /** 下载文档 */
    @Operation(summary = "下载文档", description = "返回后端存储的文件下载地址；前端使用该 URL 下载")
    @GetMapping("/download/{id}")
    fun downloadDocument(@PathVariable id: Long): ApiResponse<String> {
        val document = documentService.getDocument(id)

        if (document.filePath == null) {
            throw ResourceNotFoundException("文档没有关联文件")
        }

        // 返回文件路径，前端通过路径直接下载
        // 或者重定向到文件服务的下载接口
        return ApiResponse.success(data = document.filePath, message = "请使用返回的路径下载文件")
    }

    /** 删除文档 */
    @Operation(summary = "删除文档", description = "软删除文档，并尝试删除关联文件")
    @DeleteMapping("/remove/{id}")
    fun deleteDocument(@PathVariable id: Long): ApiResponse<Nothing> {
        documentService.deleteDocument(id)
        return ApiResponse.success(message = "删除成功")
    }

    /** 批量上传文档（ZIP） */
    @Operation(summary = "批量上传文档（ZIP）", description = "上传 ZIP；文件夹→分组、文件→文档；支持嵌套；同名合并；失败回滚")
    @PostMapping("/batch-upload", consumes = ["multipart/form-data"])
    fun batchUpload(
            @Parameter(description = "ZIP 压缩包，必填", required = true)
            @RequestPart("file")
            file: MultipartFile,
            @Parameter(description = "父分组ID，可选；null 表示根目录", required = false)
            @RequestPart("parentGroupId", required = false)
            parentGroupId: Long?
    ): ApiResponse<BatchUploadResponse> {
        val result = batchUploadService.batchUpload(file, parentGroupId)
        return ApiResponse.success(result, result.message)
    }

    /** 发起异步批量上传任务 */
    @Operation(summary = "异步批量上传文档", description = "后台执行批量上传，并可轮询任务进度")
    @PostMapping("/batch-upload/async", consumes = ["multipart/form-data"])
    fun batchUploadAsync(
            @Parameter(description = "ZIP 压缩包，必填", required = true)
            @RequestPart("file")
            file: MultipartFile,
            @Parameter(description = "父分组ID，可选；null 表示根目录", required = false)
            @RequestPart("parentGroupId", required = false)
            parentGroupId: Long?
    ): ApiResponse<BatchUploadJobCreatedResponse> {
        val response = batchUploadJobService.startAsyncUpload(file, parentGroupId)
        return ApiResponse.success(response, "任务已创建")
    }

    /** 查询异步批量上传进度 */
    @Operation(summary = "查询批量上传进度", description = "根据 jobId 返回进度信息")
    @GetMapping("/batch-upload/progress/{jobId}")
    fun getBatchUploadProgress(
            @Parameter(description = "任务ID", required = true)
            @PathVariable jobId: String
    ): ApiResponse<BatchUploadProgress> {
        val progress = batchUploadJobService.getProgress(jobId)
        return ApiResponse.success(progress)
    }
}
