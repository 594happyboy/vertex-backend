package com.zzy.file.util

/**
 * 文件大小格式化工具（单例）
 * @author ZZY
 * @date 2025-11-02
 */
object FileSizeFormatter {
    
    private const val KB = 1024L
    private const val MB = KB * 1024
    private const val GB = MB * 1024
    private const val TB = GB * 1024
    
    /**
     * 格式化文件大小为人类可读格式
     * @param size 文件大小（字节）
     * @return 格式化后的字符串，如 "1.5 MB"
     */
    fun format(size: Long): String = when {
        size < 0 -> "0 B"
        size < KB -> "$size B"
        size < MB -> String.format("%.2f KB", size / KB.toDouble())
        size < GB -> String.format("%.2f MB", size / MB.toDouble())
        size < TB -> String.format("%.2f GB", size / GB.toDouble())
        else -> String.format("%.2f TB", size / TB.toDouble())
    }
    
    /**
     * 解析格式化的文件大小为字节数
     * @param formattedSize 格式化的大小字符串，如 "1.5 MB"
     * @return 字节数
     */
    fun parse(formattedSize: String): Long? {
        val regex = """^([\d.]+)\s*([KMGT]?B)$""".toRegex(RegexOption.IGNORE_CASE)
        val match = regex.matchEntire(formattedSize.trim()) ?: return null
        
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        val unit = match.groupValues[2].uppercase()
        
        return when (unit) {
            "B" -> value.toLong()
            "KB" -> (value * KB).toLong()
            "MB" -> (value * MB).toLong()
            "GB" -> (value * GB).toLong()
            "TB" -> (value * TB).toLong()
            else -> null
        }
    }
}

