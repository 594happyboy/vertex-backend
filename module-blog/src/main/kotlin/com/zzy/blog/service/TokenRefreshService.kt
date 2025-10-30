package com.zzy.blog.service

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.zzy.blog.config.TokenConfig
import com.zzy.blog.mapper.UserMapper
import com.zzy.blog.util.JwtUtil
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service

/**
 * Token 刷新服务（分布式锁方案）
 * 
 * ## 核心功能
 * - 使用 Redis 分布式锁防止并发刷新
 * - 缓存完整 TokenPair 供并发请求共享
 * - 确保同一用户并发请求只刷新一次
 * 
 * @author ZZY
 * @date 2025-10-30
 */
@Service
class TokenRefreshService(
    private val jwtUtil: JwtUtil,
    private val refreshTokenService: RefreshTokenService,
    private val userMapper: UserMapper,
    private val objectMapper: ObjectMapper,
    private val redisTemplate: StringRedisTemplate,
    private val tokenConfig: TokenConfig
) {
    
    private val logger = LoggerFactory.getLogger(TokenRefreshService::class.java)
    
    companion object {
        private const val LOCK_PREFIX = "token:refresh:lock:"
        private const val CACHE_PREFIX = "token:refresh:cache:"
        private const val WAIT_ATTEMPTS = 50
        private const val WAIT_INTERVAL_MS = 100L
    }
    
    /** Token 刷新结果 */
    data class TokenPair(
        val accessToken: String,
        val refreshToken: String
    )
    
    /** 使用分布式锁刷新 Token */
    fun refreshWithLock(
        userId: Long,
        oldRefreshToken: String,
        ipAddress: String?,
        userAgent: String?
    ): TokenPair? {
        val lockKey = "$LOCK_PREFIX$userId"
        val cacheKey = "$CACHE_PREFIX$userId"
        
        // 1. 检查缓存（可能已被其他请求刷新）
        readFromCache(cacheKey, oldRefreshToken)?.let {
            logger.info("✅ 从缓存获取Token: userId={}", userId)
            return it
        }
        
        // 2. 尝试获取分布式锁
        val acquired = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, "locked", tokenConfig.lockTimeout) ?: false
        
        return if (acquired) {
            refreshWithLockHeld(userId, oldRefreshToken, ipAddress, userAgent, lockKey, cacheKey)
        } else {
            logger.info("⏳ 等待其他请求刷新Token: userId={}", userId)
            waitForCachedToken(userId, cacheKey, oldRefreshToken)
        }
    }
    
    /** 持有锁时执行刷新 */
    private fun refreshWithLockHeld(
        userId: Long,
        oldRefreshToken: String,
        ipAddress: String?,
        userAgent: String?,
        lockKey: String,
        cacheKey: String
    ): TokenPair? = try {
        logger.info("🔒 获取刷新锁成功: userId={}", userId)
        
        // 双重检查缓存
        readFromCache(cacheKey, oldRefreshToken)?.let {
            logger.info("✅ 双重检查：从缓存获取Token: userId={}", userId)
            return it
        }
        
        // 执行刷新
        performTokenRefresh(userId, oldRefreshToken, ipAddress, userAgent)?.also { result ->
            writeToCache(cacheKey, result)
            logger.info("✅ Token刷新成功并已缓存: userId={}", userId)
        }
    } catch (e: Exception) {
        logger.error("❌ Token刷新失败: userId={}, error={}", userId, e.message, e)
        null
    } finally {
        redisTemplate.delete(lockKey)
        logger.debug("🔓 释放刷新锁: userId={}", userId)
    }
    
    /** 执行实际的 Token 刷新 */
    private fun performTokenRefresh(
        userId: Long,
        oldRefreshToken: String,
        ipAddress: String?,
        userAgent: String?
    ): TokenPair? {
        // 验证 RefreshToken
        val validUserId = refreshTokenService.validateRefreshToken(oldRefreshToken, ipAddress)
        if (validUserId != userId) {
            logger.warn("❌ RefreshToken无效: userId={}", userId)
            return null
        }
        
        // 获取用户信息
        val user = userMapper.selectById(userId) ?: run {
            logger.warn("❌ 用户不存在: userId={}", userId)
            return null
        }
        
        if (user.status == 0) {
            logger.warn("❌ 用户已被禁用: userId={}", userId)
            return null
        }
        
        // 生成新 Token
        val newAccessToken = jwtUtil.generateAccessToken(userId, user.username)
        val newRefreshToken = refreshTokenService.rotateRefreshTokenSimple(
            oldRefreshToken, ipAddress, userAgent
        ) ?: oldRefreshToken
        
        logger.info("✅ Token刷新完成: userId={}, username={}", userId, user.username)
        return TokenPair(newAccessToken, newRefreshToken)
    }
    
    /** 从 RefreshToken 获取用户 ID */
    fun getUserIdFromRefreshToken(refreshToken: String, ipAddress: String?): Long? =
        refreshTokenService.validateRefreshToken(refreshToken, ipAddress)
    
    /** 写入 TokenPair 到缓存 */
    private fun writeToCache(key: String, pair: TokenPair) {
        try {
            val json = objectMapper.writeValueAsString(pair)
            redisTemplate.opsForValue().set(key, json, tokenConfig.tokenCacheTtl)
        } catch (ex: JsonProcessingException) {
            logger.error("序列化TokenPair失败: error={}", ex.message)
        }
    }

    /** 从缓存读取 TokenPair（兼容旧格式） */
    private fun readFromCache(key: String, fallbackRefreshToken: String): TokenPair? {
        val raw = redisTemplate.opsForValue().get(key) ?: return null
        
        // 尝试反序列化为 TokenPair
        return try {
            objectMapper.readValue(raw, TokenPair::class.java)
        } catch (ex: Exception) {
            // 兼容旧格式（仅 AccessToken 字符串）
            if (raw.isNotEmpty()) {
                logger.debug("检测到旧格式Token缓存，使用回退: key={}", key)
                TokenPair(raw, fallbackRefreshToken)
            } else null
        }
    }

    /** 等待并从缓存获取 Token */
    private fun waitForCachedToken(
        userId: Long,
        cacheKey: String,
        fallbackRefreshToken: String
    ): TokenPair? {
        repeat(WAIT_ATTEMPTS) { attempt ->
            try {
                Thread.sleep(WAIT_INTERVAL_MS)
                
                readFromCache(cacheKey, fallbackRefreshToken)?.let {
                    logger.info("✅ 等待后从缓存获取Token: userId={}, attempt={}", userId, attempt + 1)
                    return it
                }
            } catch (e: InterruptedException) {
                logger.warn("⚠️ 等待被中断: userId={}", userId)
                Thread.currentThread().interrupt()
                return null
            }
        }
        
        logger.error("❌ 等待超时：未能从缓存获取Token: userId={}", userId)
        return null
    }
}
