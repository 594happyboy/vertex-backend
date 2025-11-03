package com.zzy.file.util

import org.slf4j.LoggerFactory
import java.util.regex.Pattern

/**
 * Markdown文件引用提取器
 * 从MD内容中提取所有文件引用
 * @author ZZY
 * @date 2025-11-03
 */
object MarkdownFileExtractor {
    
    private val logger = LoggerFactory.getLogger(MarkdownFileExtractor::class.java)
    
    /**
     * MD图片语法正则：![alt](url)
     * MD链接语法正则：[text](url)
     * HTML img标签：<img src="url">
     */
    private val patterns = listOf(
        // Markdown 图片: ![](http://domain/api/files/123/download)
        Pattern.compile("""!\[.*?\]\((.*?)\)"""),
        
        // Markdown 链接: [](http://domain/api/files/123/download)
        Pattern.compile("""\[.*?\]\((.*?)\)"""),
        
        // HTML img标签: <img src="http://domain/api/files/123/download">
        Pattern.compile("""<img[^>]+src=["'](.*?)["']""", Pattern.CASE_INSENSITIVE),
        
        // HTML a标签: <a href="http://domain/api/files/123/download">
        Pattern.compile("""<a[^>]+href=["'](.*?)["']""", Pattern.CASE_INSENSITIVE)
    )
    
    /**
     * 文件URL匹配正则
     * 匹配文件服务URL模式，例如：
     * - http://domain/api/files/123/download
     * - https://domain/api/files/123/download
     * - /api/files/123/download (相对路径)
     */
    private val fileUrlPattern = Pattern.compile(
        """(?:https?://[^/]+)?/api/files/(\d+)(?:/download)?"""
    )
    
    /**
     * 从Markdown内容中提取所有文件ID
     * @param content MD内容
     * @return 文件ID集合
     */
    fun extractFileIds(content: String?): Set<Long> {
        if (content.isNullOrBlank()) {
            return emptySet()
        }
        
        val fileIds = mutableSetOf<Long>()
        
        // 遍历所有模式提取URL
        patterns.forEach { pattern ->
            val matcher = pattern.matcher(content)
            while (matcher.find()) {
                val url = matcher.group(1)
                extractFileIdFromUrl(url)?.let { fileIds.add(it) }
            }
        }
        
        logger.debug("从MD内容提取到 {} 个文件引用", fileIds.size)
        return fileIds
    }
    
    /**
     * 从URL中提取文件ID
     * @param url 文件URL
     * @return 文件ID，如果不匹配返回null
     */
    private fun extractFileIdFromUrl(url: String): Long? {
        val matcher = fileUrlPattern.matcher(url)
        return if (matcher.find()) {
            try {
                matcher.group(1).toLong()
            } catch (e: NumberFormatException) {
                logger.warn("解析文件ID失败: url={}", url)
                null
            }
        } else {
            null
        }
    }
    
    /**
     * 检查内容中是否包含指定文件的引用
     */
    fun containsFileReference(content: String?, fileId: Long): Boolean {
        return extractFileIds(content).contains(fileId)
    }
}

