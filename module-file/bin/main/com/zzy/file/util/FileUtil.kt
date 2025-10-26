package com.zzy.file.util

import cn.hutool.core.io.FileUtil as HutoolFileUtil
import cn.hutool.core.util.IdUtil
import cn.hutool.crypto.digest.DigestUtil
import org.springframework.web.multipart.MultipartFile
import java.io.IOException

/**
 * 文件工具类
 * @author ZZY
 * @date 2025-10-09
 */
object FileUtil {
    
    /**
     * 获取文件扩展名
     */
    fun getFileExtension(filename: String): String {
        return HutoolFileUtil.extName(filename)
    }
    
    /**
     * 生成唯一文件名
     */
    fun generateUniqueFileName(originalFilename: String): String {
        val extension = getFileExtension(originalFilename)
        val uuid = IdUtil.simpleUUID()
        return if (extension.isNotEmpty()) {
            "$uuid.$extension"
        } else {
            uuid
        }
    }
    
    /**
     * 计算文件MD5
     */
    fun calculateMd5(file: MultipartFile): String {
        return try {
            DigestUtil.md5Hex(file.inputStream)
        } catch (e: IOException) {
            throw RuntimeException("计算文件MD5失败", e)
        }
    }
    
    /**
     * 验证文件类型
     */
    fun validateFileType(filename: String, allowedTypes: List<String>): Boolean {
        if(allowedTypes.contains("*")) {
            return true
        }
        val extension = getFileExtension(filename).lowercase()
        return allowedTypes.contains(extension)
    }
    
    /**
     * 验证文件大小
     */
    fun validateFileSize(fileSize: Long, maxSize: Long): Boolean {
        return fileSize <= maxSize
    }
    
    /**
     * 格式化文件大小
     */
    fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format("%.2f KB", size / 1024.0)
            size < 1024 * 1024 * 1024 -> String.format("%.2f MB", size / (1024.0 * 1024))
            else -> String.format("%.2f GB", size / (1024.0 * 1024 * 1024))
        }
    }
}

