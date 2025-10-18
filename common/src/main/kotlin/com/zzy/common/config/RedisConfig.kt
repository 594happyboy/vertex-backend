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
 * Redis配置 - 通用配置，支持Kotlin数据类
 * @author ZZY
 * @date 2025-10-09
 */
@Configuration
@EnableCaching
class RedisConfig {
    
    /**
     * 通用的ObjectMapper Bean，供整个应用使用
     * 配置完善的Kotlin和Java时间类型支持
     * 输出干净的JSON，不包含类型信息
     */
    @Bean
    @Primary
    fun objectMapper(): ObjectMapper = jacksonObjectMapper().apply {
        // 注册Java 8时间模块
        registerModule(JavaTimeModule())
        
        // 序列化配置 - 干净的JSON输出
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)  // 日期格式化而非时间戳
        disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)        // 允许空对象
        
        // 反序列化配置 - 宽松模式
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)  // 忽略未知字段
        enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)  // 空字符串视为null
    }
    
    /**
     * RedisTemplate<String, Any> - 用于存储复杂对象
     * 使用带类型信息的JSON序列化，确保反序列化正确
     * 注意：此Template会在Redis中存储类型信息，推荐使用StringRedisTemplate
     */
    @Bean
    fun redisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, Any> {
        // 为Redis创建独立的ObjectMapper，带类型信息以支持反序列化
        val redisObjectMapper = jacksonObjectMapper().apply {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            
            // Redis专用：启用类型信息以支持正确的反序列化
            activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
            )
        }
        
        return RedisTemplate<String, Any>().apply {
            this.connectionFactory = connectionFactory
            
            val stringSerializer = StringRedisSerializer()
            val jsonSerializer = GenericJackson2JsonRedisSerializer(redisObjectMapper)
            
            keySerializer = stringSerializer
            hashKeySerializer = stringSerializer
            valueSerializer = jsonSerializer
            hashValueSerializer = jsonSerializer
            
            afterPropertiesSet()
        }
    }
    
    /**
     * StringRedisTemplate - 推荐使用 ⭐
     * 存储纯字符串，配合ObjectMapper手动序列化
     * 优势：完全控制JSON格式，输出干净无类型信息
     */
    @Bean
    fun stringRedisTemplate(connectionFactory: RedisConnectionFactory): StringRedisTemplate =
        StringRedisTemplate(connectionFactory)
    
    /**
     * CacheManager - Spring Cache注解支持
     * 配置统一的缓存策略
     */
    @Bean
    fun cacheManager(
        connectionFactory: RedisConnectionFactory,
        objectMapper: ObjectMapper
    ): CacheManager {
        val config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30))
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer())
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    GenericJackson2JsonRedisSerializer(objectMapper)
                )
            )
            .disableCachingNullValues()
        
        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            .transactionAware()
            .build()
    }
}

