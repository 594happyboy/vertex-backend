package com.zzy.file.constants

/**
 * 文件模块常量定义
 * @author ZZY
 * @date 2025-11-02
 */
object FileConstants {
    
    /** 缓存相关 */
    object Cache {
        const val KEY_FILE_LIST = "file:list:"
        const val KEY_FILE_INFO = "file:info:"
        const val KEY_FOLDER_TREE = "folder:tree:"
        const val KEY_FOLDER_INFO = "folder:info:"
        const val KEY_FOLDER_PATH = "folder:path:"
        const val KEY_ROOT_INFO = "folder:explorer:root:"
        
        const val EXPIRE_SHORT = 300L  // 5分钟
        const val EXPIRE_LONG = 600L   // 10分钟
    }
    
    /** 分页相关 */
    object Pagination {
        const val DEFAULT_LIMIT = 50
        const val MAX_LIMIT = 200
        const val MIN_LIMIT = 1
    }
    
    /** 文件管理相关 */
    object FileManagement {
        const val RETENTION_DAYS = 30  // 回收站保留天数
        const val DEFAULT_SORT_FIELD = "upload_time"
    }
    
    /** 排序字段映射 */
    object SortFields {
        val FILE_FIELDS = mapOf(
            "uploadTime" to "upload_time",
            "updateTime" to "update_time",
            "fileName" to "file_name",
            "fileSize" to "file_size",
            "downloadCount" to "download_count",
            "name" to "file_name",
            "size" to "file_size",
            "updatedAt" to "update_time",
            "id" to "id"
        )
        
        val FOLDER_FIELDS = mapOf(
            "name" to "name",
            "updatedAt" to "updated_at",
            "createdAt" to "created_at"
        )
    }
    
    /** 可预览的文件扩展名 */
    val PREVIEWABLE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "gif", "bmp", "webp",
        "pdf", "txt", "md", "json", "xml", "html"
    )
}

