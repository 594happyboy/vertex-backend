package com.zzy.file.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

/**
 * 文件元数据实体类
 * @author ZZY
 * @date 2025-10-09
 */
@TableName("file_metadata")
data class FileMetadata(
    @TableId(type = IdType.AUTO)
    var id: Long? = null,
    
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
    
    /** 上传时间 */
    var uploadTime: LocalDateTime? = null,
    
    /** 更新时间 */
    var updateTime: LocalDateTime? = null,
    
    /** 状态(1:正常 0:已删除) */
    var status: Int = 1,
    
    /** 上传用户ID(可选) */
    var userId: Long? = null
)

