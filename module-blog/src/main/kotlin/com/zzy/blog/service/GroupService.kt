package com.zzy.blog.service

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.zzy.blog.context.AuthContextHolder
import com.zzy.blog.dto.CreateGroupRequest
import com.zzy.blog.dto.GroupResponse
import com.zzy.blog.dto.UpdateGroupRequest
import com.zzy.blog.entity.Group
import com.zzy.blog.exception.ForbiddenException
import com.zzy.blog.exception.ResourceNotFoundException
import com.zzy.blog.mapper.GroupMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 分组服务
 * @author ZZY
 * @date 2025-10-18
 */
@Service
class GroupService(
    private val groupMapper: GroupMapper,
    private val directoryTreeService: DirectoryTreeService
) {
    
    private val logger = LoggerFactory.getLogger(GroupService::class.java)
    
    /**
     * 获取分组树
     */
    fun getGroupTree(rootOnly: Boolean = false): List<GroupResponse> {
        val userId = getCurrentUserId()
        
        // 查询所有分组
        val allGroups = groupMapper.selectList(
            QueryWrapper<Group>()
                .eq("user_id", userId)
                .orderByAsc("sort_index")
        )
        
        if (rootOnly) {
            // 只返回根节点
            return allGroups
                .filter { it.parentId == null }
                .map { GroupResponse.fromEntity(it) }
        }
        
        // 构建树形结构
        return buildTree(allGroups)
    }
    
    /**
     * 创建分组
     */
    @Transactional
    fun createGroup(request: CreateGroupRequest): GroupResponse {
        val userId = getCurrentUserId()
        
        // 验证父分组是否存在且属于当前用户
        if (request.parentId != null) {
            val parentGroup = groupMapper.selectById(request.parentId)
            if (parentGroup == null || parentGroup.userId != userId) {
                throw ResourceNotFoundException("父分组不存在")
            }
        }
        
        // 创建分组
        val group = Group(
            userId = userId,
            name = request.name,
            parentId = request.parentId,
            sortIndex = 0
        )
        
        groupMapper.insert(group)
        logger.info("创建分组: id={}, name={}", group.id, group.name)
        
        // 清除缓存
        directoryTreeService.clearCache(userId)
        
        return GroupResponse.fromEntity(group)
    }
    
    /**
     * 更新分组
     */
    @Transactional
    fun updateGroup(id: Long, request: UpdateGroupRequest): GroupResponse {
        val userId = getCurrentUserId()
        
        // 查询分组
        val group = groupMapper.selectById(id)
            ?: throw ResourceNotFoundException("分组不存在")
        
        // 检查归属
        if (group.userId != userId) {
            throw ForbiddenException("无权操作此分组")
        }
        
        // 更新字段
        request.name?.let { group.name = it }
        request.sortIndex?.let { group.sortIndex = it }
        
        // 验证并更新父分组
        if (request.parentId != null) {
            // 不能将分组移动到自己或自己的子分组下
            if (request.parentId == id || isDescendant(request.parentId, id)) {
                throw IllegalArgumentException("不能将分组移动到自己或子分组下")
            }
            
            val parentGroup = groupMapper.selectById(request.parentId)
            if (parentGroup == null || parentGroup.userId != userId) {
                throw ResourceNotFoundException("父分组不存在")
            }
            group.parentId = request.parentId
        }
        
        groupMapper.updateById(group)
        logger.info("更新分组: id={}", id)
        
        // 清除缓存
        directoryTreeService.clearCache(userId)
        
        return GroupResponse.fromEntity(group)
    }
    
    /**
     * 删除分组（软删除）
     */
    @Transactional
    fun deleteGroup(id: Long) {
        val userId = getCurrentUserId()
        
        // 查询分组
        val group = groupMapper.selectById(id)
            ?: throw ResourceNotFoundException("分组不存在")
        
        // 检查归属
        if (group.userId != userId) {
            throw ForbiddenException("无权操作此分组")
        }
        
        // 检查是否有子分组
        val childCount = groupMapper.selectCount(
            QueryWrapper<Group>()
                .eq("parent_id", id)
                .eq("user_id", userId)
        )
        
        if (childCount > 0) {
            throw IllegalArgumentException("该分组下还有子分组，请先删除子分组")
        }
        
        // 软删除
        groupMapper.deleteById(id)
        logger.info("删除分组: id={}", id)
        
        // 清除缓存
        directoryTreeService.clearCache(userId)
    }
    
    /**
     * 构建树形结构
     */
    private fun buildTree(groups: List<Group>): List<GroupResponse> {
        // 先将所有分组转换为 GroupResponse
        val responseMap = groups.associate { it.id!! to GroupResponse.fromEntity(it) }
        val rootGroups = mutableListOf<GroupResponse>()
        
        // 构建树形结构
        responseMap.values.forEach { groupResponse ->
            if (groupResponse.parentId == null) {
                // 根节点
                rootGroups.add(groupResponse)
            } else {
                // 子节点，添加到父节点的 children 中
                val parent = responseMap[groupResponse.parentId]
                if (parent != null) {
                    if (parent.children == null) {
                        parent.children = mutableListOf()
                    }
                    parent.children!!.add(groupResponse)
                }
            }
        }
        
        return rootGroups
    }
    
    /**
     * 检查目标分组是否是源分组的后代
     */
    private fun isDescendant(targetId: Long, sourceId: Long): Boolean {
        var current = groupMapper.selectById(targetId)
        while (current != null) {
            if (current.parentId == sourceId) {
                return true
            }
            current = current.parentId?.let { groupMapper.selectById(it) }
        }
        return false
    }
    
    /**
     * 获取当前用户ID
     */
    private fun getCurrentUserId(): Long {
        return AuthContextHolder.getCurrentUserId()
    }
}

