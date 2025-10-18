package com.zzy.blog.service

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.extension.plugins.pagination.Page
import com.zzy.blog.context.AuthContextHolder
import com.zzy.blog.dto.*
import com.zzy.blog.entity.DocStatus
import com.zzy.blog.entity.Document
import com.zzy.blog.exception.ForbiddenException
import com.zzy.blog.exception.ResourceNotFoundException
import com.zzy.blog.mapper.DocumentMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 文档服务
 * @author ZZY
 * @date 2025-10-18
 */
@Service
class DocumentService(
    private val documentMapper: DocumentMapper,
    private val directoryTreeService: DirectoryTreeService
) {
    
    private val logger = LoggerFactory.getLogger(DocumentService::class.java)
    
    /**
     * 查询文档列表
     */
    fun getDocuments(request: DocumentQueryRequest): DocumentListResponse {
        val authUser = AuthContextHolder.getAuthUser()
            ?: throw ForbiddenException("未登录")
        
        // 构建查询条件
        val wrapper = QueryWrapper<Document>()
        
        // 根据角色设置查询条件
        when (authUser.role) {
            "USER" -> {
                // 用户只能查询自己的文档
                wrapper.eq("user_id", authUser.currentUserId)
                
                // 状态过滤
                request.status?.let { wrapper.eq("status", it) }
            }
            "VISITOR" -> {
                // 游客只能查询目标用户的已发布文档
                wrapper.eq("user_id", authUser.targetUserId)
                    .eq("status", DocStatus.PUBLISHED.value)
            }
        }
        
        // 分组过滤
        request.groupId?.let { wrapper.eq("group_id", it) }
        
        // 搜索关键词（标题）
        request.q?.let { 
            if (it.isNotBlank()) {
                wrapper.like("title", it)
            }
        }
        
        // 排序
        wrapper.orderByAsc("sort_index")
            .orderByDesc("created_at")
        
        // 分页查询
        val page = Page<Document>(request.page.toLong(), request.size.toLong())
        val result = documentMapper.selectPage(page, wrapper)
        
        // 转换为DTO
        val items = result.records.map { doc ->
            DocumentItem(
                id = doc.id!!,
                title = doc.title,
                type = doc.type,
                status = doc.status,
                groupId = doc.groupId,
                sortIndex = doc.sortIndex,
                createdAt = doc.createdAt,
                updatedAt = doc.updatedAt
            )
        }
        
        return DocumentListResponse(
            items = items,
            total = result.total
        )
    }
    
    /**
     * 获取文档详情
     */
    fun getDocument(id: Long): DocumentDetail {
        val authUser = AuthContextHolder.getAuthUser()
            ?: throw ForbiddenException("未登录")
        
        val document = documentMapper.selectById(id)
            ?: throw ResourceNotFoundException("文档不存在")
        
        // 权限检查
        when (authUser.role) {
            "USER" -> {
                if (document.userId != authUser.currentUserId) {
                    throw ForbiddenException("无权访问此文档")
                }
            }
            "VISITOR" -> {
                if (document.userId != authUser.targetUserId 
                    || document.status != DocStatus.PUBLISHED.value) {
                    throw ForbiddenException("无权访问此文档")
                }
            }
        }
        
        return DocumentDetail.fromEntity(document)
    }
    
    /**
     * 创建文档
     */
    @Transactional
    fun createDocument(request: CreateDocumentRequest): DocumentDetail {
        val userId = getCurrentUserId()
        
        // 验证类型
        if (request.type !in listOf("md", "pdf")) {
            throw IllegalArgumentException("无效的文档类型")
        }
        
        // 创建文档
        val document = Document(
            userId = userId,
            groupId = request.groupId,
            title = request.title,
            type = request.type,
            status = DocStatus.DRAFT.value,
            contentMd = request.contentMd,
            sortIndex = 0
        )
        
        documentMapper.insert(document)
        logger.info("创建文档: id={}, title={}", document.id, document.title)
        
        // 清除缓存
        directoryTreeService.clearCache(userId)
        
        return DocumentDetail.fromEntity(document)
    }
    
    /**
     * 更新文档
     */
    @Transactional
    fun updateDocument(id: Long, request: UpdateDocumentRequest): DocumentDetail {
        val userId = getCurrentUserId()
        
        // 查询文档
        val document = documentMapper.selectById(id)
            ?: throw ResourceNotFoundException("文档不存在")
        
        // 检查归属
        if (document.userId != userId) {
            throw ForbiddenException("无权操作此文档")
        }
        
        // 更新字段
        request.title?.let { document.title = it }
        request.groupId?.let { document.groupId = it }
        request.contentMd?.let { document.contentMd = it }
        request.status?.let {
            if (it !in listOf("draft", "published")) {
                throw IllegalArgumentException("无效的文档状态")
            }
            document.status = it
        }
        request.sortIndex?.let { document.sortIndex = it }
        
        documentMapper.updateById(document)
        logger.info("更新文档: id={}", id)
        
        // 清除缓存
        directoryTreeService.clearCache(userId)
        
        return DocumentDetail.fromEntity(document)
    }
    
    /**
     * 删除文档（软删除）
     */
    @Transactional
    fun deleteDocument(id: Long) {
        val userId = getCurrentUserId()
        
        // 查询文档
        val document = documentMapper.selectById(id)
            ?: throw ResourceNotFoundException("文档不存在")
        
        // 检查归属
        if (document.userId != userId) {
            throw ForbiddenException("无权操作此文档")
        }
        
        // 软删除
        documentMapper.deleteById(id)
        logger.info("删除文档: id={}", id)
        
        // 清除缓存
        directoryTreeService.clearCache(userId)
    }
    
    /**
     * 获取当前用户ID
     */
    private fun getCurrentUserId(): Long {
        // 游客不能操作文档
        if (AuthContextHolder.isVisitor()) {
            throw ForbiddenException("游客无权操作文档")
        }
        return AuthContextHolder.getCurrentUserId()
    }
}

