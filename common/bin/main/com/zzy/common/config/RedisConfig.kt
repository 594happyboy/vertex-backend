package com.zzy.common.config

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

/**
 * Redis配置类 - 完整的序列化和缓存解决方案
 * 
 * ## 核心概念
 * 
 * ### 1. 序列化与反序列化
 * - **序列化**：将Java/Kotlin对象转换为字节流，存储到Redis
 * - **反序列化**：将Redis中的字节流还原为Java/Kotlin对象
 * - **类型信息**：为了正确反序列化复杂对象（如List、Map），需要在JSON中存储类型信息（@class字段）
 * 
 * ### 2. 为什么需要类型信息？
 * ```json
 * // 没有类型信息的JSON（反序列化时不知道是ArrayList还是LinkedList）
 * ["item1", "item2"]
 * 
 * // 带有类型信息的JSON（明确知道类型）
 * ["java.util.ArrayList", [{"@class": "com.example.Item", "name": "item1"}]]
 * ```
 * 
 * ### 3. 本配置提供的Bean
 * - **objectMapper**: 通用的JSON处理器（不带类型信息，用于API响应）
 * - **redisTemplate**: Redis操作模板（带类型信息，用于缓存复杂对象）
 * - **stringRedisTemplate**: Redis字符串模板（适合手动序列化的场景）
 * - **cacheManager**: Spring Cache注解支持（@Cacheable等）
 * 
 * @author ZZY
 * @date 2025-10-20
 */
@Configuration
@EnableCaching
class RedisConfig {
    
    /**
     * 通用的ObjectMapper Bean - 用于API响应和普通JSON处理
     * 
     * ## 特点
     * - 不包含类型信息（@class），输出干净的JSON
     * - 支持Kotlin数据类
     * - 支持Java 8时间类型（LocalDateTime等）
     * - 忽略未知字段（向后兼容）
     * 
     * ## 使用场景
     * - Controller返回JSON响应
     * - 手动JSON序列化/反序列化
     * - 日志打印
     * 
     * ## 示例
     * ```kotlin
     * @Autowired
     * private lateinit var objectMapper: ObjectMapper
     * 
     * val json = objectMapper.writeValueAsString(user) // 序列化
     * val user = objectMapper.readValue(json, User::class.java) // 反序列化
     * ```
     */
    @Bean
    @Primary  // 默认的ObjectMapper，注入时优先使用
    fun objectMapper(): ObjectMapper = jacksonObjectMapper().apply {
        // 注册Java 8时间模块（支持LocalDateTime、LocalDate等）
        registerModule(JavaTimeModule())
        
        // === 序列化配置（对象 -> JSON） ===
        // 日期格式化为字符串而不是时间戳（易读性）
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        // 允许序列化空对象（避免空对象报错）
        disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
        
        // === 反序列化配置（JSON -> 对象） ===
        // 忽略JSON中存在但Java类中不存在的字段（向后兼容）
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        // 空字符串视为null对象（容错处理）
        enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
    }
    
    /**
     * RedisTemplate Bean - 用于存储复杂对象的Redis操作模板
     * 
     * ## 序列化策略
     * - **Key**: String序列化（所有key都是字符串）
     * - **Value**: JSON序列化 + 类型信息（确保反序列化正确）
     * 
     * ## 为什么Value需要类型信息？
     * 因为Redis存储的是字节流，反序列化时需要知道对象的确切类型：
     * - 集合类型：ArrayList vs LinkedList vs HashSet
     * - 接口实现：存储接口类型时，需要知道具体实现类
     * - 多态对象：父类引用指向子类对象
     * 
     * ## 类型信息格式
     * ```json
     * {
     *   "@class": "com.zzy.file.dto.FileListResponse",
     *   "total": 10,
     *   "files": ["java.util.ArrayList", [...]]
     * }
     * ```
     * 
     * ## 使用场景（通过RedisUtil使用）
     * - 缓存查询结果（列表、对象）
     * - 缓存计算结果
     * - 临时数据存储
     * 
     * ## 注意事项
     * ⚠️ 直接使用此Template会在Redis中存储类型信息
     * ⚠️ 类型信息会增加存储空间（约10-20%）
     * ⚠️ 推荐通过RedisUtil工具类使用，而不是直接注入
     */
    @Bean
    fun redisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, Any> {
        // 创建Redis专用的ObjectMapper（带类型信息）
        val redisObjectMapper = createRedisObjectMapper()
        
        return RedisTemplate<String, Any>().apply {
            // 设置连接工厂
            this.connectionFactory = connectionFactory
            
            // 创建序列化器
            val stringSerializer = StringRedisSerializer()  // 字符串序列化器
            val jsonSerializer = GenericJackson2JsonRedisSerializer(redisObjectMapper)  // JSON序列化器
            
            // === 配置序列化器 ===
            // Key使用String序列化（推荐：易读、易调试）
            keySerializer = stringSerializer
            // HashKey使用String序列化（Hash数据结构的field）
            hashKeySerializer = stringSerializer
            // Value使用JSON序列化（支持复杂对象）
            valueSerializer = jsonSerializer
            // HashValue使用JSON序列化（Hash数据结构的value）
            hashValueSerializer = jsonSerializer
            
            // 初始化配置
            afterPropertiesSet()
        }
    }
    
