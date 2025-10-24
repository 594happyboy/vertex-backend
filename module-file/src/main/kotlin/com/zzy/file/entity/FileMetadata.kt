package com.zzy.file.entity

import com.baomidou.mybatisplus.annotation.*
import java.time.LocalDateTime

/**
 * 文件元数据实体类（重构版，支持文件夹）
 * @author ZZY
 * @date 2025-10-23
 */
@TableName("file_metadata")
data class FileMetadata(
    /** 文件ID */
    @TableId(type = IdType.AUTO)
    var id: Long? = null,
    
    /** 上传用户ID */
    var userId: Long,
    
    /** 所属文件夹ID（NULL表示根目录） */
    var folderId: Long? = null,
    
    /** 原始文件名 */
    var fileName: String? = null,
    
    /** 存储文件名(UUID) */
    var storedName: String? = null,
    
    /** 文件大小(字节) */
    var fileSize: Long? = null,
    
    /** 文件MIME类型 */
    var fileType: String? = null,
    
    /** 文件扩展名 */
    var fileExtension: String? = null,
    
    /** 文件存储路径 */
    var filePath: String? = null,
    
    /** 文件MD5值(用于秒传) */
    var fileMd5: String? = null,
    
    /** 下载次数 */
    var downloadCount: Int = 0,
    
    /** 文件标签（逗号分隔，如：工作,重要） */
    var tags: String? = null,
    
    /** 文件描述 */
    var description: String? = null,
    
    /** 是否删除（软删除） */
    @TableLogic
    var deleted: Boolean = false,
    
    /** 删除时间 */
    var deletedAt: LocalDateTime? = null,
    
    /** 上传时间 */
    var uploadTime: LocalDateTime? = null,
    
    /** 更新时间 */
    var updateTime: LocalDateTime? = null
)
