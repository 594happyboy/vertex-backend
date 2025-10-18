package com.zzy.blog.enums

/**
 * 文章内容类型枚举
 * @property value 类型值
 * @property description 类型描述
 */
enum class ContentType(val value: String, val description: String) {
    /**
     * Markdown 格式
     */
    MARKDOWN("markdown", "Markdown格式"),
    
    /**
     * PDF 格式
     */
    PDF("pdf", "PDF文档");
    
    companion object {
        /**
         * 根据值获取枚举
         */
        fun fromValue(value: String): ContentType? {
            return values().find { it.value == value }
        }
    }
}

