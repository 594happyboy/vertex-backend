package com.zzy.blog.service

import com.zzy.blog.mapper.DocumentMapper
import com.zzy.file.service.FileService
import com.zzy.search.service.DocumentIndexData
import com.zzy.search.service.DocumentSearchIndexService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 搜索索引重建服务
 */
@Service
class SearchIndexRebuildService(
    private val documentMapper: DocumentMapper,
    private val fileService: FileService,
    private val searchIndexService: DocumentSearchIndexService
) {
    
    private val logger = LoggerFactory.getLogger(SearchIndexRebuildService::class.java)
    
    /**
     * 全量重建搜索索引
     */
    fun rebuildAll(): RebuildResult {
        logger.info("开始全量重建搜索索引...")
        
        try {
            // 1. 查询所有未删除的文档
            val documents = documentMapper.selectList(
                com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.zzy.blog.entity.Document>()
                    .eq("deleted", false)
            )
            
            logger.info("找到 {} 篇文档待索引", documents.size)
            
            // 2. 读取每个文档的内容并构建索引数据
            val indexDataList = mutableListOf<DocumentIndexData>()
            var successCount = 0
            var failedCount = 0
            
            for (document in documents) {
                try {
                    // 只处理 md 和 txt 类型的文档
                    val content = if (document.type in listOf("md", "txt") && document.fileId != null) {
                        try {
                            // 通过 FileService 下载文件并读取内容
                            val (inputStream, _) = fileService.downloadFile(document.fileId!!)
                            inputStream.bufferedReader().use { it.readText() }
                        } catch (e: Exception) {
                            logger.warn("读取文档内容失败: docId={}, fileId={}", document.id, document.fileId, e)
                            document.title
                        }
                    } else {
                        document.title
                    }
                    
                    indexDataList.add(
                        DocumentIndexData(
                            docId = document.id!!,
                            userId = document.userId,
                            groupId = document.groupId,
                            title = document.title,
                            content = content,
                            createdAt = document.createdAt,
                            updatedAt = document.updatedAt
                        )
                    )
                    successCount++
                } catch (e: Exception) {
                    logger.error("处理文档失败: docId={}", document.id, e)
                    failedCount++
                }
            }
            
            // 3. 批量重建索引
            searchIndexService.rebuildAll(indexDataList)
            
            logger.info("搜索索引重建完成: 成功={}, 失败={}", successCount, failedCount)
            
            return RebuildResult(
                success = true,
                totalDocuments = documents.size,
                successCount = successCount,
                failedCount = failedCount,
                message = "索引重建成功"
            )
        } catch (e: Exception) {
            logger.error("重建索引失败", e)
            return RebuildResult(
                success = false,
                totalDocuments = 0,
                successCount = 0,
                failedCount = 0,
                message = "索引重建失败: ${e.message}"
            )
        }
    }
}

/**
 * 重建结果
 */
data class RebuildResult(
    val success: Boolean,
    val totalDocuments: Int,
    val successCount: Int,
    val failedCount: Int,
    val message: String
)
