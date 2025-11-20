package com.zzy.blog.service

import com.zzy.blog.dto.DocumentSearchRequest
import com.zzy.blog.dto.DocumentSearchResponse
import com.zzy.blog.dto.DocumentSearchResultItem
import com.zzy.common.context.AuthContextHolder
import com.zzy.common.exception.ForbiddenException
import com.zzy.search.service.DocumentSearchIndexService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.format.DateTimeFormatter

/**
 * 文档搜索服务
 * 封装搜索请求预处理、调用 FTS 服务、结果组装
 */
@Service
class DocumentSearchService(
    private val searchIndexService: DocumentSearchIndexService
) {
    
    private val logger = LoggerFactory.getLogger(DocumentSearchService::class.java)
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    
    /**
     * 搜索文档
     */
    fun searchDocuments(request: DocumentSearchRequest): DocumentSearchResponse {
        val authUser = AuthContextHolder.getAuthUser()
            ?: throw ForbiddenException("未登录")
        
        val userId = authUser.userId
        
        // 参数校验
        if (request.q.isBlank()) {
            return DocumentSearchResponse(
                items = emptyList(),
                page = request.page,
                size = request.size,
                total = 0
            )
        }
        
        val page = request.page.coerceIn(1, Int.MAX_VALUE)
        val size = request.size.coerceIn(1, 100)
        val offset = (page - 1) * size
        
        logger.debug("搜索文档: userId={}, q='{}', groupId={}, page={}, size={}", 
            userId, request.q, request.groupId, page, size)
        
        try {
            // 调用 FTS 服务搜索
            val results = searchIndexService.search(
                userId = userId,
                q = request.q,
                groupId = request.groupId,
                limit = size,
                offset = offset
            )
            
            // 转换为前端展示格式
            val items = results.map { result ->
                DocumentSearchResultItem(
                    id = result.docId,
                    title = result.title,
                    groupId = result.groupId,
                    snippet = result.snippet,
                    score = result.score,
                    createdAt = result.createdAt?.format(dateFormatter),
                    updatedAt = result.updatedAt?.format(dateFormatter)
                )
            }
            
            logger.debug("搜索完成: 返回 {} 条结果", items.size)
            
            return DocumentSearchResponse(
                items = items,
                page = page,
                size = size,
                total = null // v1 暂不返回总数
            )
        } catch (e: Exception) {
            logger.error("搜索文档失败", e)
            throw e
        }
    }
}
