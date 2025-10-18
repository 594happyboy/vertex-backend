package com.zzy.file.controller

import com.zzy.common.dto.ApiResponse
import com.zzy.file.dto.FileListResponse
import com.zzy.file.dto.FileResponse
import com.zzy.file.service.FileService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 文件管理Controller
 * @author ZZY
 * @date 2025-10-09
 */
@Tag(name = "文件管理", description = "文件上传、下载、删除等API")
@RestController
@RequestMapping("/api/files")
class FileController(
    private val fileService: FileService
) {
    
    private val logger = LoggerFactory.getLogger(FileController::class.java)
    
    @Operation(summary = "上传文件", description = "支持单文件上传，最大100MB")
    @PostMapping("/upload")
    fun uploadFile(
        @Parameter(description = "上传的文件", required = true)
        @RequestParam("file") file: MultipartFile,
        
        @Parameter(description = "用户ID（可选）")
        @RequestParam("userId", required = false) userId: Long?
    ): ApiResponse<FileResponse> {
        logger.info("开始上传文件: {}, 大小: {}", file.originalFilename, file.size)
        
        val fileResponse = fileService.uploadFile(file, userId)
        
        return ApiResponse.success(fileResponse, "文件上传成功")
    }
    
    @Operation(summary = "获取文件列表", description = "分页查询文件列表，支持搜索和排序")
    @GetMapping
    fun getFileList(
        @Parameter(description = "页码，从1开始") @RequestParam(defaultValue = "1") page: Int,
        @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") size: Int,
        @Parameter(description = "搜索关键词") @RequestParam(required = false) keyword: String?,
        @Parameter(description = "排序字段") @RequestParam(defaultValue = "uploadTime") sortBy: String,
        @Parameter(description = "排序方式：asc/desc") @RequestParam(defaultValue = "desc") order: String
    ): ApiResponse<FileListResponse> {
        logger.debug("查询文件列表: page={}, size={}, keyword={}", page, size, keyword)
        
        val fileList = fileService.getFileList(page, size, keyword, sortBy, order)
        
        return ApiResponse.success(fileList, "查询成功")
    }
    
    @Operation(summary = "获取文件详情", description = "根据文件ID获取详细信息")
    @GetMapping("/{id}")
    fun getFileInfo(
        @Parameter(description = "文件ID", required = true) @PathVariable id: Long
    ): ApiResponse<FileResponse> {
        logger.debug("查询文件详情: id={}", id)
        
        val fileInfo = fileService.getFileInfo(id)
        
        return ApiResponse.success(fileInfo, "查询成功")
    }
    
    @Operation(summary = "下载文件", description = "根据文件ID下载文件")
    @GetMapping("/{id}/download")
    fun downloadFile(
        @Parameter(description = "文件ID", required = true) @PathVariable id: Long,
        response: HttpServletResponse
    ) {
        logger.info("开始下载文件: id={}", id)
        
        val (inputStream, fileMetadata) = fileService.downloadFile(id)
        
        // 设置Content-Type
        response.contentType = fileMetadata.fileType ?: "application/octet-stream"
        response.characterEncoding = "UTF-8"
        
        // 设置文件名（RFC 5987标准）
        val fileName = fileMetadata.fileName ?: "download"
        val encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
            .replace("+", "%20")
        
        response.setHeader(
            "Content-Disposition",
            "attachment; filename*=UTF-8''$encodedFileName"
        )
        
        // 写入文件流
        inputStream.use { input ->
            IOUtils.copy(input, response.outputStream)
            response.outputStream.flush()
        }
        
        logger.info("文件下载成功: {}", fileMetadata.fileName)
    }
    
    @Operation(summary = "删除文件", description = "根据文件ID删除文件（逻辑删除）")
    @DeleteMapping("/{id}")
    fun deleteFile(
        @Parameter(description = "文件ID", required = true) @PathVariable id: Long
    ): ApiResponse<Boolean> {
        logger.info("删除文件: id={}", id)
        
        val result = fileService.deleteFile(id)
        
        return ApiResponse.success(result, "删除成功")
    }
    
    @Operation(summary = "批量删除文件", description = "根据文件ID列表批量删除")
    @DeleteMapping("/batch")
    fun batchDeleteFiles(
        @Parameter(description = "文件ID列表", required = true)
        @RequestBody ids: List<Long>
    ): ApiResponse<Map<String, Any>> {
        logger.info("批量删除文件: ids={}", ids)
        
        val count = fileService.batchDeleteFiles(ids)
        
        val result = mapOf(
            "total" to ids.size,
            "success" to count,
            "failed" to (ids.size - count)
        )
        
        return ApiResponse.success(result, "批量删除完成")
    }
    
    @Operation(summary = "清除所有缓存", description = "清除Redis中的所有文件相关缓存（管理功能）")
    @PostMapping("/cache/clear")
    fun clearCache(): ApiResponse<String> {
        logger.info("清除所有文件缓存")
        
        fileService.clearAllCache()
        
        return ApiResponse.success("缓存已清除", "缓存清除成功")
    }
    
}

