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
        // Markdown 图片: ![](http://domain/api/files/download/3jSMLgKEIseog8trXtVAX)
        Pattern.compile("""!\[.*?\]\((.*?)\)"""),
        
        // Markdown 链接: [](http://domain/api/files/download/3jSMLgKEIseog8trXtVAX)
        Pattern.compile("""\[.*?\]\((.*?)\)"""),
        
        // HTML img标签: <img src="http://domain/api/files/download/3jSMLgKEIseog8trXtVAX">
        Pattern.compile("""<img[^>]+src=["'](.*?)["']""", Pattern.CASE_INSENSITIVE),
        
        // HTML a标签: <a href="http://domain/api/files/download/3jSMLgKEIseog8trXtVAX">
        Pattern.compile("""<a[^>]+href=["'](.*?)["']""", Pattern.CASE_INSENSITIVE)
    )
    
    /**
     * 文件URL匹配正则
     * 匹配文件服务URL模式，支持新旧两种格式：
     * 
     * 新格式（使用公开ID）：
     * - /api/files/download/3jSMLgKEIseog8trXtVAX
     * - /api/files/preview/3jSMLgKEIseog8trXtVAX
     * - http://domain/api/files/download/3jSMLgKEIseog8trXtVAX
     * 
     * 注意：仅匹配新格式URL，旧格式已不再支持
     */
    private val fileUrlPattern = Pattern.compile(
        """(?:https?://[^/]+)?/api/files/(?:download|preview|thumbnail)/([A-Za-z0-9_-]+)"""
    )
    
    /**
     * 从Markdown内容中提取所有文件公开ID
     * @param content MD内容
     * @return 文件公开ID集合（字符串）
     */
    fun extractFileIds(content: String?): Set<String> {
        if (content.isNullOrBlank()) {
            return emptySet()
        }
        
        val fileIds = mutableSetOf<String>()
        
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
     * 从URL中提取文件公开ID
     * @param url 文件URL
     * @return 文件公开ID（字符串），如果不匹配返回null
     */
    private fun extractFileIdFromUrl(url: String): String? {
        val matcher = fileUrlPattern.matcher(url)
        return if (matcher.find()) {
            matcher.group(1)
        } else {
            null
        }
    }
    
    /**
     * 检查内容中是否包含指定文件的引用
     * @param content MD内容
     * @param filePublicId 文件公开ID
     */
    fun containsFileReference(content: String?, filePublicId: String): Boolean {
        return extractFileIds(content).contains(filePublicId)
    }
}

