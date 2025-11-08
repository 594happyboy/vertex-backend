package com.zzy.file.util

import java.security.SecureRandom

/**
 * Nano ID 生成器
 * 
 * 生成21位随机字符串作为外部ID，用于替代数据库自增ID暴露给外部API
 * 
 * 特性：
 * - 长度：21位
 * - 字符集：A-Za-z0-9_- (64个字符，URL安全)
 * - 碰撞概率：极低（每秒生成1000个ID，需要~14万年才有1%碰撞概率）
 * - 安全性：使用 SecureRandom，不可预测
 * 
 * @author ZZY
 * @date 2025-11-07
 */
object NanoIdGenerator {
    
    /**
     * URL安全的字符集（64个字符）
     */
    private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_-"
    
    /**
     * 安全随机数生成器
     */
    private val secureRandom = SecureRandom()
    
    /**
     * Nano ID 的标准长度
     */
    private const val DEFAULT_SIZE = 21
    
    /**
     * 生成21位Nano ID
     * 
     * @return 21位随机字符串
     */
    fun generate(): String {
        return generate(DEFAULT_SIZE)
    }
    
    /**
     * 生成指定长度的Nano ID
     * 
     * @param size ID长度
     * @return 随机字符串
     */
    fun generate(size: Int): String {
        val id = StringBuilder(size)
        repeat(size) {
            id.append(ALPHABET[secureRandom.nextInt(ALPHABET.length)])
        }
        return id.toString()
    }
    
    /**
     * 生成唯一的Nano ID（带存在性检查和自动重试）
     * 
     * 此方法会在生成ID后检查是否已存在，如果存在则自动重试。
     * 虽然碰撞概率极低，但这提供了额外的安全保障。
     * 
     * @param checkExists 检查ID是否已存在的函数，返回true表示已存在
     * @param maxRetries 最大重试次数，默认3次
     * @return 唯一的Nano ID
     * @throws IllegalStateException 如果达到最大重试次数仍未生成唯一ID
     */
    fun generateUnique(
        checkExists: (String) -> Boolean,
        maxRetries: Int = 3
    ): String {
        repeat(maxRetries) {
            val nanoId = generate()
            if (!checkExists(nanoId)) {
                return nanoId
            }
        }
        throw IllegalStateException(
            "Failed to generate unique Nano ID after $maxRetries attempts. " +
            "This is extremely unlikely and may indicate a system issue."
        )
    }
}

