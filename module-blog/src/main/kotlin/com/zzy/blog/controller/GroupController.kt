package com.zzy.blog.controller

import com.zzy.common.dto.ApiResponse
import com.zzy.blog.dto.request.GroupRequest
import com.zzy.blog.dto.response.GroupResponse
import com.zzy.blog.entity.Group
import com.zzy.blog.service.GroupService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*

/**
 * 分组接口控制器
 */
@Tag(name = "分组接口")
@RestController
@RequestMapping("/api/groups")
class GroupController(
    private val groupService: GroupService
) {
    
    /**
     * 获取所有分组列表（含文章数）
     */
    @Operation(summary = "获取分组列表")
    @GetMapping
    fun getAllGroups(): ApiResponse<List<GroupResponse>> {
        val groups = groupService.getAllGroupsWithCount()
        return ApiResponse.success(groups)
    }
}

/**
 * 分组管理接口控制器
 */
@Tag(name = "分组管理接口")
@RestController
@RequestMapping("/api/admin/groups")
class GroupAdminController(
    private val groupService: GroupService
) {
    
    /**
     * 获取所有分组列表
     */
    @Operation(summary = "获取所有分组")
    @GetMapping
    fun getAllGroups(): ApiResponse<List<Group>> {
        val groups = groupService.getAllGroups()
        return ApiResponse.success(groups)
    }
    
    /**
     * 创建分组
     */
    @Operation(summary = "创建分组")
    @PostMapping
    fun createGroup(@RequestBody request: GroupRequest): ApiResponse<Group> {
        val group = groupService.createGroup(request)
        return ApiResponse.success(group, "创建成功")
    }
    
    /**
     * 更新分组
     */
    @Operation(summary = "更新分组")
    @PutMapping("/{id}")
    fun updateGroup(
        @Parameter(description = "分组ID") @PathVariable id: Long,
        @RequestBody request: GroupRequest
    ): ApiResponse<Group> {
        val group = groupService.updateGroup(id, request)
        return ApiResponse.success(group, "更新成功")
    }
    
    /**
     * 删除分组
     */
    @Operation(summary = "删除分组")
    @DeleteMapping("/{id}")
    fun deleteGroup(
        @Parameter(description = "分组ID") @PathVariable id: Long
    ): ApiResponse<Unit> {
        groupService.deleteGroup(id)
        return ApiResponse.success(message = "删除成功")
    }
}
