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
import com.zzy.file.service.FileReferenceService
import com.zzy.file.entity.ReferenceType
import com.zzy.common.pagination.CursorParams
import com.zzy.common.pagination.CursorUtil
import com.zzy.common.pagination.PaginatedResponse
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
    private val fileService: FileService,
    private val folderService: com.zzy.file.service.FolderService,
    private val fileReferenceService: FileReferenceService,
    private val asyncFileReferenceService: AsyncFileReferenceService
) {
    
    private val logger = LoggerFactory.getLogger(DocumentService::class.java)
    
    companion object {
        // 支持的文档文件类型
        private val SUPPORTED_EXTENSIONS = setOf("md", "pdf", "txt")
        private const val MAX_LIMIT = 100
    }
    
    /**
     * 查询文档列表（游标分页）
     */
    fun getDocuments(request: DocumentQueryRequest): PaginatedResponse<DocumentItem> {
        val authUser = AuthContextHolder.getAuthUser()
            ?: throw ForbiddenException("未登录")
        
        // 验证限制
        val limit = request.limit.coerceIn(1, MAX_LIMIT)
        
        // 解析游标
        var lastId: Long? = null
        var lastSortValue: String? = null
        
        if (request.cursor != null) {
            val cursorParams = CursorUtil.decodeCursor(request.cursor)
            if (cursorParams != null) {
                // 验证游标参数是否匹配当前请求
                if (!CursorUtil.validateCursorParams(
                        cursorParams,
                        request.sortBy,
                        request.order,
                        request.q
                    )
                ) {
                    throw IllegalArgumentException("游标已失效，请重新请求")
                }
                lastId = cursorParams.lastId
                lastSortValue = cursorParams.lastSortValue
            }
        }
        
        // 根据排序字段和方向调用不同的查询方法
        val documents = when (request.sortBy) {
            "title" -> {
                if (request.order == "asc") {
                    documentMapper.selectDocumentsByTitleAsc(
                        userId = authUser.userId,
                        groupId = request.groupId,
                        keyword = request.q,
                        lastId = lastId,
                        lastSortValue = lastSortValue,
                        limit = limit + 1
                    )
                } else {
                    documentMapper.selectDocumentsByTitleDesc(
                        userId = authUser.userId,
                        groupId = request.groupId,
                        keyword = request.q,
                        lastId = lastId,
                        lastSortValue = lastSortValue,
                        limit = limit + 1
                    )
                }
            }
            "createdAt" -> {
                if (request.order == "asc") {
                    documentMapper.selectDocumentsByCreatedAtAsc(
                        userId = authUser.userId,
                        groupId = request.groupId,
                        keyword = request.q,
                        lastId = lastId,
                        lastSortValue = lastSortValue,
                        limit = limit + 1
                    )
                } else {
                    documentMapper.selectDocumentsByCreatedAtDesc(
                        userId = authUser.userId,
                        groupId = request.groupId,
                        keyword = request.q,
                        lastId = lastId,
                        lastSortValue = lastSortValue,
                        limit = limit + 1
                    )
                }
            }
            "updatedAt" -> {
                if (request.order == "asc") {
                    documentMapper.selectDocumentsByUpdatedAtAsc(
                        userId = authUser.userId,
                        groupId = request.groupId,
                        keyword = request.q,
                        lastId = lastId,
                        lastSortValue = lastSortValue,
                        limit = limit + 1
                    )
                } else {
                    documentMapper.selectDocumentsByUpdatedAtDesc(
                        userId = authUser.userId,
                        groupId = request.groupId,
                        keyword = request.q,
                        lastId = lastId,
                        lastSortValue = lastSortValue,
                        limit = limit + 1
                    )
                }
            }
            else -> {
                // 默认排序
                documentMapper.selectDocumentsDefault(
                    userId = authUser.userId,
                    groupId = request.groupId,
                    keyword = request.q,
                    lastId = lastId,
                    limit = limit + 1
                )
            }
        }
        
        // 转换为DTO
        val items = documents.take(limit).map { doc ->
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
        
        // 判断是否还有更多
        val hasMore = documents.size > limit
        val nextCursor = if (hasMore && items.isNotEmpty()) {
            val lastItem = documents[limit - 1]
            encodeCursor(lastItem, request)
        } else {
            null
        }
        
        return PaginatedResponse.of(
            items = items,
            limit = limit,
            nextCursor = nextCursor,
            hasMore = hasMore
        )
    }
    
    /**
     * 编码游标
     */
    private fun encodeCursor(document: Document, request: DocumentQueryRequest): String {
        val sortValue = when (request.sortBy) {
            "title" -> document.title
            "createdAt" -> document.createdAt?.toString() ?: ""
            "updatedAt" -> document.updatedAt?.toString() ?: ""
            else -> document.sortIndex.toString()
        }
        
        return CursorUtil.encodeCursor(
            CursorParams(
                lastId = document.id!!,
                lastSortValue = sortValue,
                sortField = request.sortBy,
                sortOrder = request.order,
                keyword = request.q
            )
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
        
        // 1. 上传文件到系统/知识库文件夹（使用统一的系统文件夹管理）
        val fileResponse = fileService.uploadToSystemFolder(
            userId = userId,
            file = file,
            folderType = com.zzy.file.service.SystemFolderManager.SystemFolderType.KNOWLEDGE_BASE,
                description = "知识库文档：${request.title}"
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
        
        // 3. 添加文件引用
        fileReferenceService.addReference(
            fileId = fileResponse.id,
            referenceType = ReferenceType.DOCUMENT.value,
            referenceId = document.id!!
        )
        logger.debug("添加文档文件引用: documentId={}, fileId={}", document.id, fileResponse.id)
        
        // 4. 如果是Markdown文件，异步同步文档内容中的文件引用
        if (extension == "md") {
            try {
                val content = file.inputStream.bufferedReader().use { it.readText() }
                // 使用异步方法，不阻塞主流程
                asyncFileReferenceService.syncDocumentContentReferencesAsync(document.id!!, content)
                    .exceptionally { e ->
                        logger.warn("异步同步文档内容引用失败: documentId={}", document.id, e)
                        null
                    }
                logger.debug("已提交文档内容引用异步同步任务: documentId={}", document.id)
            } catch (e: Exception) {
                logger.warn("读取文档内容失败，跳过引用同步: documentId={}", document.id, e)
            }
        }
        
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
        
        // 删除旧文件引用（如果存在）
        document.fileId?.let { oldFileId ->
            fileReferenceService.removeReference(
                fileId = oldFileId,
                referenceType = ReferenceType.DOCUMENT.value,
                referenceId = id
            )
            logger.debug("移除旧文件引用: documentId={}, oldFileId={}", id, oldFileId)
            
            try {
                fileService.deleteFile(oldFileId, userId)
                logger.info("删除旧文件: fileId={}", oldFileId)
            } catch (e: Exception) {
                logger.warn("删除旧文件失败: fileId={}", oldFileId, e)
            }
        }
        
        // 上传新文件到系统/知识库文件夹（使用统一的系统文件夹管理）
        val fileResponse = fileService.uploadToSystemFolder(
            userId = userId,
            file = file,
            folderType = com.zzy.file.service.SystemFolderManager.SystemFolderType.KNOWLEDGE_BASE,
                description = "知识库文档：${document.title}"
        )
        
        // 更新文档记录
        document.type = extension
        document.fileId = fileResponse.id
        document.filePath = fileResponse.downloadUrl
        
        documentMapper.updateById(document)
        logger.info("更新文档文件: id={}, newFileId={}", id, document.fileId)
        
        // 添加新文件引用
        fileReferenceService.addReference(
            fileId = fileResponse.id,
            referenceType = ReferenceType.DOCUMENT.value,
            referenceId = id
        )
        logger.debug("添加新文件引用: documentId={}, newFileId={}", id, fileResponse.id)
        
        // 如果是Markdown文件，异步同步文档内容中的文件引用
        if (extension == "md") {
            try {
                val content = file.inputStream.bufferedReader().use { it.readText() }
                // 使用异步方法，不阻塞主流程
                asyncFileReferenceService.syncDocumentContentReferencesAsync(id, content)
                    .exceptionally { e ->
                        logger.warn("异步同步文档内容引用失败: documentId={}", id, e)
                        null
                    }
                logger.debug("已提交文档内容引用异步同步任务: documentId={}", id)
            } catch (e: Exception) {
                logger.warn("读取文档内容失败，跳过引用同步: documentId={}", id, e)
            }
        }
        
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
        
        // 移除文档的所有文件引用
        fileReferenceService.removeAllReferences(
            referenceType = ReferenceType.DOCUMENT.value,
            referenceId = id
        )
        logger.debug("移除文档所有引用: documentId={}", id)
        
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
        documentMapper.softDelete(id)
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

