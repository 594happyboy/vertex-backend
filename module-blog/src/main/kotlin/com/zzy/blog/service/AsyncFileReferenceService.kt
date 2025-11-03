package com.zzy.blog.service

import com.zzy.file.service.FileReferenceService
import com.zzy.file.service.ReferenceChanges
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.CompletableFuture

/**
 * 异步文件引用处理服务
 * 用于处理耗时的文档内容文件引用同步操作
 * 
 * 由于文件引用计数有宽限期（7天），异步处理不会影响业务正确性，
 * 同时可以提升文档创建和更新的接口响应速度
 * 
 * @author ZZY
 * @date 2025-11-03
 */
@Service
class AsyncFileReferenceService(
    private val fileReferenceService: FileReferenceService
) {
    
    private val logger = LoggerFactory.getLogger(AsyncFileReferenceService::class.java)
    
    /**
     * 异步同步文档内容中的文件引用
     * 
     * 使用独立事务（REQUIRES_NEW），避免影响主流程的事务
     * 如果同步失败，不会导致文档创建/更新失败
     * 
     * @param documentId 文档ID
     * @param content 文档内容（Markdown格式）
     * @return CompletableFuture 包含引用变化统计
     */
    @Async("fileReferenceExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = [Exception::class])
    fun syncDocumentContentReferencesAsync(
        documentId: Long, 
        content: String?
    ): CompletableFuture<ReferenceChanges> {
        return CompletableFuture.supplyAsync {
            try {
                logger.debug("开始异步同步文档内容引用: documentId={}", documentId)
                
                val changes = fileReferenceService.syncDocumentContentReferences(documentId, content)
                
                logger.info("异步同步文档内容引用完成: documentId={}, 新增={}, 删除={}", 
                    documentId, changes.added, changes.removed)
                
                changes
            } catch (e: Exception) {
                logger.error("异步同步文档内容引用失败: documentId={}", documentId, e)
                throw e
            }
        }
    }
}

