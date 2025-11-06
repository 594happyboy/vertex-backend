package com.zzy.common.util

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
 * 
 * ## 核心功能
 * - 生成 AccessToken
 * - 解析 Token
 * - 验证 Token
 * - 获取 Token 信息
 * 
 * @author ZZY
 * @date 2025-11-06
 */
@Component
class JwtUtil {
    
    @Value("\${jwt.secret:your-256-bit-secret-key-change-in-production-environment}")
    private lateinit var secret: String
    
    @Value("\${jwt.access-token-expiration:1800000}") // AccessToken默认30分钟
    private var accessTokenExpiration: Long = 1800000
    
    @Value("\${jwt.issuer:vertex-backend}")
    private lateinit var issuer: String
    
    /**
     * 生成用户 AccessToken（短期，30分钟）
     */
    fun generateAccessToken(userId: Long, username: String): String {
        val now = Date()
        val expiryDate = Date(now.time + accessTokenExpiration)
        
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
     * 验证令牌是否有效（未过期且签名正确）
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
     * 从令牌获取用户名
     */
    fun getUsernameFromToken(token: String): String? {
        return try {
            val claims = parseToken(token)
            claims?.get("username", String::class.java)
        } catch (e: Exception) {
            null
        }
    }
}

