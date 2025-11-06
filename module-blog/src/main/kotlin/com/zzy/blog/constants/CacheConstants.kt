package com.zzy.blog.constants

/**
 * 缓存配置常量
 * @author ZZY
 * @date 2025-11-06
 */
object CacheConstants {
    /**
     * 目录树缓存 TTL（分钟）
     */
    const val DIRECTORY_TREE_TTL_MINUTES = 30L
    
    /**
     * Token 刷新等待相关
     */
    object TokenRefresh {
        /** 等待尝试次数 */
        const val WAIT_ATTEMPTS = 50
        
        /** 每次等待间隔（毫秒） */
        const val WAIT_INTERVAL_MS = 100L
    }
}

