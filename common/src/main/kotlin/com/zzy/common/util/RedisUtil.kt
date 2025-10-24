package com.zzy.common.util

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * Redis缓存工具类 - 类型安全的缓存操作封装
 * 
 * ## 设计理念
 * 1. **类型安全**：使用泛型确保编译时类型检查
 * 2. **简单易用**：封装常用操作，隐藏底层复杂性
 * 3. **错误处理**：捕获异常并记录日志，避免缓存故障影响业务
 * 4. **统一序列化**：使用RedisTemplate的序列化机制，确保一致性
 * 
 * ## 使用场景
 * - 缓存数据库查询结果
 * - 缓存计算结果
 * - 临时数据存储
 * - 分布式锁（简单场景）
 * 
 * ## 核心概念
 * 
 * ### 1. 缓存键命名规范
 * 推荐使用冒号分隔的层级结构：
 * ```
 * 模块:功能:具体标识
 * 例如：
 * - user:info:1001         // 用户信息
 * - file:list:page1:size20 // 文件列表
 * - blog:doc:123           // 博客文档
 * ```
 * 
 * ### 2. 缓存过期策略
 * - 热点数据：较长过期时间（30分钟 - 1小时）
 * - 普通数据：中等过期时间（5-10分钟）
 * - 临时数据：较短过期时间（1-5分钟）
 * - 不设置过期：仅用于需要手动删除的数据
 * 
 * ### 3. 缓存更新策略
 * - **Cache Aside**（推荐）：读时写入缓存，更新时删除缓存
 * - **Write Through**：更新数据库同时更新缓存
 * - **Write Behind**：异步更新数据库，立即更新缓存
 * 
 * ## 使用示例
 * ```kotlin
 * @Service
 * class UserService(private val redisUtil: RedisUtil) {
 *     
 *     fun getUser(id: Long): User {
 *         val cacheKey = "user:info:$id"
 *         
 *         // 尝试从缓存获取
 *         val cached = redisUtil.get(cacheKey, User::class.java)
 *         if (cached != null) return cached
 *         
 *         // 从数据库查询
 *         val user = userMapper.selectById(id)
 *         
 *         // 写入缓存（5分钟过期）
 *         redisUtil.set(cacheKey, user, 5, TimeUnit.MINUTES)
 *         
 *         return user
 *     }
 *     
 *     fun updateUser(user: User) {
 *         userMapper.updateById(user)
 *         // 删除缓存，下次查询时重新加载
 *         redisUtil.delete("user:info:${user.id}")
 *     }
 * }
 * ```
 * 
 * ## 注意事项
 * ⚠️ 缓存不是银弹，需要权衡：
 * - 缓存命中率 vs 数据一致性
 * - 内存占用 vs 查询性能
 * - 缓存维护成本 vs 收益
 * 
 * ⚠️ 避免缓存大对象：
 * - 单个缓存值建议 < 1MB
 * - 大对象考虑分片存储
 * 
 * ⚠️ 防止缓存穿透/雪崩：
 * - 缓存穿透：缓存空结果（需业务判断）
 * - 缓存雪崩：设置随机过期时间
 * - 缓存击穿：使用分布式锁（热点数据）
 * 
 * @author ZZY
 * @date 2025-10-20
 */
