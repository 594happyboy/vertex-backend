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
     * 生成游客令牌
     */
    fun generateVisitorToken(targetUserId: Long, targetUsername: String): String {
        val now = Date()
        val expiryDate = Date(now.time + expiration)
        
        val key = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))
        
        return Jwts.builder()
            .setSubject("visitor:$targetUserId")
            .claim("targetUserId", targetUserId)
            .claim("targetUsername", targetUsername)
            .claim("role", "VISITOR")
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
            val subject = claims.subject
            if (subject.startsWith("visitor:")) {
                null
            } else {
                subject.toLongOrNull()
            }
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
     * 从游客令牌获取目标用户ID
     */
    fun getTargetUserIdFromToken(token: String): Long? {
        return try {
            val claims = parseToken(token) ?: return null
            val role = claims.get("role", String::class.java)
            if (role == "VISITOR") {
                claims.get("targetUserId", java.lang.Long::class.java)?.toLong()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

