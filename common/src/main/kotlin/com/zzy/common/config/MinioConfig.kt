package com.zzy.common.config

import io.minio.MinioClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * MinIO对象存储配置
 * 
 * MinIO 是一个高性能的对象存储服务，兼容Amazon S3 API
 * 用于存储用户上传的文件（图片、文档、视频等）
 * 
 * ## 使用场景
 * - 文件管理模块：存储用户上传的各类文件
 * - 博客模块：存储文档附件、图片等
 * 
 * ## 配置说明
 * - endpoint: MinIO服务地址
 * - accessKey: 访问密钥（类似用户名）
 * - secretKey: 安全密钥（类似密码）
 * 
 * ⚠️ 生产环境请修改默认的accessKey和secretKey
 * 
 * @author ZZY
 * @date 2025-10-09
 */
@Configuration
class MinioConfig {
    
    @Value("\${minio.endpoint}")
    private lateinit var endpoint: String
    
    @Value("\${minio.accessKey}")
    private lateinit var accessKey: String
    
    @Value("\${minio.secretKey}")
    private lateinit var secretKey: String
    
    /**
     * 创建MinIO客户端
     */
    @Bean
    fun minioClient(): MinioClient {
        return MinioClient.builder()
            .endpoint(endpoint)
            .credentials(accessKey, secretKey)
            .build()
    }
}

