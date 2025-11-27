package com.zzy.blog.controller

import com.zzy.blog.dto.DirectoryTreeResponse
import com.zzy.blog.dto.TreeReorderRequest
import com.zzy.blog.service.DirectoryTreeService
import com.zzy.blog.service.TreeCommandService
import com.zzy.common.dto.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*

/**
 * 目录树控制器
 * 提供完整的目录树接口（分组 + 文档），带Redis缓存
 * @author ZZY
 * @date 2025-10-18
 */
@Tag(name = "目录树管理", description = "获取完整的目录树结构（分组+文档），包含Redis缓存")
@RestController
@RequestMapping("/api/tree")
class DirectoryTreeController(
    private val directoryTreeService: DirectoryTreeService,
    private val treeCommandService: TreeCommandService
) {
    
    /**
     * 获取完整的目录树
     * 包含所有分组和文档的树形结构，支持Redis缓存
     */
    @Operation(
        summary = "获取完整的目录树",
        description = """
            获取当前用户的完整目录树结构，包含：
            - 所有分组的树形结构
            - 每个分组下的文档
            - 未分组的文档
            
            特性：
            - 自动使用Redis缓存（30分钟）
            - 用户角色：查看自己的所有文档（包括草稿）
            - 游客角色：只能查看目标用户的已发布文档
            - 响应中包含 cached 字段，标识数据是否来自缓存
            
            节点类型：
            - nodeType=group：分组节点
            - nodeType=document：文档节点
        """
    )
    @GetMapping
    fun getDirectoryTree(): ApiResponse<DirectoryTreeResponse> {
        val response = directoryTreeService.getDirectoryTree()
        return ApiResponse.success(response)
    }

    /**
     * 重排目录树（拖拽移动 + 排序）
     */
    @Operation(summary = "重排目录树", description = "同一父节点下的节点重新排序，可同时移动文件夹和文档")
    @PostMapping("/reorder")
    fun reorder(@RequestBody request: TreeReorderRequest): ApiResponse<Nothing> {
        treeCommandService.reorder(request)
        return ApiResponse.success(message = "重排成功")
    }
}


