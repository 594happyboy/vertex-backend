package com.zzy.search.config

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * SQLite 连接管理器
 * 负责管理 SQLite 连接、执行 PRAGMA 优化、确保文件路径存在
 */
@Component
class SqliteConnectionManager(
    private val searchConfig: SearchConfig
) {
    
    private val logger = LoggerFactory.getLogger(SqliteConnectionManager::class.java)
    private var connection: Connection? = null
    
    @PostConstruct
    fun init() {
        logger.info("初始化 SQLite 连接: {}", searchConfig.dbPath)
        ensureDbFileExists()
        getConnection() // 触发连接初始化
    }
    
    @PreDestroy
    fun destroy() {
        connection?.let {
            if (!it.isClosed) {
                it.close()
                logger.info("SQLite 连接已关闭")
            }
        }
    }
    
    /**
     * 获取 SQLite 连接（单例）
     */
    @Synchronized
    fun getConnection(): Connection {
        if (connection == null || connection!!.isClosed) {
            val url = "jdbc:sqlite:${searchConfig.dbPath}"
            connection = DriverManager.getConnection(url)
            applyPragmas(connection!!)
            logger.info("SQLite 连接已建立: {}", url)
        }
        return connection!!
    }
    
    /**
     * 执行 PRAGMA 优化配置
     */
    private fun applyPragmas(conn: Connection) {
        conn.createStatement().use { stmt ->
            // WAL 模式：提升并发读性能
            stmt.execute("PRAGMA journal_mode = WAL;")
            // 同步模式：在可靠性与性能之间折中
            stmt.execute("PRAGMA synchronous = NORMAL;")
            // Page Cache：约 20MB
            stmt.execute("PRAGMA cache_size = -20000;")
            // 临时表优先使用内存
            stmt.execute("PRAGMA temp_store = MEMORY;")
            
            logger.debug("SQLite PRAGMA 配置已应用")
        }
    }
    
    /**
     * 确保 DB 文件所在目录存在
     */
    private fun ensureDbFileExists() {
        val dbFile = File(searchConfig.dbPath)
        val parentDir = dbFile.parentFile
        
        if (parentDir != null && !parentDir.exists()) {
            val created = parentDir.mkdirs()
            if (created) {
                logger.info("创建 SQLite 数据库目录: {}", parentDir.absolutePath)
            }
        }
    }
}
