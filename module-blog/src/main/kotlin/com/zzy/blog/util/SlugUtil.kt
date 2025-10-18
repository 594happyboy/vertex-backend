package com.zzy.blog.util

import com.github.stuxuhai.jpinyin.PinyinHelper
import java.util.*

/**
 * Slug 生成工具类
 */
object SlugUtil {
    
    /**
     * 根据标题生成 URL 友好的 slug
     * @param title 标题
     * @return slug
     */
    fun generateSlug(title: String): String {
        if (title.isBlank()) {
            return UUID.randomUUID().toString().substring(0, 8)
        }
        
        // 1. 转换为拼音（中文转拼音）
        var slug = try {
            PinyinHelper.convertToPinyinString(title, "-", com.github.stuxuhai.jpinyin.PinyinFormat.WITHOUT_TONE)
        } catch (e: Exception) {
            title
        }
        
        // 2. 转换为小写
        slug = slug.lowercase(Locale.getDefault())
        
        // 3. 替换空格和特殊字符为连字符
        slug = slug.replace(Regex("[\\s_]+"), "-")
        
        // 4. 移除非字母数字和连字符的字符
        slug = slug.replace(Regex("[^a-z0-9\\-]"), "")
        
        // 5. 移除连续的连字符
        slug = slug.replace(Regex("-+"), "-")
        
        // 6. 移除首尾的连字符
        slug = slug.trim('-')
        
        // 7. 如果为空，使用 UUID
        if (slug.isBlank()) {
            slug = UUID.randomUUID().toString().substring(0, 8)
        }
        
        // 8. 限制长度
        if (slug.length > 100) {
            slug = slug.substring(0, 100).trim('-')
        }
        
        return slug
    }
    
    /**
     * 为 slug 添加唯一后缀
     * @param baseSlug 基础 slug
     * @param suffix 后缀（通常是数字）
     * @return 带后缀的 slug
     */
    fun addSuffix(baseSlug: String, suffix: Int): String {
        return "$baseSlug-$suffix"
    }
    
    /**
     * 验证 slug 是否合法
     * @param slug slug
     * @return 是否合法
     */
    fun isValidSlug(slug: String): Boolean {
        if (slug.isBlank()) {
            return false
        }
        // slug 只能包含小写字母、数字和连字符
        return slug.matches(Regex("^[a-z0-9]+(-[a-z0-9]+)*$"))
    }
}

