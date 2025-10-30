package com.zzy.blog.service

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.zzy.blog.config.TokenConfig
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.*
import kotlin.math.max

/**
 * RefreshToken 管理服务
 * 
 * ## 功能
 * - 生成、验证、撤销 RefreshToken（支持轮换 + 宽限期）
 * - Redis 存储 + JSON 序列化
 * - 记录设备信息，检测异常访问
 * 
 * @author ZZY
 * @date 2025-10-29
 */
@Service
class RefreshTokenService(
    private val stringRedisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val tokenConfig: TokenConfig
) {
    
    private val logger = LoggerFactory.getLogger(RefreshTokenService::class.java)
    
    companion object {
        private const val REFRESH_TOKEN_PREFIX = "refresh_token:"
        private const val USER_TOKENS_PREFIX = "user_tokens:"
        private const val UNKNOWN = "unknown"
    }

    /**
     * RefreshToken元信息
     */
    private data class RefreshTokenInfo(
        val userId: Long,
        val ipAddress: String,
        val userAgent: String,
        val createTime: Long,
        val rotated: Boolean = false,
        val rotatedAt: Long? = null,
        val replacedBy: String? = null,
        val graceExpiresAt: Long? = null
    )
    
    /**
     * 生成 RefreshToken 并存储到Redis
     * 
     * @param userId 用户ID
     * @param ipAddress 客户端IP地址
     * @param userAgent 用户代理（浏览器信息）
     * @return 生成的RefreshToken字符串
     */
    fun generateRefreshToken(userId: Long, ipAddress: String?, userAgent: String?): String {
        val refreshToken = UUID.randomUUID().toString().replace("-", "")
        val info = RefreshTokenInfo(
            userId = userId,
            ipAddress = ipAddress ?: UNKNOWN,
            userAgent = userAgent ?: UNKNOWN,
            createTime = System.currentTimeMillis()
        )

        val redisKey = redisKey(refreshToken)
        writeTokenInfo(redisKey, info, tokenConfig.refreshTokenTtl)

        val userTokensKey = userTokensKey(userId)
        stringRedisTemplate.opsForSet().add(userTokensKey, refreshToken)
        stringRedisTemplate.expire(userTokensKey, tokenConfig.refreshTokenTtl)

        logger.debug("生成RefreshToken: userId={}, token={}", userId, previewToken(refreshToken))

        return refreshToken
    }
    
    /**
     * 验证 RefreshToken 并返回用户ID
     */
    fun validateRefreshToken(refreshToken: String, currentIp: String?): Long? {
        val info = readTokenInfo(redisKey(refreshToken)) ?: run {
            logger.warn("RefreshToken不存在或已过期: {}", previewToken(refreshToken))
            return null
        }

        // 检测 IP 变化（仅警告，不阻止）
        if (currentIp != null && info.ipAddress != UNKNOWN && currentIp != info.ipAddress) {
            logger.warn(
                "检测到RefreshToken IP变化: userId={}, 原IP={}, 当前IP={}",
                info.userId, info.ipAddress, currentIp
            )
        }

        // 未轮换的 token 直接有效
        if (!info.rotated) return info.userId

        // 已轮换则检查宽限期
        val now = System.currentTimeMillis()
        val graceExpiresAt = info.graceExpiresAt
        
        return when {
            graceExpiresAt != null && now <= graceExpiresAt -> {
                logger.debug(
                    "RefreshToken处于宽限期: userId={}, token={}, 剩余{}ms",
                    info.userId, previewToken(refreshToken), graceExpiresAt - now
                )
                info.userId
            }
            else -> {
                logger.warn("RefreshToken已轮换且超过宽限期: userId={}, token={}", info.userId, previewToken(refreshToken))
                null
            }
        }
    }
    
    /**
     * 轮换 RefreshToken（旧 token 保留宽限期）
     */
    fun rotateRefreshToken(oldRefreshToken: String, ipAddress: String?, userAgent: String?): Pair<Long, String>? {
        val key = redisKey(oldRefreshToken)
        val info = readTokenInfo(key) ?: return null

        val newToken = generateRefreshToken(info.userId, ipAddress, userAgent)
        val now = System.currentTimeMillis()

        // 标记旧 token 已轮换，设置宽限到期时间
        val rotatedInfo = info.copy(
            rotated = true,
            rotatedAt = now,
            replacedBy = newToken,
            graceExpiresAt = now + tokenConfig.gracePeriod.toMillis()
        )

        // TTL 取宽限期与 token 有效期的较大值
        val ttl = Duration.ofSeconds(
            max(tokenConfig.gracePeriod.seconds, tokenConfig.refreshTokenTtl.seconds)
        )
        writeTokenInfo(key, rotatedInfo, ttl)

        logger.debug("RefreshToken已轮转: userId={}, newToken={}", info.userId, previewToken(newToken))
        return Pair(info.userId, newToken)
    }
    
    /** 轮换 RefreshToken（仅返回新 token） */
    fun rotateRefreshTokenSimple(oldToken: String, ipAddress: String?, userAgent: String?): String? =
        rotateRefreshToken(oldToken, ipAddress, userAgent)?.second
    
    /** 撤销单个 RefreshToken */
    fun revokeRefreshToken(token: String) {
        val key = redisKey(token)
        readTokenInfo(key)?.let { info ->
            stringRedisTemplate.opsForSet().remove(userTokensKey(info.userId), token)
        }
        stringRedisTemplate.delete(key)
        logger.debug("撤销RefreshToken: {}", previewToken(token))
    }
    
    /** 撤销用户所有 RefreshToken（用于登出/安全事件） */
    fun revokeAllUserTokens(userId: Long) {
        val tokensKey = userTokensKey(userId)
        val tokens = stringRedisTemplate.opsForSet().members(tokensKey) ?: emptySet()
        
        if (tokens.isNotEmpty()) {
            tokens.forEach { stringRedisTemplate.delete(redisKey(it)) }
            stringRedisTemplate.delete(tokensKey)
            logger.info("撤销用户所有RefreshToken: userId={}, count={}", userId, tokens.size)
        }
    }
    
    private fun redisKey(token: String) = "$REFRESH_TOKEN_PREFIX$token"

    private fun userTokensKey(userId: Long) = "$USER_TOKENS_PREFIX$userId"

    private fun previewToken(token: String) = token.take(8) + "..."

    private fun readTokenInfo(key: String): RefreshTokenInfo? =
        stringRedisTemplate.opsForValue().get(key)?.let { json ->
            try {
                objectMapper.readValue(json, RefreshTokenInfo::class.java)
            } catch (ex: Exception) {
                logger.error("解析RefreshToken失败: key={}, error={}", key, ex.message)
                null
            }
        }

    private fun writeTokenInfo(key: String, info: RefreshTokenInfo, ttl: Duration) {
        try {
            val json = objectMapper.writeValueAsString(info)
            stringRedisTemplate.opsForValue().set(key, json, ttl)
        } catch (ex: JsonProcessingException) {
            logger.error("序列化RefreshToken失败: userId={}, error={}", info.userId, ex.message)
        }
    }
}

