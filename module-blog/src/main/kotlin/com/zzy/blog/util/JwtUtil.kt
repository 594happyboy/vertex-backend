package com.zzy.blog.util

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * JWT 工具类
 * @author ZZY
 * @date 2025-10-18
 */
@Component
class JwtUtil {
    
    @Value("\${jwt.secret:your-256-bit-secret-key-change-in-production-environment}")
    private lateinit var secret: String
    
    @Value("\${jwt.expiration:7200000}") // 默认2小时
    private var expiration: Long = 7200000
    
    @Value("\${jwt.refresh-threshold:1800000}") // 默认30分钟，当剩余时间少于此值时自动刷新
    private var refreshThreshold: Long = 1800000
    
    @Value("\${jwt.issuer:vertex-backend}")
    private lateinit var issuer: String
    
    /**
     * 生成用户令牌
     */
    fun generateUserToken(userId: Long, username: String): String {
        val now = Date()
        val expiryDate = Date(now.time + expiration)
        
        val key = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))
        
        return Jwts.builder()
            .setSubject(userId.toString())
            .claim("username", username)
            .claim("role", "USER")
            .setIssuer(issuer)
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()
    }
    
    /**
     * 解析令牌
     */
    fun parseToken(token: String): Claims? {
        return try {
            val key = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))
            Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .body
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 验证令牌
     */
    fun validateToken(token: String): Boolean {
        return try {
            val claims = parseToken(token)
            claims != null && claims.expiration.after(Date())
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 从令牌获取用户ID
     */
    fun getUserIdFromToken(token: String): Long? {
        return try {
            val claims = parseToken(token) ?: return null
            claims.subject.toLongOrNull()
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 从令牌获取角色
     */
    fun getRoleFromToken(token: String): String? {
        return try {
            val claims = parseToken(token)
            claims?.get("role", String::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 检查令牌是否需要刷新
     * @return true 如果令牌还有效但即将过期（剩余时间 < refreshThreshold）
     */
    fun shouldRefreshToken(token: String): Boolean {
        return try {
            val claims = parseToken(token) ?: return false
            val expiration = claims.expiration
            val now = Date()
            
            // 如果已过期，返回false
            if (expiration.before(now)) {
                return false
            }
            
            // 如果剩余时间少于阈值，需要刷新
            val remainingTime = expiration.time - now.time
            remainingTime < refreshThreshold
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 刷新令牌（基于旧令牌生成新令牌）
     */
    fun refreshToken(oldToken: String): String? {
        return try {
            val claims = parseToken(oldToken) ?: return null
            val role = claims.get("role", String::class.java)
            
            if (role == "USER") {
                val userId = getUserIdFromToken(oldToken) ?: return null
                val username = claims.get("username", String::class.java) ?: return null
                generateUserToken(userId, username)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

