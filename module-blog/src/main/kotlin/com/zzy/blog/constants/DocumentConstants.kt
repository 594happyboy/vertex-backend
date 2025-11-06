package com.zzy.blog.constants

/**
 * 文档相关常量
 * @author ZZY
 * @date 2025-11-06
 */
object DocumentConstants {
    /**
     * 支持的文档文件类型
     */
    val SUPPORTED_EXTENSIONS = setOf("html", "md", "pdf", "txt")
    
    /**
     * 分页查询最大限制
     */
    const val MAX_LIMIT = 100
    
    /**
     * 最大文件大小: 100MB
     */
    const val MAX_FILE_SIZE = 100 * 1024 * 1024L
}

