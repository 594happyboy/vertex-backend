package com.zzy.blog.service

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.extension.plugins.pagination.Page
import com.zzy.blog.context.AuthContextHolder
import com.zzy.blog.dto.*
import com.zzy.blog.entity.Document
import com.zzy.blog.exception.ForbiddenException
import com.zzy.blog.exception.ResourceNotFoundException
import com.zzy.blog.mapper.DocumentMapper
import com.zzy.file.service.FileService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

/**
 * 文档服务
 * @author ZZY
 * @date 2025-10-18
 */
@Service
class DocumentService(
    private val documentMapper: DocumentMapper,
    private val directoryTreeService: DirectoryTreeService,
    private val fileService: FileService
) {
    
    private val logger = LoggerFactory.getLogger(DocumentService::class.java)
    
    companion object {
        // 支持的文档文件类型
        private val SUPPORTED_EXTENSIONS = setOf("md", "pdf", "txt")
    }
    
    /**
     * 查询文档列表
     */
    fun getDocuments(request: DocumentQueryRequest): DocumentListResponse {
        val authUser = AuthContextHolder.getAuthUser()
            ?: throw ForbiddenException("未登录")
        
        // 构建查询条件
        val wrapper = QueryWrapper<Document>()
        
        // 用户只能查询自己的文档
        wrapper.eq("user_id", authUser.userId)
        
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
        if (document.userId != authUser.userId) {
            throw ForbiddenException("无权访问此文档")
        }
        
        return DocumentDetail.fromEntity(document)
    }
    
    /**
     * 创建文档
     */
    @Transactional
    fun createDocument(request: CreateDocumentRequest, file: MultipartFile): DocumentDetail {
        val userId = getCurrentUserId()
        
        // 验证文件
        val originalFilename = file.originalFilename ?: throw IllegalArgumentException("文件名不能为空")
        val extension = originalFilename.substringAfterLast('.', "").lowercase()
        
        if (extension !in SUPPORTED_EXTENSIONS) {
            throw IllegalArgumentException("不支持的文件类型: $extension，仅支持: ${SUPPORTED_EXTENSIONS.joinToString()}")
        }
        
        // 1. 上传文件到文件管理系统
        val fileResponse = fileService.uploadFile(

            userId = userId,
            file = file,
            request = com.zzy.file.dto.FileUploadRequest()
        )
        
        // 2. 创建文档记录
        val document = Document(
            userId = userId,
            groupId = request.groupId,
            title = request.title,
            type = extension,
            fileId = fileResponse.id,
            filePath = fileResponse.downloadUrl,
            sortIndex = 0
        )
        
        documentMapper.insert(document)
        logger.info("创建文档: id={}, title={}, fileId={}", document.id, document.title, document.fileId)
        
        // 清除缓存
        directoryTreeService.clearCache(userId)
        
        return DocumentDetail.fromEntity(document)
    }
    
    /**
     * 更新文档元数据（不包括文件本身）
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
        request.sortIndex?.let { document.sortIndex = it }

        documentMapper.updateById(document)
        logger.info("更新文档: id={}", id)
        
        // 清除缓存
        directoryTreeService.clearCache(userId)
        
        return DocumentDetail.fromEntity(document)
    }
    
    /**
     * 更新文档文件
     */
    @Transactional
    fun updateDocumentFile(id: Long, file: MultipartFile): DocumentDetail {
        val userId = getCurrentUserId()
        
        // 查询文档
        val document = documentMapper.selectById(id)
            ?: throw ResourceNotFoundException("文档不存在")
        
        // 检查归属
        if (document.userId != userId) {
            throw ForbiddenException("无权操作此文档")
        }
        
        // 验证文件类型
        val originalFilename = file.originalFilename ?: throw IllegalArgumentException("文件名不能为空")
        val extension = originalFilename.substringAfterLast('.', "").lowercase()
        
        if (extension !in SUPPORTED_EXTENSIONS) {
            throw IllegalArgumentException("不支持的文件类型: $extension")
        }
        
        // 删除旧文件（如果存在）
        document.fileId?.let { oldFileId ->
            try {
                fileService.deleteFile(oldFileId, userId)
                logger.info("删除旧文件: fileId={}", oldFileId)
            } catch (e: Exception) {
                logger.warn("删除旧文件失败: fileId={}", oldFileId, e)
            }
        }
        
        // 上传新文件
        val fileResponse = fileService.uploadFile(
            userId = userId,
            file = file,
            request = com.zzy.file.dto.FileUploadRequest()
        )
        
        // 更新文档记录
        document.type = extension
        document.fileId = fileResponse.id
        document.filePath = fileResponse.downloadUrl
        
        documentMapper.updateById(document)
        logger.info("更新文档文件: id={}, newFileId={}", id, document.fileId)
        
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
        
        // 删除关联的文件
        document.fileId?.let { fileId ->
            try {
                fileService.deleteFile(fileId, userId)
                logger.info("删除关联文件: fileId={}", fileId)
            } catch (e: Exception) {
                logger.warn("删除关联文件失败: fileId={}", fileId, e)
            }
        }
        
        // 软删除文档记录
        documentMapper.deleteById(id)
        logger.info("删除文档: id={}", id)
        
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

