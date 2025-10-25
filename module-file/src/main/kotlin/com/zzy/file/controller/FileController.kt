package com.zzy.file.controller

import com.zzy.common.dto.ApiResponse
import com.zzy.file.dto.*
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
 * 文件管理Controller（重构版，支持文件夹）
 * @author ZZY
 * @date 2025-10-23
 */
@Tag(name = "文件管理", description = "文件上传、下载、删除、移动等API")
@RestController
@RequestMapping("/api/files")
class FileController(
    private val fileService: FileService
) {
    
    private val logger = LoggerFactory.getLogger(FileController::class.java)
    
    @Operation(summary = "上传文件", description = "上传文件到指定文件夹，支持秒传，最大100MB")
    @PostMapping("/upload")
    fun uploadFile(
        @Parameter(description = "用户ID", required = true)
        @RequestParam userId: Long,
        
        @Parameter(description = "上传的文件", required = true)
        @RequestParam("file") file: MultipartFile,
        
        @Parameter(description = "所属文件夹ID（null表示根目录）")
        @RequestParam(required = false) folderId: Long?,
        
        @Parameter(description = "文件描述")
        @RequestParam(required = false) description: String?
    ): ApiResponse<FileResponse> {
        logger.info("开始上传文件: userId={}, fileName={}, fileSize={}, folderId={}", 
            userId, file.originalFilename, file.size, folderId)
        
        val request = FileUploadRequest(
            folderId = folderId,
            description = description
        )
        
        val fileResponse = fileService.uploadFile(userId, file, request)
        
        return ApiResponse.success(fileResponse, "文件上传成功")
    }
    
    @Operation(summary = "获取文件列表", description = "分页查询文件列表，支持按文件夹筛选、搜索和排序")
    @GetMapping
    fun getFileList(
        @Parameter(description = "用户ID", required = true) 
        @RequestParam userId: Long,
        
        @Parameter(description = "文件夹ID（null表示根目录）") 
        @RequestParam(required = false) folderId: Long?,
        
        @Parameter(description = "页码，从1开始") 
        @RequestParam(defaultValue = "1") page: Int,
        
        @Parameter(description = "每页数量") 
        @RequestParam(defaultValue = "20") size: Int,
        
        @Parameter(description = "搜索关键词（文件名、描述）") 
        @RequestParam(required = false) keyword: String?,
        
        @Parameter(description = "排序字段") 
        @RequestParam(defaultValue = "uploadTime") sortBy: String,
        
        @Parameter(description = "排序方式：asc/desc") 
        @RequestParam(defaultValue = "desc") order: String
    ): ApiResponse<FileListResponse> {
        logger.debug("查询文件列表: userId={}, folderId={}, page={}, size={}, keyword={}", 
            userId, folderId, page, size, keyword)
        
        val fileList = fileService.getFileList(userId, folderId, page, size, keyword, sortBy, order)
        
        return ApiResponse.success(fileList, "查询成功")
    }
    
    @Operation(summary = "获取文件详情", description = "根据文件ID获取详细信息")
    @GetMapping("/{id}")
    fun getFileInfo(
        @Parameter(description = "文件ID", required = true) @PathVariable id: Long,
        @Parameter(description = "用户ID", required = true) @RequestParam userId: Long
    ): ApiResponse<FileResponse> {
        logger.debug("查询文件详情: fileId={}, userId={}", id, userId)
        
        val fileInfo = fileService.getFileInfo(id, userId)
        
        return ApiResponse.success(fileInfo, "查询成功")
    }
    
    @Operation(summary = "更新文件信息", description = "更新文件名称、描述等")
    @PutMapping("/{id}")
    fun updateFile(
        @Parameter(description = "文件ID", required = true) @PathVariable id: Long,
        @Parameter(description = "用户ID", required = true) @RequestParam userId: Long,
        @RequestBody request: UpdateFileRequest
    ): ApiResponse<FileResponse> {
        logger.info("更新文件信息: fileId={}, userId={}", id, userId)
        
        val fileInfo = fileService.updateFile(id, userId, request)
        
        return ApiResponse.success(fileInfo, "更新成功")
    }
    
    @Operation(summary = "下载文件", description = "根据文件ID下载文件（公开接口，无需认证）")
    @GetMapping("/{id}/download")
    fun downloadFile(
        @Parameter(description = "文件ID", required = true) @PathVariable id: Long,
        response: HttpServletResponse
    ) {
        logger.info("开始下载文件: fileId={}", id)
        
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
    
    @Operation(summary = "移动文件", description = "将文件移动到指定文件夹")
    @PutMapping("/{id}/move")
    fun moveFile(
        @Parameter(description = "文件ID", required = true) @PathVariable id: Long,
        @Parameter(description = "用户ID", required = true) @RequestParam userId: Long,
        @RequestBody request: MoveFileRequest
    ): ApiResponse<FileResponse> {
        logger.info("移动文件: fileId={}, userId={}, targetFolderId={}", 
            id, userId, request.targetFolderId)
        
        val updateRequest = UpdateFileRequest(folderId = request.targetFolderId)
        val fileInfo = fileService.updateFile(id, userId, updateRequest)
        
        return ApiResponse.success(fileInfo, "移动成功")
    }
    
    @Operation(summary = "批量移动文件", description = "批量将文件移动到指定文件夹")
    @PostMapping("/batch-move")
    fun batchMoveFiles(
        @Parameter(description = "用户ID", required = true) @RequestParam userId: Long,
        @RequestBody request: BatchMoveFilesRequest
    ): ApiResponse<Map<String, Any>> {
        logger.info("批量移动文件: userId={}, fileCount={}, targetFolderId={}", 
            userId, request.fileIds.size, request.targetFolderId)
        
        val count = fileService.batchMoveFiles(userId, request)
        
        val result = mapOf(
            "total" to request.fileIds.size,
            "success" to count,
            "failed" to (request.fileIds.size - count)
        )
        
        return ApiResponse.success(result, "批量移动完成")
    }
    
    @Operation(summary = "删除文件", description = "删除文件（软删除，移入回收站）")
    @DeleteMapping("/{id}")
    fun deleteFile(
        @Parameter(description = "文件ID", required = true) @PathVariable id: Long,
        @Parameter(description = "用户ID", required = true) @RequestParam userId: Long
    ): ApiResponse<Boolean> {
        logger.info("删除文件: fileId={}, userId={}", id, userId)
        
        val result = fileService.deleteFile(id, userId)
        
        return ApiResponse.success(result, "删除成功")
    }
    
    @Operation(summary = "批量删除文件", description = "批量删除文件（软删除）")
    @PostMapping("/batch-delete")
    fun batchDeleteFiles(
        @Parameter(description = "用户ID", required = true) @RequestParam userId: Long,
        @RequestBody request: BatchDeleteFilesRequest
    ): ApiResponse<Map<String, Any>> {
        logger.info("批量删除文件: userId={}, fileCount={}", userId, request.fileIds.size)
        
        val count = fileService.batchDeleteFiles(userId, request)
        
        val result = mapOf(
            "total" to request.fileIds.size,
            "success" to count,
            "failed" to (request.fileIds.size - count)
        )
        
        return ApiResponse.success(result, "批量删除完成")
    }
    
    @Operation(summary = "永久删除文件", description = "彻底删除文件，包括MinIO中的物理文件，不可恢复")
    @DeleteMapping("/{id}/permanent")
    fun permanentlyDeleteFile(
        @Parameter(description = "文件ID", required = true) @PathVariable id: Long,
        @Parameter(description = "用户ID", required = true) @RequestParam userId: Long
    ): ApiResponse<Boolean> {
        logger.info("永久删除文件: fileId={}, userId={}", id, userId)
        
        val result = fileService.permanentlyDeleteFile(id, userId)
        
        return ApiResponse.success(result, "永久删除成功")
    }
    
    @Operation(summary = "恢复文件", description = "从回收站恢复文件")
    @PostMapping("/{id}/restore")
    fun restoreFile(
        @Parameter(description = "文件ID", required = true) @PathVariable id: Long,
        @Parameter(description = "用户ID", required = true) @RequestParam userId: Long
    ): ApiResponse<Boolean> {
        logger.info("恢复文件: fileId={}, userId={}", id, userId)
        
        val result = fileService.restoreFile(id, userId)
        
        return ApiResponse.success(result, "恢复成功")
    }
    
    @Operation(summary = "获取回收站文件列表", description = "查询已删除但未彻底清理的文件")
    @GetMapping("/recycle-bin")
    fun getRecycleBinFiles(
        @Parameter(description = "用户ID", required = true) @RequestParam userId: Long,
        @Parameter(description = "页码，从1开始") @RequestParam(defaultValue = "1") page: Int,
        @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") size: Int
    ): ApiResponse<FileListResponse> {
        logger.debug("查询回收站: userId={}, page={}, size={}", userId, page, size)
        
        val fileList = fileService.getRecycleBinFiles(userId, page, size)
        
        return ApiResponse.success(fileList, "查询成功")
    }
    
    @Operation(summary = "获取文件统计信息", description = "获取用户的文件总数、总大小、类型分布等统计信息")
    @GetMapping("/statistics")
    fun getFileStatistics(
        @Parameter(description = "用户ID", required = true) @RequestParam userId: Long
    ): ApiResponse<FileStatisticsResponse> {
        logger.debug("获取文件统计信息: userId={}", userId)
        
        val statistics = fileService.getFileStatistics(userId)
        
        return ApiResponse.success(statistics, "查询成功")
    }
    
    @Operation(summary = "清除所有缓存", description = "清除Redis中的所有文件相关缓存（管理功能）")
    @PostMapping("/cache/clear")
    fun clearCache(): ApiResponse<String> {
        logger.info("清除所有文件缓存")
        
        fileService.clearAllCache()
        
        return ApiResponse.success("缓存已清除", "缓存清除成功")
    }
}
