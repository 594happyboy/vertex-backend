package com.zzy.search.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture

/**
 * 异步文档搜索索引服务
 * 用于在文档 CRUD 操作后异步更新索引，避免阻塞主线程
 */
@Service
class AsyncDocumentSearchIndexService(
    private val indexService: DocumentSearchIndexService
) {
    
    private val logger = LoggerFactory.getLogger(AsyncDocumentSearchIndexService::class.java)
    
    /**
     * 异步索引文档
     */
    @Async
    fun indexDocumentAsync(
        docId: Long,
        userId: Long,
        groupId: Long?,
        title: String,
        content: String,
        createdAt: LocalDateTime?,
        updatedAt: LocalDateTime?
    ): CompletableFuture<Unit> {
        return executeAsync {
            indexService.indexDocument(docId, userId, groupId, title, content, createdAt, updatedAt)
            logger.debug("异步索引成功: docId={}", docId)
        }
    }
    
    /**
     * 异步删除文档索引
     */
    @Async
    fun deleteByDocumentIdAsync(docId: Long): CompletableFuture<Unit> {
        return executeAsync {
            indexService.deleteByDocumentId(docId)
            logger.debug("异步删除索引成功: docId={}", docId)
        }
    }

    private fun executeAsync(block: () -> Unit): CompletableFuture<Unit> {
        return try {
            block()
            CompletableFuture.completedFuture(Unit)
        } catch (e: Exception) {
            logger.warn("异步索引任务执行失败", e)
            CompletableFuture.completedFuture(Unit)
        }
    }
}
