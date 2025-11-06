package com.zzy.blog.service

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.zzy.blog.constants.CacheConstants
import com.zzy.blog.constants.RedisKeyConstants
import com.zzy.blog.dto.DirectoryTreeNode
import com.zzy.blog.dto.DirectoryTreeResponse
import com.zzy.blog.entity.Document
import com.zzy.blog.entity.Group
import com.zzy.common.context.AuthContextHolder
import com.zzy.common.exception.ForbiddenException
import com.zzy.blog.mapper.DocumentMapper
import com.zzy.blog.mapper.GroupMapper
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/**
 * 目录树服务
 * 提供完整的目录树结构（分组 + 文档），并带有Redis缓存
 * @author ZZY
 * @date 2025-10-18
 */
@Service
class DirectoryTreeService(
    private val groupMapper: GroupMapper,
    private val documentMapper: DocumentMapper,
    private val stringRedisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(DirectoryTreeService::class.java)

    /**
     * 获取完整的目录树
     * 包含所有分组和文档，带缓存
     */
    fun getDirectoryTree(): DirectoryTreeResponse {
        val authUser = AuthContextHolder.getAuthUser()
            ?: throw ForbiddenException("未登录")

        // 获取当前用户ID
        val userId = authUser.userId

        // 构建缓存键
        val cacheKey = buildCacheKey(userId)

        // 尝试从缓存获取
        val cachedTree = getFromCache(cacheKey)
        if (cachedTree != null) {
            logger.debug("从缓存获取目录树: userId={}", userId)
            return DirectoryTreeResponse(tree = cachedTree, cached = true)
        }

        // 从数据库加载
        logger.debug("从数据库加载目录树: userId={}", userId)
        val tree = buildDirectoryTree(userId)

        // 保存到缓存
        saveToCache(cacheKey, tree)

        return DirectoryTreeResponse(tree = tree, cached = false)
    }

    /**
     * 从数据库构建目录树
     */
    private fun buildDirectoryTree(userId: Long): List<DirectoryTreeNode> {
        // 1. 查询所有分组（手动过滤已删除的记录）
        val allGroups = groupMapper.selectList(
            QueryWrapper<Group>()
                .eq("user_id", userId)
                .eq("deleted", false)
                .orderByAsc("sort_index")
        )

        // 2. 查询所有文档（手动过滤已删除的记录）
        val allDocuments = documentMapper.selectList(
            QueryWrapper<Document>()
                .eq("user_id", userId)
                .eq("deleted", false)
                .orderByAsc("sort_index")
                .orderByDesc("created_at")
        )

        // 3. 转换为节点
        val groupNodes = allGroups.associateBy(
            { it.id!! },
            { DirectoryTreeNode.fromGroup(it) }
        )

        val documentNodes = allDocuments.map { DirectoryTreeNode.fromDocument(it) }

        // 4. 构建树形结构
        return buildTree(groupNodes, documentNodes)
    }

    /**
     * 构建树形结构
     * @param groupNodes 所有分组节点（Map: id -> node）
     * @param documentNodes 所有文档节点（List）
     * @return 根节点列表
     */
    private fun buildTree(
        groupNodes: Map<Long, DirectoryTreeNode>,
        documentNodes: List<DirectoryTreeNode>
    ): List<DirectoryTreeNode> {
        val rootNodes = mutableListOf<DirectoryTreeNode>()

        // 1. 构建分组树形结构
        groupNodes.values.forEach { node ->
            if (node.parentId == null) {
                // 根分组
                rootNodes.add(node)
            } else {
                // 子分组，添加到父分组
                val parent = groupNodes[node.parentId]
                if (parent != null) {
                    if (parent.children == null) {
                        parent.children = mutableListOf()
                    }
                    parent.children!!.add(node)
                }
            }
        }

        // 2. 将文档添加到对应的分组节点
        documentNodes.forEach { docNode ->
            if (docNode.groupId != null) {
                // 有分组的文档，添加到分组下
                val parentGroup = groupNodes[docNode.groupId]
                if (parentGroup != null) {
                    if (parentGroup.children == null) {
                        parentGroup.children = mutableListOf()
                    }
                    parentGroup.children!!.add(docNode)
                }
            } else {
                // 没有分组的文档，添加到根节点
                rootNodes.add(docNode)
            }
        }

        // 3. 对所有节点的子节点按 sortIndex 排序
        sortChildren(rootNodes)
        groupNodes.values.forEach { sortChildren(it.children) }

        return rootNodes.sortedBy { it.sortIndex }
    }

    /**
     * 对子节点进行排序
     */
    private fun sortChildren(children: MutableList<DirectoryTreeNode>?) {
        children?.sortWith(compareBy({ it.nodeType.value }, { it.sortIndex }))
    }

    /**
     * 构建缓存键
     */
    private fun buildCacheKey(userId: Long): String {
        return "${RedisKeyConstants.Cache.DIRECTORY_TREE_PREFIX}$userId"
    }

    /**
     * 从缓存获取目录树
     * 使用JSON字符串存储，避免序列化/反序列化问题
     */
    private fun getFromCache(key: String): List<DirectoryTreeNode>? {
        return try {
            val json = stringRedisTemplate.opsForValue().get(key)
            if (json != null) {
                objectMapper.readValue<List<DirectoryTreeNode>>(json)
            } else {
                null
            }
        } catch (e: Exception) {
            logger.warn("从缓存获取目录树失败: {}", e.message)
            null
        }
    }
    
    /**
     * 保存到缓存
     * 使用JSON字符串存储，避免序列化/反序列化问题
     */
    private fun saveToCache(key: String, tree: List<DirectoryTreeNode>) {
        try {
            val json = objectMapper.writeValueAsString(tree)
            stringRedisTemplate.opsForValue().set(key, json, CacheConstants.DIRECTORY_TREE_TTL_MINUTES, TimeUnit.MINUTES)
            logger.debug("目录树已缓存: key={}", key)
        } catch (e: Exception) {
            logger.error("保存目录树到缓存失败: {}", e.message, e)
        }
    }

    /**
     * 清除指定用户的缓存
     */
    fun clearCache(userId: Long) {
        try {
            val cacheKey = buildCacheKey(userId)
            stringRedisTemplate.delete(cacheKey)
            logger.info("清除目录树缓存: userId={}", userId)
        } catch (e: Exception) {
            logger.error("清除缓存失败: {}", e.message, e)
        }
    }
}


