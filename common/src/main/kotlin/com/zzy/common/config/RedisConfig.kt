package com.zzy.common.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer

/**
 * Redis配置
 * @author ZZY
 * @date 2025-10-09
 */
@Configuration
class RedisConfig {
    
    @Bean
    fun redisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, Any> {
        val template = RedisTemplate<String, Any>()
        template.connectionFactory = connectionFactory
        
        // 配置ObjectMapper以支持Kotlin和Java时间类型
        val objectMapper = ObjectMapper().apply {
            // 注册Kotlin模块
            registerModule(KotlinModule.Builder().build())
            // 注册Java 8时间模块
            registerModule(JavaTimeModule())
            // 禁用将日期写为时间戳
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
        
        // 使用GenericJackson2JsonRedisSerializer，支持带类型信息的序列化
        // 能够正确处理复杂对象的序列化和反序列化
        val jsonSerializer = GenericJackson2JsonRedisSerializer(objectMapper)
        
        // 使用StringRedisSerializer来序列化和反序列化redis的key值
        val stringRedisSerializer = StringRedisSerializer()
        
        // key采用String的序列化方式
        template.keySerializer = stringRedisSerializer
        // hash的key也采用String的序列化方式
        template.hashKeySerializer = stringRedisSerializer
        // value序列化方式采用jackson（带类型信息）
        template.valueSerializer = jsonSerializer
        // hash的value序列化方式采用jackson
        template.hashValueSerializer = jsonSerializer
        
        template.afterPropertiesSet()
        return template
    }
}

