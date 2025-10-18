package com.zzy.blog.enums

/**
 * 文章状态枚举
 * @property value 状态值
 * @property description 状态描述
 */
enum class ArticleStatus(val value: String, val description: String) {
    /**
     * 草稿
     */
    DRAFT("draft", "草稿"),
    
    /**
     * 已发布
     */
    PUBLISHED("published", "已发布");
    
    companion object {
        /**
         * 根据值获取枚举
         */
        fun fromValue(value: String): ArticleStatus? {
            return values().find { it.value == value }
        }
    }
}

