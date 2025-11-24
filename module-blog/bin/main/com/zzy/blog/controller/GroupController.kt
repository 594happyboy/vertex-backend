package com.zzy.blog.controller

import com.zzy.blog.dto.CreateGroupRequest
import com.zzy.blog.dto.GroupResponse
import com.zzy.blog.dto.UpdateGroupRequest
import com.zzy.blog.service.GroupService
import com.zzy.common.dto.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*

/**
 * 分组控制器
 * @author ZZY
 * @date 2025-10-18
 */
@Tag(name = "分组管理", description = "文档分组的增删改查、树形结构管理")
@RestController
@RequestMapping("/api/groups")
class GroupController(
    private val groupService: GroupService
) {
    
    /**
     * 获取分组树
     */
    @Operation(summary = "获取分组树", description = "获取当前用户的分组树形结构")
    @GetMapping("/tree")
    fun getGroupTree(@RequestParam(required = false) rootOnly: Boolean = false): ApiResponse<List<GroupResponse>> {
        val tree = groupService.getGroupTree(rootOnly)
        return ApiResponse.success(tree)
    }
    
    /**
     * 创建分组
     */
    @Operation(summary = "创建分组", description = "创建新的文档分组")
    @PostMapping("/create")
    fun createGroup(@RequestBody request: CreateGroupRequest): ApiResponse<GroupResponse> {
        val group = groupService.createGroup(request)
        return ApiResponse.success(group, "创建成功")
    }
    
    /**
     * 更新分组
     */
    @Operation(summary = "更新分组", description = "更新分组信息，包括名称、父分组、排序")
    @PatchMapping("/update/{id}")
    fun updateGroup(
        @PathVariable id: Long,
        @RequestBody request: UpdateGroupRequest
    ): ApiResponse<GroupResponse> {
        val group = groupService.updateGroup(id, request)
        return ApiResponse.success(group, "更新成功")
    }
    
    /**
     * 删除分组
     */
    @Operation(summary = "删除分组", description = "删除分组（软删除），如果有子分组则无法删除")
    @DeleteMapping("/remove/{id}")
    fun deleteGroup(@PathVariable id: Long): ApiResponse<Nothing> {
        groupService.deleteGroup(id)
        return ApiResponse.success(message = "删除成功")
    }
}