    /**
     * StringRedisTemplate Bean - 用于存储纯字符串的Redis操作模板
     * 
     * ## 序列化策略
     * - **Key**: String序列化
     * - **Value**: String序列化
     * 
     * ## 特点
     * - 所有数据都以字符串形式存储
     * - 需要手动序列化/反序列化对象
     * - 输出的JSON干净无类型信息
     * - 完全控制存储格式
     * 
     * ## 使用场景
     * - 存储简单的字符串（配置、状态）
     * - 手动序列化对象为JSON
     * - 需要与其他系统共享Redis数据
     * - 对存储格式有严格要求
     * 
     * ## 示例
     * ```kotlin
     * @Autowired
     * private lateinit var stringRedisTemplate: StringRedisTemplate
     * 
     * @Autowired
     * private lateinit var objectMapper: ObjectMapper
     * 
     * // 存储
     * val json = objectMapper.writeValueAsString(user)
     * stringRedisTemplate.opsForValue().set("user:1", json)
     * 
     * // 读取
     * val json = stringRedisTemplate.opsForValue().get("user:1")
     * val user = objectMapper.readValue(json, User::class.java)
     * ```
     */
    @Bean
    fun stringRedisTemplate(connectionFactory: RedisConnectionFactory): StringRedisTemplate =
        StringRedisTemplate(connectionFactory)
    
    /**
     * CacheManager Bean - Spring Cache注解支持
     * 
     * ## 功能
     * 支持Spring的缓存注解：
     * - @Cacheable: 查询时使用缓存
     * - @CachePut: 更新缓存
     * - @CacheEvict: 删除缓存
     * 
     * ## 配置
     * - 默认过期时间：30分钟
     * - Key序列化：String
     * - Value序列化：JSON + 类型信息
     * - 不缓存null值
     * 
     * ## 使用示例
     * ```kotlin
     * @Cacheable(value = ["users"], key = "#id")
     * fun getUserById(id: Long): User {
     *     return userMapper.selectById(id)
     * }
     * 
     * @CacheEvict(value = ["users"], key = "#id")
     * fun deleteUser(id: Long) {
     *     userMapper.deleteById(id)
     * }
     * ```
     * 
     * ## 注意事项
     * ⚠️ 缓存的方法必须是public
     * ⚠️ 同一个类内部调用不会触发缓存（Spring AOP限制）
     * ⚠️ 建议统一使用RedisUtil而不是混用注解
     */
    @Bean
    fun cacheManager(connectionFactory: RedisConnectionFactory): CacheManager {
        // 创建缓存专用的ObjectMapper（带类型信息）
        val cacheObjectMapper = createRedisObjectMapper()
        
        // 配置缓存
        val config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30))  // 默认过期时间：30分钟
            .serializeKeysWith(
                // Key序列化为字符串
                RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer())
            )
            .serializeValuesWith(
                // Value序列化为JSON + 类型信息
                RedisSerializationContext.SerializationPair.fromSerializer(
                    GenericJackson2JsonRedisSerializer(cacheObjectMapper)
                )
            )
            .disableCachingNullValues()  // 不缓存null值（避免缓存穿透问题需要单独处理）
        
        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            .transactionAware()  // 支持事务（缓存操作会在事务提交后执行）
            .build()
    }
    
    /**
     * 创建Redis专用的ObjectMapper（带类型信息）
     * 
     * ## 关键配置：activateDefaultTyping
     * 这个配置会在JSON中添加类型信息（@class字段）
     * 
     * ### 参数说明
     * 1. **LaissezFaireSubTypeValidator**: 宽松的类型验证器（允许所有类型）
     *    - 生产环境可以使用更严格的验证器（白名单机制）
     * 
     * 2. **DefaultTyping.NON_FINAL**: 为非final类型添加类型信息
     *    - JAVA_LANG_OBJECT: 仅Object类型
     *    - OBJECT_AND_NON_CONCRETE: Object和抽象类/接口
     *    - NON_CONCRETE_AND_ARRAYS: 抽象类/接口和数组
     *    - NON_FINAL: 所有非final类型（推荐）
     * 
     * 3. **JsonTypeInfo.As.PROPERTY**: 类型信息作为JSON属性存储
     *    - PROPERTY: {"@class":"...", "field":"value"}
     *    - WRAPPER_ARRAY: ["className", {...}]
     *    - WRAPPER_OBJECT: {"className": {...}}
     * 
     * ## 安全性考虑
     * ⚠️ 类型信息可能带来安全风险（反序列化漏洞）
     * ⚠️ 生产环境建议：
     *    1. 使用白名单验证器（BasicPolymorphicTypeValidator）
     *    2. 限制允许反序列化的包名
     *    3. 定期更新Jackson版本
     */
    private fun createRedisObjectMapper(): ObjectMapper = jacksonObjectMapper().apply {
        // 注册Java 8时间模块
        registerModule(JavaTimeModule())
        
        // 日期格式化
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        
        // 容错配置
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        
        // 🔑 关键配置：启用类型信息
        // 这使得Redis能够正确反序列化复杂对象（List、Map、多态对象等）
        activateDefaultTyping(
            LaissezFaireSubTypeValidator.instance,  // 类型验证器
            ObjectMapper.DefaultTyping.NON_FINAL,   // 为非final类型添加类型信息
            JsonTypeInfo.As.PROPERTY                // 类型信息作为JSON属性
        )
    }
}
