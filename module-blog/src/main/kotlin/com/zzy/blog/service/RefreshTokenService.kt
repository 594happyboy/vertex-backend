package com.zzy.blog.service

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper
import com.zzy.blog.entity.RefreshTokenEntity
import com.zzy.blog.mapper.RefreshTokenMapper
import com.zzy.common.config.TokenConfig
import com.zzy.common.constants.AuthConstants
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

/**
 * RefreshToken 管理服务（MySQL 持久化）
 *
 * 通过数据库保存 RefreshToken 的完整生命周期，解决 Redis 重启后令牌丢失的问题。
 */
@Service
class RefreshTokenService(
    private val refreshTokenMapper: RefreshTokenMapper,
    private val tokenConfig: TokenConfig
) {

    private val logger = LoggerFactory.getLogger(RefreshTokenService::class.java)

    /**
     * 生成 RefreshToken
     *
     * @param userId 用户ID
     * @param ipAddress 客户端IP地址
     * @param userAgent 用户代理
     */
    fun generateRefreshToken(userId: Long, ipAddress: String?, userAgent: String?): String {
        val refreshToken = UUID.randomUUID().toString().replace("-", "")
        val now = LocalDateTime.now()
        val entity = RefreshTokenEntity(
            token = refreshToken,
            userId = userId,
            ipAddress = ipAddress ?: AuthConstants.UNKNOWN,
            userAgent = userAgent ?: AuthConstants.UNKNOWN,
            createdAt = now,
            expiresAt = now.plus(tokenConfig.refreshTokenTtl),
            rotated = false,
            revoked = false,
            updatedAt = now
        )
        refreshTokenMapper.insert(entity)

        logger.debug("生成RefreshToken: userId={}, token={}", userId, previewToken(refreshToken))
        return refreshToken
    }

    /**
     * 验证 RefreshToken 并返回用户ID
     */
    fun validateRefreshToken(refreshToken: String, currentIp: String?): Long? {
        val entity = refreshTokenMapper.selectById(refreshToken) ?: run {
            logger.warn("RefreshToken不存在或已过期: {}", previewToken(refreshToken))
            return null
        }

        val userId = entity.userId ?: return null
        val now = LocalDateTime.now()

        if (entity.revoked) {
            logger.warn("RefreshToken已被撤销: userId={}, token={}", userId, previewToken(refreshToken))
            return null
        }

        if (currentIp != null &&
            entity.ipAddress != null &&
            entity.ipAddress != AuthConstants.UNKNOWN &&
            currentIp != entity.ipAddress
        ) {
            logger.warn(
                "检测到RefreshToken IP变化: userId={}, 原IP={}, 当前IP={}",
                userId, entity.ipAddress, currentIp
            )
        }

        if (!entity.rotated) {
            if (entity.expiresAt != null && now.isAfter(entity.expiresAt)) {
                logger.warn("RefreshToken已过期: userId={}, token={}", userId, previewToken(refreshToken))
                return null
            }
            return userId
        }

        val graceExpiresAt = entity.graceExpiresAt
        if (graceExpiresAt != null && (now.isBefore(graceExpiresAt) || now.isEqual(graceExpiresAt))) {
            val remainingSeconds = Duration.between(now, graceExpiresAt).seconds.coerceAtLeast(0)
            logger.debug(
                "RefreshToken处于宽限期: userId={}, token={}, 剩余={}s",
                userId,
                previewToken(refreshToken),
                remainingSeconds
            )
            return userId
        }

        logger.warn("RefreshToken已轮换且超过宽限期: userId={}, token={}", userId, previewToken(refreshToken))
        return null
    }

    /**
     * 轮换 RefreshToken（旧 token 保留宽限期）
     */
    fun rotateRefreshToken(oldRefreshToken: String, ipAddress: String?, userAgent: String?): Pair<Long, String>? {
        val entity = refreshTokenMapper.selectById(oldRefreshToken) ?: return null
        val userId = entity.userId ?: return null
        if (entity.revoked) {
            logger.warn("无法轮换已撤销的RefreshToken: token={}", previewToken(oldRefreshToken))
            return null
        }

        val newToken = generateRefreshToken(userId, ipAddress, userAgent)
        val now = LocalDateTime.now()
        val graceExpiresAt = now.plus(tokenConfig.gracePeriod)

        val updateWrapper = UpdateWrapper<RefreshTokenEntity>()
            .eq("token", oldRefreshToken)
            .set("rotated", true)
            .set("rotated_at", now)
            .set("replaced_by", newToken)
            .set("grace_expires_at", graceExpiresAt)
            .set("updated_at", now)
        refreshTokenMapper.update(null, updateWrapper)

        logger.debug("RefreshToken已轮转: userId={}, newToken={}", userId, previewToken(newToken))
        return Pair(userId, newToken)
    }

    /** 轮换 RefreshToken（仅返回新 token） */
    fun rotateRefreshTokenSimple(oldToken: String, ipAddress: String?, userAgent: String?): String? =
        rotateRefreshToken(oldToken, ipAddress, userAgent)?.second

    /** 撤销单个 RefreshToken */
    fun revokeRefreshToken(token: String) {
        val now = LocalDateTime.now()
        val updateWrapper = UpdateWrapper<RefreshTokenEntity>()
            .eq("token", token)
            .set("revoked", true)
            .set("revoked_at", now)
            .set("updated_at", now)
        val updated = refreshTokenMapper.update(null, updateWrapper)
        if (updated > 0) {
            logger.debug("撤销RefreshToken: {}", previewToken(token))
        }
    }

    /** 撤销用户所有 RefreshToken（用于登出/安全事件） */
    fun revokeAllUserTokens(userId: Long) {
        val now = LocalDateTime.now()
        val updateWrapper = UpdateWrapper<RefreshTokenEntity>()
            .eq("user_id", userId)
            .eq("revoked", false)
            .set("revoked", true)
            .set("revoked_at", now)
            .set("updated_at", now)
        val count = refreshTokenMapper.update(null, updateWrapper)
        if (count > 0) {
            logger.info("撤销用户所有RefreshToken: userId={}, count={}", userId, count)
        } else {
            logger.info("撤销用户所有RefreshToken: userId={}, count=0", userId)
        }
    }

    private fun previewToken(token: String) = token.take(8) + "..."
}
