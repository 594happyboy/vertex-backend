package com.zzy.search.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync

/**
 * 搜索模块配置
 */
@Configuration
@EnableAsync
@ConfigurationProperties(prefix = "search")
class SearchConfig {
    /**
     * SQLite 数据库文件路径
     */
    var dbPath: String = "data/search/document-index.db"
    
    /**
     * 是否在启动时自动初始化 Schema
     */
    var autoInitSchema: Boolean = true
    
    /**
     * 查询超时时间（毫秒）
     */
    var queryTimeout: Int = 5000
    
    /**
     * 最大搜索关键词长度
     */
    var maxQueryLength: Int = 128
}
