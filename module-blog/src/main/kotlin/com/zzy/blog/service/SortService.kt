package com.zzy.blog.service

import com.zzy.blog.dto.DocumentSortRequest
import com.zzy.blog.dto.GroupSortRequest
import com.zzy.common.context.AuthContextHolder
import com.zzy.common.exception.ForbiddenException
import com.zzy.common.exception.ResourceNotFoundException
import com.zzy.blog.mapper.DocumentMapper
import com.zzy.blog.mapper.GroupMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 排序服务
 * @author ZZY
 * @date 2025-10-18
 */
@Service
class SortService(
    private val groupMapper: GroupMapper,
    private val documentMapper: DocumentMapper,
    private val directoryTreeService: DirectoryTreeService
) {
    
    private val logger = LoggerFactory.getLogger(SortService::class.java)
    
    /**
     * 批量更新分组排序
     */
    @Transactional
    fun sortGroups(request: GroupSortRequest) {
        val userId = getCurrentUserId()
        
        // 验证所有分组都属于当前用户
        val groupIds = request.items.map { it.id }
        val groups = groupMapper.selectBatchIds(groupIds)
        
        if (groups.size != groupIds.size) {
            throw ResourceNotFoundException("部分分组不存在")
        }
        
        if (groups.any { it.userId != userId }) {
            throw ForbiddenException("无权操作部分分组")
        }
        
        // 批量更新
        request.items.forEach { item ->
            val group = groups.first { it.id == item.id }
            group.parentId = item.parentId
            group.sortIndex = item.sortIndex
            groupMapper.updateById(group)
        }
        
        logger.info("批量更新分组排序: userId={}, count={}", userId, request.items.size)
        
        // 清除缓存
        directoryTreeService.clearCache(userId)
    }
    
    /**
     * 批量更新文档排序
     */
    @Transactional
    fun sortDocuments(request: DocumentSortRequest) {
        val userId = getCurrentUserId()
        
        // 验证所有文档都属于当前用户
        val documentIds = request.items.map { it.id }
        val documents = documentMapper.selectBatchIds(documentIds)
        
        if (documents.size != documentIds.size) {
            throw ResourceNotFoundException("部分文档不存在")
        }
        
        if (documents.any { it.userId != userId }) {
            throw ForbiddenException("无权操作部分文档")
        }
        
        // 批量更新
        request.items.forEach { item ->
            val document = documents.first { it.id == item.id }
            document.groupId = item.groupId
            document.sortIndex = item.sortIndex
            documentMapper.updateById(document)
        }
        
        logger.info("批量更新文档排序: userId={}, count={}", userId, request.items.size)
        
        // 清除缓存
        directoryTreeService.clearCache(userId)
    }
    
    /**
     * 获取当前用户ID
     */
    private fun getCurrentUserId(): Long {
        return AuthContextHolder.getCurrentUserId()
    }
}

