package com.zzy.search.service

import com.zzy.search.config.SearchConfig
import com.zzy.search.config.SqliteConnectionManager
import com.zzy.search.dto.SearchResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.sql.ResultSet
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 文档搜索索引服务接口
 */
interface DocumentSearchIndexService {
    /**
     * 索引文档
     */
    fun indexDocument(docId: Long, userId: Long, groupId: Long?, title: String, content: String, createdAt: LocalDateTime?, updatedAt: LocalDateTime?)
    
    /**
     * 删除文档索引
     */
    fun deleteByDocumentId(docId: Long)
    
    /**
     * 搜索文档
     */
    fun search(userId: Long, q: String, groupId: Long?, limit: Int, offset: Int): List<SearchResult>
    
    /**
     * 全量重建索引
     */
    fun rebuildAll(documents: List<DocumentIndexData>)
}

/**
 * 文档索引数据
 */
data class DocumentIndexData(
    val docId: Long,
    val userId: Long,
    val groupId: Long?,
    val title: String,
    val content: String,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?
)

/**
 * 文档搜索索引服务实现
 */
@Service
class DocumentSearchIndexServiceImpl(
    private val connectionManager: SqliteConnectionManager,
    private val searchConfig: SearchConfig
) : DocumentSearchIndexService {
    
    private val logger = LoggerFactory.getLogger(DocumentSearchIndexServiceImpl::class.java)
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    
    override fun indexDocument(
        docId: Long,
        userId: Long,
        groupId: Long?,
        title: String,
        content: String,
        createdAt: LocalDateTime?,
        updatedAt: LocalDateTime?
    ) {
        try {
            val conn = connectionManager.getConnection()
            val sql = """
                INSERT OR REPLACE INTO document_index(doc_id, user_id, group_id, title, content, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, docId)
                stmt.setLong(2, userId)
                stmt.setObject(3, groupId)
                stmt.setString(4, title)
                stmt.setString(5, content)
                stmt.setString(6, createdAt?.format(dateFormatter))
                stmt.setString(7, updatedAt?.format(dateFormatter))
                stmt.executeUpdate()
            }
            
            logger.debug("文档已索引: docId={}", docId)
        } catch (e: Exception) {
            logger.error("索引文档失败: docId={}", docId, e)
            throw e
        }
    }
    
    override fun deleteByDocumentId(docId: Long) {
        try {
            val conn = connectionManager.getConnection()
            val sql = "DELETE FROM document_index WHERE doc_id = ?"
            
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, docId)
                stmt.executeUpdate()
            }
            
            logger.debug("文档索引已删除: docId={}", docId)
        } catch (e: Exception) {
            logger.error("删除文档索引失败: docId={}", docId, e)
            throw e
        }
    }
    
    override fun search(userId: Long, q: String, groupId: Long?, limit: Int, offset: Int): List<SearchResult> {
        if (q.isBlank()) {
            return emptyList()
        }
        
        try {
            val ftsQuery = buildFtsQuery(q)
            logger.debug("FTS 查询: query='{}', userId={}, groupId={}", ftsQuery, userId, groupId)
            
            val conn = connectionManager.getConnection()
            val sql = """
                SELECT
                    doc_id,
                    user_id,
                    group_id,
                    title,
                    snippet(document_index, 4, '<em>', '</em>', '...', 20) AS snippet,
                    bm25(document_index, 10.0, 1.0) AS score,
                    created_at,
                    updated_at
                FROM document_index
                WHERE document_index MATCH ?
                    AND user_id = ?
                    AND (? IS NULL OR group_id = ?)
                ORDER BY score ASC
                LIMIT ? OFFSET ?
            """.trimIndent()
            
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, ftsQuery)
                stmt.setLong(2, userId)
                stmt.setObject(3, groupId)
                stmt.setObject(4, groupId)
                stmt.setInt(5, limit)
                stmt.setInt(6, offset)
                
                val rs = stmt.executeQuery()
                return buildResultList(rs)
            }
        } catch (e: Exception) {
            logger.error("搜索失败: q='{}', userId={}", q, userId, e)
            throw e
        }
    }
    
    override fun rebuildAll(documents: List<DocumentIndexData>) {
        try {
            logger.info("开始全量重建索引，文档数量: {}", documents.size)
            
            val conn = connectionManager.getConnection()
            
            // 清空现有索引
            conn.createStatement().use { stmt ->
                stmt.execute("DELETE FROM document_index")
            }
            
            // 批量插入
            val sql = """
                INSERT INTO document_index(doc_id, user_id, group_id, title, content, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            
            conn.prepareStatement(sql).use { stmt ->
                for (doc in documents) {
                    stmt.setLong(1, doc.docId)
                    stmt.setLong(2, doc.userId)
                    stmt.setObject(3, doc.groupId)
                    stmt.setString(4, doc.title)
                    stmt.setString(5, doc.content)
                    stmt.setString(6, doc.createdAt?.format(dateFormatter))
                    stmt.setString(7, doc.updatedAt?.format(dateFormatter))
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
            
            // 更新元数据
            conn.prepareStatement("UPDATE meta SET value = ? WHERE key = 'last_rebuild_time'").use { stmt ->
                stmt.setString(1, LocalDateTime.now().format(dateFormatter))
                stmt.executeUpdate()
            }
            
            logger.info("索引重建完成，共索引 {} 篇文档", documents.size)
        } catch (e: Exception) {
            logger.error("重建索引失败", e)
            throw e
        }
    }
    
    /**
     * 构建 FTS 查询字符串
     */
    private fun buildFtsQuery(q: String): String {
        val trimmed = q.trim().take(searchConfig.maxQueryLength)
        
        // 按空格拆分为 tokens
        val tokens = trimmed.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .map { sanitizeToken(it) }
            .filter { it.isNotBlank() }
        
        if (tokens.isEmpty()) {
            return ""
        }
        
        // 构造 AND 查询：token1* AND token2* AND token3*
        return tokens.joinToString(" AND ") { "$it*" }
    }
    
    /**
     * 清理 token，移除 FTS 特殊字符
     */
    private fun sanitizeToken(token: String): String {
        // 移除 FTS 特殊字符：" * : ' ( ) 等
        return token.replace(Regex("""["*:')(]"""), "")
            .replace(Regex("""\p{Punct}+"""), " ") // 连续标点替换为空格
            .trim()
    }
    
    /**
     * 构建搜索结果列表
     */
    private fun buildResultList(rs: ResultSet): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        
        while (rs.next()) {
            val createdAtStr = rs.getString("created_at")
            val updatedAtStr = rs.getString("updated_at")
            
            results.add(
                SearchResult(
                    docId = rs.getLong("doc_id"),
                    userId = rs.getLong("user_id"),
                    groupId = rs.getObject("group_id") as Long?,
                    title = rs.getString("title"),
                    snippet = rs.getString("snippet"),
                    score = rs.getDouble("score"),
                    createdAt = if (createdAtStr != null) LocalDateTime.parse(createdAtStr, dateFormatter) else null,
                    updatedAt = if (updatedAtStr != null) LocalDateTime.parse(updatedAtStr, dateFormatter) else null
                )
            )
        }
        
        return results
    }
}
