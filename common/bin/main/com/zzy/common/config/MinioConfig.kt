package com.zzy.common.config

import io.minio.MinioClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * MinIO配置
 * @author ZZY
 * @date 2025-10-09
 */
@Configuration
class MinioConfig {
    
    @Value("\${minio.endpoint}")
    private lateinit var endpoint: String
    
    @Value("\${minio.access-key}")
    private lateinit var accessKey: String
    
    @Value("\${minio.secret-key}")
    private lateinit var secretKey: String
    
    @Bean
    fun minioClient(): MinioClient {
        return MinioClient.builder()
            .endpoint(endpoint)
            .credentials(accessKey, secretKey)
            .build()
    }
}

