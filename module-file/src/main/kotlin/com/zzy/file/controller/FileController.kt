package com.zzy.file.controller

import com.zzy.common.context.AuthContextHolder
import com.zzy.common.dto.ApiResponse
import com.zzy.file.dto.*
import com.zzy.common.pagination.PaginatedResponse
import com.zzy.file.dto.resource.FileResource
import com.zzy.file.service.FileService
import com.zzy.file.service.TrashService
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
 * 
 * 提供文件的上传、下载、删除、移动、批量操作等功能
 * 
 * @author ZZY
 * @date 2025-10-23
 */
@Tag(name = "文件管理", description = "文件的上传、下载、删除、移动、批量操作等API")
@RestController
@RequestMapping("/api/files")
class FileController(
    private val fileService: FileService,
    private val trashService: TrashService,
    private val fileReferenceService: com.zzy.file.service.FileReferenceService
) {
    
    private val logger = LoggerFactory.getLogger(FileController::class.java)
    
    @Operation(summary = "上传文件", description = "上传文件到指定文件夹，支持秒传和覆盖上传，最大100MB")
    @PostMapping("/upload", consumes = ["multipart/form-data"])
    fun uploadFile(
        @Parameter(description = "上传的文件", required = true)
        @RequestParam("file") file: MultipartFile,
        
        @Parameter(description = "所属文件夹公开ID（null表示根目录）")
        @RequestParam(required = false) folderId: String?,
        
        @Parameter(description = "文件描述")
        @RequestParam(required = false) description: String?,
        
        @Parameter(description = "要替换的文件公开ID（提供此参数则执行覆盖上传）")
        @RequestParam(required = false) replaceFileId: String?
    ): ApiResponse<FileResponse> {
        val userId = AuthContextHolder.getCurrentUserId()
        logger.info("开始上传文件: userId={}, fileName={}, fileSize={}, folderId={}, replaceFileId={}", 
            userId, file.originalFilename, file.size, folderId, replaceFileId)
        
        val request = FileUploadRequest(
            folderId = folderId,
            description = description,
            replaceFileId = replaceFileId
        )
        
        val fileResponse = fileService.uploadFile(userId, file, request)
        
        val message = if (replaceFileId != null) "文件覆盖成功" else "文件上传成功"
        return ApiResponse.success(fileResponse, message)
    }
    
    @Operation(summary = "上传附件", description = "上传附件文件，自动存储到系统/附件文件夹")
    @PostMapping("/upload/attachment", consumes = ["multipart/form-data"])
    fun uploadAttachment(
        @Parameter(description = "上传的文件", required = true)
        @RequestParam("file") file: MultipartFile,
        
        @Parameter(description = "文件描述")
        @RequestParam(required = false) description: String?
    ): ApiResponse<FileResponse> {
        val userId = AuthContextHolder.getCurrentUserId()
        logger.info("上传附件: userId={}, fileName={}", userId, file.originalFilename)
        
        val fileResponse = fileService.uploadToSystemFolder(
            userId = userId,
            file = file,
            folderType = com.zzy.file.service.SystemFolderManager.SystemFolderType.ATTACHMENTS,
            description = description
        )
        
        return ApiResponse.success(fileResponse, "附件上传成功")
    }
    
    @Operation(summary = "获取文件详情", description = "根据文件公开ID获取详细信息")
    @GetMapping("/{publicId}")
    fun getFileInfo(
        @Parameter(description = "文件公开ID", required = true) @PathVariable publicId: String
    ): ApiResponse<FileResponse> {
        val userId = AuthContextHolder.getCurrentUserId()
        logger.debug("查询文件详情: publicId={}, userId={}", publicId, userId)
        
        val fileInfo = fileService.getFileInfoByPublicId(publicId, userId)
        
        return ApiResponse.success(fileInfo, "查询成功")
    }
    
    @Operation(summary = "更新文件信息", description = "更新文件名称、描述等")
    @PutMapping("/{publicId}")
    fun updateFile(
        @Parameter(description = "文件公开ID", required = true) @PathVariable publicId: String,
        @RequestBody request: UpdateFileRequest
    ): ApiResponse<FileResponse> {
        val userId = AuthContextHolder.getCurrentUserId()
        logger.info("更新文件信息: publicId={}, userId={}", publicId, userId)
        
        val fileInfo = fileService.updateFileByPublicId(publicId, userId, request)
        
        return ApiResponse.success(fileInfo, "更新成功")
    }
    
    @Operation(summary = "下载文件", description = "根据文件公开ID下载文件（公开接口，无需认证）")
    @GetMapping("/download/{publicId}")
    fun downloadFile(
        @Parameter(description = "文件公开ID", required = true) @PathVariable publicId: String,
        response: HttpServletResponse
    ) {
        logger.info("开始下载文件: publicId={}", publicId)
        
        val (inputStream, fileMetadata) = fileService.downloadFileByPublicId(publicId)
        
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
    @PutMapping("/move/{publicId}")
    fun moveFile(
        @Parameter(description = "文件公开ID", required = true) @PathVariable publicId: String,
        @RequestBody request: MoveFileRequest
    ): ApiResponse<FileResponse> {
        val userId = AuthContextHolder.getCurrentUserId()
        logger.info("移动文件: publicId={}, userId={}, targetFolderId={}", 
            publicId, userId, request.targetFolderId)
        
        val updateRequest = UpdateFileRequest(folderId = request.targetFolderId)
        val fileInfo = fileService.updateFileByPublicId(publicId, userId, updateRequest)
        
        return ApiResponse.success(fileInfo, "移动成功")
    }
    
    @Operation(summary = "批量移动文件", description = "批量将文件移动到指定文件夹")
    @PostMapping("/batch-move")
    fun batchMoveFiles(
        @RequestBody request: BatchMoveFilesRequest
    ): ApiResponse<Map<String, Any>> {
        val userId = AuthContextHolder.getCurrentUserId()
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
    @DeleteMapping("/delete/{publicId}")
    fun deleteFile(
        @Parameter(description = "文件公开ID", required = true) @PathVariable publicId: String
    ): ApiResponse<Boolean> {
        val userId = AuthContextHolder.getCurrentUserId()
        logger.info("删除文件: publicId={}, userId={}", publicId, userId)
        
        val result = fileService.deleteFileByPublicId(publicId, userId)
        
        return ApiResponse.success(result, "删除成功")
    }
    
    @Operation(summary = "批量操作", description = "批量操作文件（移动、删除、恢复等）")
    @PostMapping("/batch")
    fun batchOperation(
        @RequestBody request: BatchOperationRequest
    ): ApiResponse<BatchOperationResponse> {
        val userId = AuthContextHolder.getCurrentUserId()
        logger.info("批量操作文件: userId={}, action={}, fileCount={}", 
            userId, request.action, request.fileIds.size)
        
        val result = fileService.batchOperation(userId, request)
        
        return ApiResponse.success(result, "批量操作完成")
    }
    
    @Operation(summary = "批量删除文件", description = "批量删除文件（软删除）")
    @PostMapping("/batch-delete")
    fun batchDeleteFiles(
        @RequestBody request: BatchDeleteFilesRequest
    ): ApiResponse<Map<String, Any>> {
        val userId = AuthContextHolder.getCurrentUserId()
        logger.info("批量删除文件: userId={}, fileCount={}", userId, request.fileIds.size)
        
        val count = fileService.batchDeleteFiles(userId, request)
        
        val result = mapOf(
            "total" to request.fileIds.size,
            "success" to count,
            "failed" to (request.fileIds.size - count)
        )
        
        return ApiResponse.success(result, "批量删除完成")
    }
    
    @Operation(summary = "恢复文件", description = "从回收站恢复文件")
    @PostMapping("/restore/{publicId}")
    fun restoreFile(
        @Parameter(description = "文件公开ID", required = true) @PathVariable publicId: String
    ): ApiResponse<Map<String, Boolean>> {
        val userId = AuthContextHolder.getCurrentUserId()
        logger.info("恢复文件: publicId={}, userId={}", publicId, userId)
        
        val result = trashService.restoreFileByPublicId(publicId, userId)
        
        return ApiResponse.success(mapOf("success" to result), "恢复成功")
    }
    
    @Operation(summary = "永久删除文件", description = "彻底删除文件，包括MinIO中的物理文件，不可恢复")
    @DeleteMapping("/permanent/{publicId}")
    fun permanentlyDeleteFile(
        @Parameter(description = "文件公开ID", required = true) @PathVariable publicId: String
    ): ApiResponse<Map<String, Boolean>> {
        val userId = AuthContextHolder.getCurrentUserId()
        logger.info("永久删除文件: publicId={}, userId={}", publicId, userId)
        
        val result = trashService.permanentlyDeleteFileByPublicId(publicId, userId)
        
        return ApiResponse.success(mapOf("success" to result), "永久删除成功")
    }
    
    @Operation(
        summary = "获取回收站文件列表", 
        description = "查询已删除但未彻底清理的文件，支持游标分页"
    )
    @GetMapping("/recycle-bin")
    fun getRecycleBinFiles(
        @Parameter(description = "分页游标，首次请求不传") @RequestParam(required = false) cursor: String?,
        @Parameter(description = "每页数量，范围1-200") @RequestParam(defaultValue = "50") limit: Int
    ): ApiResponse<PaginatedResponse<FileResource>> {
        val userId = AuthContextHolder.getCurrentUserId()
        logger.debug("查询回收站: userId={}, cursor={}, limit={}", userId, cursor, limit)
        
        val validLimit = limit.coerceIn(
            com.zzy.file.constants.FileConstants.Pagination.MIN_LIMIT,
            com.zzy.file.constants.FileConstants.Pagination.MAX_LIMIT
        )
        
        return ApiResponse.success(
            trashService.getRecycleBinWithCursor(userId, cursor, validLimit),
            "查询成功"
        )
    }
    
    @Operation(summary = "获取文件统计信息", description = "获取用户的文件总数、总大小、类型分布等统计信息")
    @GetMapping("/statistics")
    fun getFileStatistics(): ApiResponse<FileStatisticsResponse> {
        val userId = AuthContextHolder.getCurrentUserId()
        logger.debug("获取文件统计信息: userId={}", userId)
        
        val statistics = fileService.getFileStatistics(userId)
        
        return ApiResponse.success(statistics, "查询成功")
    }
    
    @Operation(
        summary = "清除所有缓存", 
        description = "清除Redis中的所有文件相关缓存，仅用于开发或测试环境"
    )
    @PostMapping("/cache/clear")
    fun clearCache(): ApiResponse<String> {
        logger.info("清除所有文件缓存")
        
        fileService.clearAllCache()
        
        return ApiResponse.success("缓存已清除", "缓存清除成功")
    }
    
    @Operation(
        summary = "查询孤儿文件列表",
        description = "查询系统文件夹下无任何引用的文件（超过指定天数）。仅查询系统文件夹，用户文件夹的文件不会显示。"
    )
    @GetMapping("/orphaned")
    fun getOrphanedFiles(
        @Parameter(description = "宽限期（天）", required = false) 
        @RequestParam(defaultValue = "7") gracePeriodDays: Int,
        @Parameter(description = "限制数量", required = false) 
        @RequestParam(defaultValue = "100") limit: Int
    ): ApiResponse<Map<String, Any>> {
        logger.info("查询孤儿文件: gracePeriodDays={}, limit={}", gracePeriodDays, limit)
        
        val orphanFileIds = fileReferenceService.getOrphanFiles(gracePeriodDays, limit)
        
        val result = mapOf(
            "fileIds" to orphanFileIds,
            "count" to orphanFileIds.size,
            "gracePeriodDays" to gracePeriodDays,
            "note" to "仅显示系统文件夹下的孤儿文件"
        )
        
        return ApiResponse.success(result, "查询成功")
    }
    
    @Operation(
        summary = "手动清理无引用文件",
        description = "手动触发清理无引用文件任务（管理员功能）。只清理系统文件夹及其子文件夹下的文件，用户文件夹的文件永久保留。"
    )
    @PostMapping("/cleanup/unreferenced")
    fun cleanupUnreferencedFiles(
        @Parameter(description = "宽限期（天）", required = false) 
        @RequestParam(defaultValue = "7") gracePeriodDays: Int
    ): ApiResponse<Map<String, Any>> {
        logger.info("手动触发清理无引用文件: gracePeriodDays={}", gracePeriodDays)
        
        val cleanedCount = fileReferenceService.cleanupUnreferencedFiles(gracePeriodDays)
        
        val result = mapOf(
            "cleanedCount" to cleanedCount,
            "gracePeriodDays" to gracePeriodDays,
            "message" to "清理完成（仅清理系统文件夹）"
        )
        
        return ApiResponse.success(result, "清理完成")
    }
    
    @Operation(
        summary = "查看文件引用详情",
        description = "查看指定文件的所有引用信息"
    )
    @GetMapping("/{publicId}/references")
    fun getFileReferences(
        @Parameter(description = "文件公开ID", required = true) @PathVariable publicId: String
    ): ApiResponse<Map<String, Any>> {
        logger.info("查询文件引用详情: publicId={}", publicId)
        
        // 先通过publicId获取内部ID
        val file = fileReferenceService.getFileByPublicId(publicId)
        val references = fileReferenceService.getFileReferences(file.id!!)
        
        val result = mapOf(
            "filePublicId" to publicId,
            "referenceCount" to references.size,
            "references" to references.map { ref ->
                mapOf(
                    "id" to ref.id,
                    "type" to ref.referenceType,
                    "referenceId" to ref.referenceId,
                    "referenceField" to ref.referenceField,
                    "createdAt" to ref.createdAt
                )
            }
        )
        
        return ApiResponse.success(result, "查询成功")
    }
}
