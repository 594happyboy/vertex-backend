package com.zzy.file.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 日期格式化工具
 * @author ZZY
 * @date 2025-11-02
 */
object DateFormatter {
    
    /** 标准日期时间格式 */
    val STANDARD: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    
    /** ISO 8601 格式 */
    val ISO: DateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME
    
    /** 日期格式 */
    val DATE_ONLY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    
    /** 时间格式 */
    val TIME_ONLY: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    
    /**
     * 格式化日期时间为标准格式
     */
    fun format(dateTime: LocalDateTime?): String {
        return dateTime?.format(STANDARD) ?: ""
    }
    
    /**
     * 格式化日期时间为 ISO 格式
     */
    fun formatIso(dateTime: LocalDateTime?): String {
        return dateTime?.format(ISO) ?: ""
    }
}

