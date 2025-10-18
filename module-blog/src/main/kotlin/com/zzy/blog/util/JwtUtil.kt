package com.zzy.blog.util

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*
import javax.crypto.SecretKey

/**
 * JWT 工具类
 */
@Component
class JwtUtil {
    
    @Value("\${blog.jwt.secret:your-secret-key-change-it-in-production-must-be-at-least-256-bits}")
    private lateinit var secret: String
    
    @Value("\${blog.jwt.expiration:86400000}")
    private var expiration: Long = 86400000 // 默认 24 小时
    
    /**
     * 获取签名密钥
     */
    private fun getSignKey(): SecretKey {
        return Keys.hmacShaKeyFor(secret.toByteArray())
    }
    
    /**
     * 生成 Token
     * @param userId 用户ID
     * @param username 用户名
     * @return JWT Token
     */
    fun generateToken(userId: Long, username: String): String {
        val now = Date()
        val expiryDate = Date(now.time + expiration)
        
        return Jwts.builder()
            .setSubject(userId.toString())
            .claim("username", username)
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(getSignKey(), SignatureAlgorithm.HS256)
            .compact()
    }
    
    /**
     * 从 Token 中获取 Claims
     * @param token JWT Token
     * @return Claims
     */
    fun getClaimsFromToken(token: String): Claims? {
        return try {
            Jwts.parserBuilder()
                .setSigningKey(getSignKey())
                .build()
                .parseClaimsJws(token)
                .body
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 从 Token 中获取用户ID
     * @param token JWT Token
     * @return 用户ID
     */
    fun getUserIdFromToken(token: String): Long? {
        val claims = getClaimsFromToken(token) ?: return null
        return claims.subject?.toLongOrNull()
    }
    
    /**
     * 从 Token 中获取用户名
     * @param token JWT Token
     * @return 用户名
     */
    fun getUsernameFromToken(token: String): String? {
        val claims = getClaimsFromToken(token) ?: return null
        return claims["username"] as? String
    }
    
    /**
     * 验证 Token 是否有效
     * @param token JWT Token
     * @return 是否有效
     */
    fun validateToken(token: String): Boolean {
        return try {
            val claims = getClaimsFromToken(token) ?: return false
            val expiration = claims.expiration
            expiration.after(Date())
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 从请求头中提取 Token
     * @param authHeader Authorization 请求头
     * @return Token 或 null
     */
    fun extractTokenFromHeader(authHeader: String?): String? {
        if (authHeader.isNullOrBlank()) {
            return null
        }
        return if (authHeader.startsWith("Bearer ", ignoreCase = true)) {
            authHeader.substring(7)
        } else {
            null
        }
    }
}