@Component
class RedisUtil(
    private val redisTemplate: RedisTemplate<String, Any>
) {
    
    private val logger = LoggerFactory.getLogger(RedisUtil::class.java)
    
    // ==================== 基础操作 ====================
    
    /**
     * 设置缓存（无过期时间）
     * 
     * ## 使用场景
     * - 配置信息（手动更新）
     * - 永久性数据
     * - 需要显式删除的数据
     * 
     * ## 注意
     * ⚠️ 无过期时间的缓存会一直占用内存
     * ⚠️ 必须手动删除，否则可能导致内存泄漏
     * ⚠️ 建议优先使用带过期时间的方法
     * 
     * @param key 缓存键
     * @param value 缓存值（任意类型）
     */
    fun set(key: String, value: Any) {
        try {
            redisTemplate.opsForValue().set(key, value)
            logger.debug("Redis SET成功: key={}", key)
        } catch (e: Exception) {
            logger.error("Redis SET失败: key={}", key, e)
            // 缓存失败不应影响业务，仅记录日志
        }
    }
    
    /**
     * 设置缓存（带过期时间）⭐ 推荐使用
     * 
     * ## 使用场景
     * - 查询结果缓存
     * - 计算结果缓存
     * - 临时数据存储
     * 
     * ## 过期时间建议
     * - 用户信息：5-10分钟
     * - 列表数据：1-5分钟
     * - 统计数据：10-30分钟
     * - 配置信息：30-60分钟
     * 
     * ## 示例
     * ```kotlin
     * // 缓存5分钟
     * redisUtil.set("user:1", user, 5, TimeUnit.MINUTES)
     * 
     * // 缓存1小时
     * redisUtil.set("config:system", config, 1, TimeUnit.HOURS)
     * 
     * // 缓存30秒
     * redisUtil.set("temp:token", token, 30, TimeUnit.SECONDS)
     * ```
     * 
     * @param key 缓存键
     * @param value 缓存值（任意类型）
     * @param timeout 过期时间数值
     * @param unit 时间单位（SECONDS、MINUTES、HOURS、DAYS）
     */
    fun set(key: String, value: Any, timeout: Long, unit: TimeUnit) {
        try {
            if (timeout <= 0) {
                logger.warn("Redis SET: timeout <= 0，将设置为无过期时间: key={}", key)
                set(key, value)
                return
            }
            redisTemplate.opsForValue().set(key, value, timeout, unit)
            logger.debug("Redis SET成功: key={}, timeout={}({})", key, timeout, unit)
        } catch (e: Exception) {
            logger.error("Redis SET失败: key={}, timeout={}({})", key, timeout, unit, e)
        }
    }
    
    /**
     * 获取缓存（返回原始类型）
     * 
     * ## 使用场景
     * - 已知返回类型的场景
     * - 需要自己处理类型转换
     * 
     * ## 注意
     * ⚠️ 返回类型是Any?，需要自己进行类型转换
     * ⚠️ 推荐使用带类型参数的 get(key, clazz) 方法
     * 
     * @param key 缓存键
     * @return 缓存值，不存在返回null
     */
    fun get(key: String): Any? {
        return try {
            val value = redisTemplate.opsForValue().get(key)
            if (value != null) {
                logger.debug("Redis GET命中: key={}", key)
            } else {
                logger.debug("Redis GET未命中: key={}", key)
            }
            value
        } catch (e: Exception) {
            logger.error("Redis GET失败: key={}", key, e)
            null
        }
    }
    
    /**
     * 获取缓存（类型安全）⭐ 推荐使用
     * 
     * ## 优势
     * - 编译时类型检查
     * - 自动类型转换
     * - 类型不匹配返回null（而不是抛异常）
     * 
     * ## 工作原理
     * 1. 从Redis获取数据（已通过RedisTemplate反序列化）
     * 2. 检查类型是否匹配
     * 3. 匹配则强转，不匹配返回null
     * 
     * ## 示例
     * ```kotlin
     * // 获取单个对象
     * val user = redisUtil.get("user:1", User::class.java)
     * 
     * // 获取列表（需要使用TypeReference，见下方getList方法）
     * val users = redisUtil.getList("users", User::class.java)
     * 
     * // 获取基本类型
     * val count = redisUtil.get("count", Long::class.java)
     * ```
     * 
     * ## 注意事项
     * ⚠️ 泛型集合（如List<User>）不能直接获取，请使用getList方法
     * ⚠️ 如果类型不匹配，返回null而不是抛异常
     * 
     * @param key 缓存键
     * @param clazz 期望的类型
     * @return 缓存值，不存在或类型不匹配返回null
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String, clazz: Class<T>): T? {
        return try {
            // 从Redis获取值（RedisTemplate已经反序列化）
            val value = redisTemplate.opsForValue().get(key) ?: return null
            
            // 检查类型是否匹配
            if (clazz.isInstance(value)) {
                logger.debug("Redis GET命中: key={}, type={}", key, clazz.simpleName)
                clazz.cast(value)
            } else {
                // 类型不匹配，记录警告
                logger.warn(
                    "Redis GET类型不匹配: key={}, expected={}, actual={}",
                    key,
                    clazz.simpleName,
                    value.javaClass.simpleName
                )
                null
            }
        } catch (e: Exception) {
            logger.error("Redis GET失败: key={}, type={}", key, clazz.simpleName, e)
            null
        }
    }
    
    /**
     * 获取列表类型的缓存
     * 
     * ## 为什么需要专门的方法？
     * 由于Java的类型擦除，List<User>在运行时只是List，无法直接获取泛型参数。
     * 这个方法通过运行时类型检查，确保列表中的元素类型正确。
     * 
     * ## 使用场景
     * - 缓存查询结果列表
     * - 缓存ID列表
     * - 缓存配置项列表
     * 
     * ## 示例
     * ```kotlin
     * // 缓存用户列表
     * val users: List<User> = listOf(user1, user2)
     * redisUtil.set("users", users, 5, TimeUnit.MINUTES)
     * 
     * // 获取用户列表
     * val cachedUsers = redisUtil.getList("users", User::class.java)
     * ```
     * 
     * @param key 缓存键
     * @param elementType 列表元素的类型
     * @return 列表，不存在或类型错误返回null
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getList(key: String, elementType: Class<T>): List<T>? {
        return try {
            val value = redisTemplate.opsForValue().get(key) ?: return null
            
            // 检查是否是List类型
            if (value is List<*>) {
                // 验证列表中的元素类型（检查第一个非null元素）
                val firstElement = value.firstOrNull { it != null }
                if (firstElement == null || elementType.isInstance(firstElement)) {
                    logger.debug("Redis GET LIST命中: key={}, elementType={}, size={}", 
                        key, elementType.simpleName, value.size)
                    value as List<T>
                } else {
                    logger.warn(
                        "Redis GET LIST元素类型不匹配: key={}, expected={}, actual={}",
                        key,
                        elementType.simpleName,
                        firstElement.javaClass.simpleName
                    )
                    null
                }
            } else {
                logger.warn("Redis GET LIST类型错误: key={}, actual={}", key, value.javaClass.simpleName)
                null
            }
        } catch (e: Exception) {
            logger.error("Redis GET LIST失败: key={}, elementType={}", key, elementType.simpleName, e)
            null
        }
    }
    
    /**
     * 删除缓存
     * 
     * ## 使用场景
     * - 数据更新后删除缓存
     * - 手动清理过期数据
     * - 缓存失效处理
     * 
     * ## 注意
     * - 删除不存在的key不会报错
     * - 返回true表示成功删除（key存在），false表示key不存在
     * 
     * ## 示例
     * ```kotlin
     * // 更新用户后删除缓存
     * userMapper.updateById(user)
     * redisUtil.delete("user:info:${user.id}")
     * 
     * // 删除列表缓存
     * redisUtil.delete("user:list:page1")
     * ```
     * 
     * @param key 缓存键
     * @return true-删除成功，false-key不存在
     */
    fun delete(key: String): Boolean {
        return try {
            val deleted = redisTemplate.delete(key)
            if (deleted) {
                logger.debug("Redis DELETE成功: key={}", key)
            } else {
                logger.debug("Redis DELETE: key不存在: {}", key)
            }
            deleted
        } catch (e: Exception) {
            logger.error("Redis DELETE失败: key={}", key, e)
            false
        }
    }
    
    /**
     * 批量删除缓存（精确匹配）
     * 
     * ## 使用场景
     * - 批量删除相关缓存
     * - 清理多个key
     * 
     * ## 示例
     * ```kotlin
     * // 删除多个用户缓存
     * redisUtil.delete(listOf("user:1", "user:2", "user:3"))
     * ```
     * 
     * @param keys 缓存键列表
     * @return 成功删除的key数量
     */
    fun delete(keys: Collection<String>): Long {
        if (keys.isEmpty()) return 0
        
        return try {
            val deleted = redisTemplate.delete(keys)
            logger.debug("Redis DELETE批量成功: keys={}, deleted={}", keys.size, deleted)
            deleted
        } catch (e: Exception) {
            logger.error("Redis DELETE批量失败: keys={}", keys.size, e)
            0
        }
    }
    
    /**
     * 按模式删除缓存（模糊匹配）⚠️ 慎用
     * 
     * ## 模式语法
     * - `*`: 匹配任意字符
     * - `?`: 匹配单个字符
     * - `[abc]`: 匹配a、b或c
     * 
     * ## 使用场景
     * - 清除某个模块的所有缓存
     * - 清除某个用户的所有缓存
     * - 批量清理缓存
     * 
     * ## 示例
     * ```kotlin
     * // 删除所有文件列表缓存
     * redisUtil.deleteByPattern("file:list:*")
     * 
     * // 删除用户123的所有缓存
     * redisUtil.deleteByPattern("user:*:123")
     * 
     * // 删除所有临时缓存
     * redisUtil.deleteByPattern("temp:*")
     * ```
     * 
     * ## 注意事项
     * ⚠️ KEYS命令会阻塞Redis，生产环境慎用
     * ⚠️ 如果key数量很多，会影响性能
     * ⚠️ 建议在低峰期使用或使用SCAN命令（本方法未实现）
     * ⚠️ 误操作可能删除大量数据，使用前请仔细检查模式
     * 
     * @param pattern 匹配模式
     * @return 删除的key数量
     */
    fun deleteByPattern(pattern: String): Long {
        return try {
            // 查找匹配的key
            val keys = redisTemplate.keys(pattern)
            if (keys.isEmpty()) {
                logger.debug("Redis DELETE BY PATTERN: 没有匹配的key: pattern={}", pattern)
                return 0
            }
            
            // 批量删除
            val deleted = redisTemplate.delete(keys)
            logger.info("Redis DELETE BY PATTERN成功: pattern={}, deleted={}", pattern, deleted)
            deleted
        } catch (e: Exception) {
            logger.error("Redis DELETE BY PATTERN失败: pattern={}", pattern, e)
            0
        }
    }
    
    // ==================== 判断与检查 ====================
    
    /**
     * 判断key是否存在
     * 
     * ## 使用场景
     * - 检查缓存是否存在
     * - 避免重复设置缓存
     * 
     * ## 示例
     * ```kotlin
     * if (!redisUtil.hasKey("user:1")) {
     *     val user = userMapper.selectById(1)
     *     redisUtil.set("user:1", user, 5, TimeUnit.MINUTES)
     * }
     * ```
     * 
     * @param key 缓存键
     * @return true-存在，false-不存在
     */
    fun hasKey(key: String): Boolean {
        return try {
            redisTemplate.hasKey(key)
        } catch (e: Exception) {
            logger.error("Redis HASKEY失败: key={}", key, e)
            false
        }
    }
    
    /**
     * 获取key的剩余过期时间
     * 
     * ## 返回值说明
     * - 正数：剩余秒数
     * - -1：key存在但未设置过期时间
     * - -2：key不存在
     * 
     * ## 使用场景
     * - 检查缓存剩余时间
     * - 决定是否需要续期
     * 
     * ## 示例
     * ```kotlin
     * val ttl = redisUtil.getExpire("user:1")
     * when {
     *     ttl == -2L -> println("key不存在")
     *     ttl == -1L -> println("key永久有效")
     *     ttl < 60 -> println("即将过期，剩余${ttl}秒")
     * }
     * ```
     * 
     * @param key 缓存键
     * @return 剩余秒数，-1表示永久，-2表示不存在
     */
    fun getExpire(key: String): Long {
        return try {
            redisTemplate.getExpire(key, TimeUnit.SECONDS) ?: -2
        } catch (e: Exception) {
            logger.error("Redis GETEXPIRE失败: key={}", key, e)
            -2
        }
    }
    
    /**
     * 设置key的过期时间
     * 
     * ## 使用场景
     * - 为现有key设置过期时间
     * - 延长key的有效期
     * - 缩短key的有效期
     * 
     * ## 示例
     * ```kotlin
     * // 设置5分钟后过期
     * redisUtil.expire("user:1", 5, TimeUnit.MINUTES)
     * 
     * // 延长过期时间
     * if (redisUtil.getExpire("session:token") < 60) {
     *     redisUtil.expire("session:token", 10, TimeUnit.MINUTES)
     * }
     * ```
     * 
     * @param key 缓存键
     * @param timeout 过期时间数值
     * @param unit 时间单位
     * @return true-设置成功，false-key不存在或设置失败
     */
    fun expire(key: String, timeout: Long, unit: TimeUnit): Boolean {
        return try {
            if (timeout <= 0) {
                logger.warn("Redis EXPIRE: timeout <= 0: key={}", key)
                return false
            }
            val success = redisTemplate.expire(key, timeout, unit) ?: false
            if (success) {
                logger.debug("Redis EXPIRE成功: key={}, timeout={}({})", key, timeout, unit)
            } else {
                logger.debug("Redis EXPIRE失败(key可能不存在): key={}", key)
            }
            success
        } catch (e: Exception) {
            logger.error("Redis EXPIRE失败: key={}, timeout={}({})", key, timeout, unit, e)
            false
        }
    }
}
