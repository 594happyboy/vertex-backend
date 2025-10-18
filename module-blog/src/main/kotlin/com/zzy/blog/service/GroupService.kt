package com.zzy.blog.service

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.zzy.common.exception.BusinessException
import com.zzy.blog.dto.request.GroupRequest
import com.zzy.blog.dto.response.GroupResponse
import com.zzy.blog.entity.Group
import com.zzy.blog.mapper.GroupMapper
import com.zzy.blog.util.SlugUtil
import org.springframework.stereotype.Service

/**
 * 分组服务
 */
@Service
class GroupService(
    private val groupMapper: GroupMapper
) {
    
    /**
     * 获取所有分组列表（含文章数）
     */
    fun getAllGroupsWithCount(): List<GroupResponse> {
        val groups = groupMapper.selectGroupsWithArticleCount()
        return groups.map { map ->
            GroupResponse(
                id = map["id"] as Long,
                name = map["name"] as String,
                slug = map["slug"] as String,
                description = map["description"] as? String,
                orderIndex = (map["order_index"] as? Number)?.toInt() ?: 0,
                articleCount = (map["article_count"] as? Number)?.toInt() ?: 0
            )
        }
    }
    
    /**
     * 获取所有分组列表（不含文章数）
     */
    fun getAllGroups(): List<Group> {
        return groupMapper.selectList(
            QueryWrapper<Group>().orderByAsc("order_index")
        )
    }
    
    /**
     * 根据 ID 获取分组
     */
    fun getGroupById(id: Long): Group? {
        return groupMapper.selectById(id)
    }
    
    /**
     * 创建分组
     */
    fun createGroup(request: GroupRequest): Group {
        // 生成 slug
        val slug = request.slug ?: SlugUtil.generateSlug(request.name)
        
        // 检查 slug 是否已存在
        val existing = groupMapper.selectOne(
            QueryWrapper<Group>().eq("slug", slug)
        )
        if (existing != null) {
            throw BusinessException(message = "Slug '$slug' 已存在，请使用其他名称")
        }
        
        val group = Group(
            name = request.name,
            slug = slug,
            description = request.description,
            orderIndex = request.orderIndex
        )
        
        groupMapper.insert(group)
        return group
    }
    
    /**
     * 更新分组
     */
    fun updateGroup(id: Long, request: GroupRequest): Group {
        val group = groupMapper.selectById(id)
            ?: throw BusinessException(message = "分组不存在")
        
        // 如果更新 slug，检查是否冲突
        val newSlug = request.slug ?: SlugUtil.generateSlug(request.name)
        if (newSlug != group.slug) {
            val existing = groupMapper.selectOne(
                QueryWrapper<Group>().eq("slug", newSlug).ne("id", id)
            )
            if (existing != null) {
                throw BusinessException(message = "Slug '$newSlug' 已存在")
            }
        }
        
        group.name = request.name
        group.slug = newSlug
        group.description = request.description
        group.orderIndex = request.orderIndex
        
        groupMapper.updateById(group)
        return group
    }
    
    /**
     * 删除分组
     */
    fun deleteGroup(id: Long) {
        val group = groupMapper.selectById(id)
            ?: throw BusinessException(message = "分组不存在")
        
        groupMapper.deleteById(id)
    }
}
