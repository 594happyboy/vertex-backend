package com.zzy.search.config

import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

/**
 * Schema 初始化器
 * 在应用启动时创建 FTS5 表和元数据表
 */
@Component
class SchemaInitializer(
    private val searchConfig: SearchConfig,
    private val connectionManager: SqliteConnectionManager
) : CommandLineRunner {
    
    private val logger = LoggerFactory.getLogger(SchemaInitializer::class.java)
    
    override fun run(vararg args: String?) {
        if (!searchConfig.autoInitSchema) {
            logger.info("自动初始化 Schema 已禁用，跳过")
            return
        }
        
        logger.info("开始初始化搜索索引 Schema...")
        
        val conn = connectionManager.getConnection()
        conn.createStatement().use { stmt ->
            // 创建 FTS5 虚拟表
            stmt.execute("""
                CREATE VIRTUAL TABLE IF NOT EXISTS document_index USING fts5(
                    doc_id UNINDEXED,
                    user_id UNINDEXED,
                    group_id UNINDEXED,
                    title,
                    content,
                    created_at UNINDEXED,
                    updated_at UNINDEXED,
                    tokenize = 'unicode61 tokenchars ''.#_''',
                    prefix = '2 3 4'
                )
            """.trimIndent())
            
            // 创建元数据表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS meta (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )
            """.trimIndent())
            
            // 初始化元数据
            stmt.execute("""
                INSERT OR IGNORE INTO meta(key, value) VALUES
                    ('schema_version', '1'),
                    ('last_rebuild_time', '')
            """.trimIndent())
            
            logger.info("搜索索引 Schema 初始化完成")
        }
    }
}
