package com.zzy.blog.constants

/**
 * Redis Key 前缀常量
 * 统一管理所有 Redis 键的命名规范
 * @author ZZY
 * @date 2025-11-06
 */
object RedisKeyConstants {
    /**
     * Token 刷新相关
     */
    object TokenRefresh {
        /** Token 刷新分布式锁前缀 */
        const val LOCK_PREFIX = "token:refresh:lock:"
        
        /** Token 刷新缓存前缀 */
        const val CACHE_PREFIX = "token:refresh:cache:"
    }
    
    /**
     * RefreshToken 相关
     */
    object RefreshToken {
        /** RefreshToken 信息前缀 */
        const val TOKEN_PREFIX = "refresh_token:"
        
        /** 用户所有 Token 集合前缀 */
        const val USER_TOKENS_PREFIX = "user_tokens:"
    }
    
    /**
     * 缓存相关
     */
    object Cache {
        /** 目录树缓存前缀 */
        const val DIRECTORY_TREE_PREFIX = "directory_tree:"
    }
}

