package com.zzy.file.controller

import com.zzy.common.dto.ApiResponse
import com.zzy.file.dto.*
import com.zzy.file.service.FolderService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*

/**
 * 文件夹管理Controller
 * @author ZZY
 * @date 2025-10-23
 */
@Tag(name = "文件夹管理", description = "文件夹树形结构管理、CRUD、移动等API")
@RestController
@RequestMapping("/api/folders")
class FolderController(
    private val folderService: FolderService
) {
    
    private val logger = LoggerFactory.getLogger(FolderController::class.java)
    
    @Operation(summary = "获取文件夹树", description = "获取用户的完整文件夹树形结构")
    @GetMapping("/tree")
    fun getFolderTree(
        @Parameter(description = "用户ID", required = true) @RequestParam userId: Long,
        @Parameter(description = "是否包含统计信息（文件数、大小等）") @RequestParam(defaultValue = "true") includeStats: Boolean
    ): ApiResponse<FolderTreeResponse> {
        logger.debug("获取文件夹树: userId={}, includeStats={}", userId, includeStats)
        
        val tree = folderService.getFolderTree(userId, includeStats)
        
        return ApiResponse.success(tree, "查询成功")
    }
    
    @Operation(summary = "获取文件夹详情", description = "获取指定文件夹的详细信息和统计数据")
    @GetMapping("/{id}")
    fun getFolderInfo(
        @Parameter(description = "文件夹ID", required = true) @PathVariable id: Long,
        @Parameter(description = "用户ID", required = true) @RequestParam userId: Long
    ): ApiResponse<FolderResponse> {
        logger.debug("获取文件夹详情: folderId={}, userId={}", id, userId)
        
        val folder = folderService.getFolderInfo(id, userId)
        
        return ApiResponse.success(folder, "查询成功")
    }
    
    @Operation(summary = "创建文件夹", description = "在指定位置创建新文件夹")
    @PostMapping
    fun createFolder(
        @Parameter(description = "用户ID", required = true) @RequestParam userId: Long,
        @RequestBody request: CreateFolderRequest
    ): ApiResponse<FolderResponse> {
        logger.info("创建文件夹: userId={}, folderName={}, parentId={}", 
            userId, request.name, request.parentId)
        
        val folder = folderService.createFolder(userId, request)
        
        return ApiResponse.success(folder, "创建成功")
    }
    
    @Operation(summary = "更新文件夹", description = "更新文件夹信息（名称、颜色、描述、移动等）")
    @PutMapping("/{id}")
    fun updateFolder(
        @Parameter(description = "文件夹ID", required = true) @PathVariable id: Long,
        @Parameter(description = "用户ID", required = true) @RequestParam userId: Long,
        @RequestBody request: UpdateFolderRequest
    ): ApiResponse<FolderResponse> {
        logger.info("更新文件夹: folderId={}, userId={}", id, userId)
        
        val folder = folderService.updateFolder(id, userId, request)
        
        return ApiResponse.success(folder, "更新成功")
    }
    
    @Operation(summary = "删除文件夹", description = "删除文件夹（软删除），支持递归删除")
    @DeleteMapping("/{id}")
    fun deleteFolder(
        @Parameter(description = "文件夹ID", required = true) @PathVariable id: Long,
        @Parameter(description = "用户ID", required = true) @RequestParam userId: Long,
        @Parameter(description = "是否递归删除子文件夹和文件") @RequestParam(defaultValue = "false") recursive: Boolean
    ): ApiResponse<Boolean> {
        logger.info("删除文件夹: folderId={}, userId={}, recursive={}", id, userId, recursive)
        
        val result = folderService.deleteFolder(id, userId, recursive)
        
        return ApiResponse.success(result, "删除成功")
    }
    
    @Operation(summary = "获取文件夹路径", description = "获取文件夹的完整路径（面包屑导航）")
    @GetMapping("/{id}/path")
    fun getFolderPath(
        @Parameter(description = "文件夹ID", required = true) @PathVariable id: Long,
        @Parameter(description = "用户ID", required = true) @RequestParam userId: Long
    ): ApiResponse<FolderPathResponse> {
        logger.debug("获取文件夹路径: folderId={}, userId={}", id, userId)
        
        val path = folderService.getFolderPath(id, userId)
        
        return ApiResponse.success(path, "查询成功")
    }
    
    @Operation(summary = "批量排序文件夹", description = "批量更新文件夹的排序索引（用于拖拽排序）")
    @PostMapping("/batch-sort")
    fun batchSortFolders(
        @Parameter(description = "用户ID", required = true) @RequestParam userId: Long,
        @RequestBody request: BatchSortFoldersRequest
    ): ApiResponse<Boolean> {
        logger.info("批量排序文件夹: userId={}, count={}", userId, request.items.size)
        
        val result = folderService.batchSortFolders(userId, request)
        
        return ApiResponse.success(result, "排序成功")
    }
}

