package com.zzy.common.util

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * Redis工具类
 * @author ZZY
 * @date 2025-10-09
 */
@Component
class RedisUtil(
    private val redisTemplate: RedisTemplate<String, Any>,
    private val objectMapper: ObjectMapper
) {
    
    /**
     * 设置缓存
     */
    fun set(key: String, value: Any, timeout: Long = 0, unit: TimeUnit = TimeUnit.SECONDS) {
        if (timeout > 0) {
            redisTemplate.opsForValue().set(key, value, timeout, unit)
        } else {
            redisTemplate.opsForValue().set(key, value)
        }
    }
    
    /**
     * 获取缓存
     */
    fun get(key: String): Any? {
        return redisTemplate.opsForValue().get(key)
    }
    
    /**
     * 获取并转换为指定类型
     */
    fun <T> get(key: String, clazz: Class<T>): T? {
        val value = redisTemplate.opsForValue().get(key) ?: return null
        return if (clazz.isInstance(value)) {
            clazz.cast(value)
        } else {
            objectMapper.convertValue(value, clazz)
        }
    }
    
    /**
     * 删除缓存
     */
    fun delete(key: String): Boolean {
        return redisTemplate.delete(key)
    }
    
    /**
     * 删除匹配的key
     */
    fun deleteByPattern(pattern: String) {
        val keys = redisTemplate.keys(pattern)
        if (keys.isNotEmpty()) {
            redisTemplate.delete(keys)
        }
    }
}

