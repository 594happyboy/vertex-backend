package com.zzy.file.controller

import com.zzy.common.context.AuthContextHolder
import com.zzy.common.dto.ApiResponse
import com.zzy.file.dto.*
import com.zzy.common.pagination.PaginatedResponse
import com.zzy.file.dto.resource.BaseResource
import com.zzy.file.dto.resource.FolderResource
import com.zzy.file.service.FolderService
import com.zzy.file.service.FolderExplorerService
import com.zzy.file.service.common.ValidationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*

/**
 * 文件夹管理Controller
 * 
 * 提供文件夹的CRUD操作、树形结构管理、路径查询等功能
 * 
 * @author ZZY
 * @date 2025-10-23
 */
@Tag(name = "文件夹管理", description = "文件夹的增删改查、树形结构、路径管理等API")
@RestController
@RequestMapping("/api/folders")
class FolderController(
    private val folderService: FolderService,
    private val folderExplorerService: FolderExplorerService,
    private val validationService: ValidationService
) {
    
    private val logger = LoggerFactory.getLogger(FolderController::class.java)
    
    @Operation(summary = "获取根目录信息", description = "获取用户根目录的元信息及首批子文件夹列表，支持游标分页")
    @GetMapping("/root")
    fun getRootFolder(
        @Parameter(description = "返回的子文件夹数量，范围1-200") @RequestParam(defaultValue = "50") limit: Int
    ): ApiResponse<RootFolderResponse> {
        val userId = AuthContextHolder.getCurrentUserId()
        logger.debug("获取根目录: userId={}, limit={}", userId, limit)
        
        val validLimit = limit.coerceIn(
            com.zzy.file.constants.FileConstants.Pagination.MIN_LIMIT,
            com.zzy.file.constants.FileConstants.Pagination.MAX_LIMIT
        )
        
        val root = folderExplorerService.getRootFolder(userId, includeChildren = true)
        
        return ApiResponse.success(root, "查询成功")
    }
    
    @Operation(summary = "获取目录内容列表", description = "获取指定目录下的文件和子文件夹，文件夹和文件分别返回，文件夹始终优先展示，支持游标分页、排序和搜索")
    @GetMapping("/children/{id}")
    fun getFolderChildren(
        @Parameter(description = "文件夹ID，root表示根目录", required = true) @PathVariable id: String,
        @Parameter(description = "分页游标，首次请求不传") @RequestParam(required = false) cursor: String?,
        @Parameter(description = "每页数量，范围1-200") @RequestParam(defaultValue = "50") limit: Int,
        @Parameter(description = "搜索关键词") @RequestParam(required = false) keyword: String?,
        @Parameter(description = "排序字段：name, size, updatedAt") @RequestParam(defaultValue = "name") orderBy: String,
        @Parameter(description = "排序方向：asc, desc") @RequestParam(defaultValue = "asc") order: String
    ): ApiResponse<FolderChildrenResponse> {
        val userId = AuthContextHolder.getCurrentUserId()
        logger.debug("获取目录子项: folderId={}, userId={}", id, userId)
        
        // 验证排序参数
        validateSortParams(orderBy, order)
        
        val folderId = parseFolderId(id, userId)
        val request = buildFolderChildrenRequest(cursor, limit, keyword, orderBy, order)
        
        return ApiResponse.success(
            folderExplorerService.getFolderChildren(folderId, userId, request),
            "查询成功"
        )
    }
    
    /**
     * 解析文件夹ID（支持 root、公开ID）
     * 
     * 统一使用公开ID（publicId）格式，特殊值 "root" 表示根目录
     * 通过 ValidationService 统一处理权限验证和实体查询
     */
    private fun parseFolderId(id: String, userId: Long): Long? {
        return when {
            // root 表示根目录（parentId = null）
            id.lowercase() == "root" -> null
            // 其他情况统一作为公开ID处理，复用 ValidationService 的验证逻辑
            else -> validationService.validateAndGetFolderByPublicId(id, userId).id
        }
    }
    
    /**
     * 构建文件夹子项查询请求
     */
    private fun buildFolderChildrenRequest(
        cursor: String?,
        limit: Int,
        keyword: String?,
        orderBy: String,
        order: String
    ): FolderChildrenRequest {
        return FolderChildrenRequest(
            cursor = cursor,
            limit = limit.coerceIn(
                com.zzy.file.constants.FileConstants.Pagination.MIN_LIMIT,
                com.zzy.file.constants.FileConstants.Pagination.MAX_LIMIT
            ),
            keyword = keyword,
            orderBy = orderBy,
            order = order,
            type = "all"  // 固定为all，文件夹始终优先
        )
    }
    
    @Operation(summary = "获取子文件夹列表", description = "仅获取子文件夹列表（不含文件），支持游标分页，适用于树形导航的懒加载")
    @GetMapping("/subfolders/{id}")
    fun getSubFolders(
        @Parameter(description = "文件夹ID，root表示根目录", required = true) @PathVariable id: String,
        @Parameter(description = "分页游标，首次请求不传") @RequestParam(required = false) cursor: String?,
        @Parameter(description = "每页数量，范围1-200") @RequestParam(defaultValue = "100") limit: Int
    ): ApiResponse<PaginatedResponse<FolderResource>> {
        val userId = AuthContextHolder.getCurrentUserId()
        logger.debug("获取子文件夹: folderId={}, userId={}", id, userId)
        
        val validLimit = limit.coerceIn(
            com.zzy.file.constants.FileConstants.Pagination.MIN_LIMIT,
            com.zzy.file.constants.FileConstants.Pagination.MAX_LIMIT
        )
        
        return ApiResponse.success(
            folderExplorerService.getSubFolders(userId, parseFolderId(id, userId), cursor, validLimit),
            "查询成功"
        )
    }
    
    @Operation(summary = "搜索目录内容", description = "在指定目录范围内搜索文件和文件夹，文件夹和文件分别返回，文件夹始终优先展示")
    @GetMapping("/search/{id}")
    fun searchInFolder(
        @Parameter(description = "搜索范围的文件夹ID，root表示全局搜索", required = true) @PathVariable id: String,
        @Parameter(description = "搜索关键词，最多100个字符", required = true) @RequestParam keyword: String,
        @Parameter(description = "分页游标，首次请求不传") @RequestParam(required = false) cursor: String?,
        @Parameter(description = "每页数量，范围1-200") @RequestParam(defaultValue = "50") limit: Int,
        @Parameter(description = "排序字段：name, size, updatedAt") @RequestParam(defaultValue = "name") orderBy: String,
        @Parameter(description = "排序方向：asc, desc") @RequestParam(defaultValue = "asc") order: String
    ): ApiResponse<FolderChildrenResponse> {
        val userId = AuthContextHolder.getCurrentUserId()
        logger.info("搜索目录: folderId={}, userId={}, keyword={}", id, userId, keyword)
        
        // 验证关键词
        validateSearchKeyword(keyword)
        
        // 验证排序参数
        validateSortParams(orderBy, order)
        
        val request = buildFolderChildrenRequest(cursor, limit, keyword, orderBy, order)
        
        return ApiResponse.success(
            folderExplorerService.searchInFolder(parseFolderId(id, userId), userId, request),
            "搜索成功"
        )
    }
    
    @Operation(summary = "获取文件夹树", description = "获取用户的完整文件夹树形结构")
    @GetMapping("/tree")
    fun getFolderTree(
        @Parameter(description = "是否包含统计信息（文件数、大小等）") @RequestParam(defaultValue = "true") includeStats: Boolean
    ): ApiResponse<FolderTreeResponse> {
        val userId = AuthContextHolder.getCurrentUserId()
        logger.debug("获取文件夹树: userId={}, includeStats={}", userId, includeStats)
        
        val tree = folderService.getFolderTree(userId, includeStats)
        
        return ApiResponse.success(tree, "查询成功")
    }
    
    @Operation(
        summary = "获取文件夹详情", 
        description = "获取指定文件夹的详细信息和统计数据"
    )
    @GetMapping("/{id}")
    fun getFolderInfo(
        @Parameter(description = "文件夹公开ID", required = true) @PathVariable id: String
    ): ApiResponse<FolderDetail> {
        val userId = AuthContextHolder.getCurrentUserId()
        logger.debug("获取文件夹详情: publicId={}, userId={}", id, userId)
        val folder = folderService.getFolderInfoByPublicId(id, userId)
        return ApiResponse.success(folder, "查询成功")
    }
    
    @Operation(summary = "创建文件夹", description = "在指定位置创建新文件夹")
    @PostMapping
    fun createFolder(
        @RequestBody request: CreateFolderRequest
    ): ApiResponse<FolderDetail> {
        val userId = AuthContextHolder.getCurrentUserId()
        logger.info("创建文件夹: userId={}, folderName={}, parentId={}", 
            userId, request.name, request.parentId)
        
        val folder = folderService.createFolder(userId, request)
        
        return ApiResponse.success(folder, "创建成功")
    }
    
    @Operation(summary = "更新文件夹", description = "更新文件夹信息（名称、颜色、描述、移动等）")
    @PutMapping("/{id}")
    fun updateFolder(
        @Parameter(description = "文件夹公开ID", required = true) @PathVariable id: String,
        @RequestBody request: UpdateFolderRequest
    ): ApiResponse<FolderDetail> {
        val userId = AuthContextHolder.getCurrentUserId()
        logger.info("更新文件夹: publicId={}, userId={}", id, userId)
        val folder = folderService.updateFolderByPublicId(id, userId, request)
        return ApiResponse.success(folder, "更新成功")
    }
    
    @Operation(summary = "删除文件夹", description = "删除文件夹（软删除），支持递归删除")
    @DeleteMapping("/{id}")
    fun deleteFolder(
        @Parameter(description = "文件夹公开ID", required = true) @PathVariable id: String,
        @Parameter(description = "是否递归删除子文件夹和文件") @RequestParam(defaultValue = "false") recursive: Boolean
    ): ApiResponse<Boolean> {
        val userId = AuthContextHolder.getCurrentUserId()
        logger.info("删除文件夹: publicId={}, userId={}, recursive={}", id, userId, recursive)
        val result = folderService.deleteFolderByPublicId(id, userId, recursive)
        return ApiResponse.success(result, "删除成功")
    }
    
    @Operation(summary = "获取文件夹路径", description = "获取文件夹的完整路径（面包屑导航）")
    @GetMapping("/path/{id}")
    fun getFolderPath(
        @Parameter(description = "文件夹公开ID", required = true) @PathVariable id: String
    ): ApiResponse<FolderPathResponse> {
        val userId = AuthContextHolder.getCurrentUserId()
        logger.debug("获取文件夹路径: publicId={}, userId={}", id, userId)
        val path = folderService.getFolderPathByPublicId(id, userId)
        return ApiResponse.success(path, "查询成功")
    }
    
    @Operation(summary = "批量排序文件夹", description = "批量更新文件夹的排序索引（用于拖拽排序）")
    @PostMapping("/batch-sort")
    fun batchSortFolders(
        @RequestBody request: BatchSortFoldersRequest
    ): ApiResponse<Boolean> {
        val userId = AuthContextHolder.getCurrentUserId()
        logger.info("批量排序文件夹: userId={}, count={}", userId, request.items.size)
        
        val result = folderService.batchSortFolders(userId, request)
        
        return ApiResponse.success(result, "排序成功")
    }
    
    /**
     * 验证搜索关键词
     */
    private fun validateSearchKeyword(keyword: String) {
        if (keyword.isBlank()) {
            throw com.zzy.common.exception.BusinessException(400, "搜索关键词不能为空")
        }
        if (keyword.length > 100) {
            throw com.zzy.common.exception.BusinessException(400, "搜索关键词过长，最多100个字符")
        }
    }
    
    /**
     * 验证排序参数
     */
    private fun validateSortParams(orderBy: String, order: String) {
        val validOrderByFields = setOf("name", "size", "updatedAt")
        val validOrderDirections = setOf("asc", "desc")
        
        if (orderBy !in validOrderByFields) {
            throw com.zzy.common.exception.BusinessException(
                400, 
                "无效的排序字段: $orderBy，支持的字段: ${validOrderByFields.joinToString(", ")}"
            )
        }
        
        if (order.lowercase() !in validOrderDirections) {
            throw com.zzy.common.exception.BusinessException(
                400, 
                "无效的排序方向: $order，支持的方向: asc, desc"
            )
        }
    }
}

