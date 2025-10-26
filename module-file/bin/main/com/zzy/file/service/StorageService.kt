package com.zzy.file.service

import io.minio.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.InputStream

/**
 * 文件存储服务 - MinIO
 * @author ZZY
 * @date 2025-10-09
 */
@Service
class StorageService(
    private val minioClient: MinioClient
) {
    
    private val logger = LoggerFactory.getLogger(StorageService::class.java)
    
    @Value("\${minio.bucket-name}")
    private lateinit var bucketName: String
    
    /**
     * 确保bucket存在
     */
    fun ensureBucketExists() {
        try {
            val exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucketName).build()
            )
            if (!exists) {
                minioClient.makeBucket(
                    MakeBucketArgs.builder().bucket(bucketName).build()
                )
                logger.info("创建bucket成功: {}", bucketName)
            }
        } catch (e: Exception) {
            logger.error("检查/创建bucket失败", e)
            throw RuntimeException("存储服务初始化失败", e)
        }
    }
    
    /**
     * 上传文件到MinIO
     */
    fun uploadFile(file: MultipartFile, storedName: String): String {
        try {
            ensureBucketExists()
            
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketName)
                    .`object`(storedName)
                    .stream(file.inputStream, file.size, -1)
                    .contentType(file.contentType)
                    .build()
            )
            
            logger.info("文件上传成功: {}", storedName)
            return storedName
        } catch (e: Exception) {
            logger.error("文件上传失败: {}", storedName, e)
            throw RuntimeException("文件上传失败", e)
        }
    }
    
    /**
     * 从MinIO下载文件
     */
    fun downloadFile(storedName: String): InputStream {
        try {
            return minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(bucketName)
                    .`object`(storedName)
                    .build()
            )
        } catch (e: Exception) {
            logger.error("文件下载失败: {}", storedName, e)
            throw RuntimeException("文件下载失败", e)
        }
    }
    
    /**
     * 从MinIO删除文件
     */
    fun deleteFile(storedName: String): Boolean {
        return try {
            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .`object`(storedName)
                    .build()
            )
            logger.info("文件删除成功: {}", storedName)
            true
        } catch (e: Exception) {
            logger.error("文件删除失败: {}", storedName, e)
            false
        }
    }
}

