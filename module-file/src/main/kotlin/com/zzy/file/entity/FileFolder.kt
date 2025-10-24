package com.zzy.file.entity

import com.baomidou.mybatisplus.annotation.*
import java.time.LocalDateTime

/**
 * 文件夹实体类（支持树形结构）
 * @author ZZY
 * @date 2025-10-23
 */
@TableName("file_folders")
data class FileFolder(
    /** 文件夹ID */
    @TableId(type = IdType.AUTO)
    var id: Long? = null,
    
    /** 用户ID */
    var userId: Long,
    
    /** 文件夹名称 */
    var name: String,
    
    /** 父文件夹ID（NULL表示根目录） */
    var parentId: Long? = null,
    
    /** 排序索引 */
    var sortIndex: Int = 0,
    
    /** 文件夹颜色标记（前端展示，如：#FF5722） */
    var color: String? = null,
    
    /** 文件夹描述 */
    var description: String? = null,
    
    /** 是否删除（软删除） */
    @TableLogic
    var deleted: Boolean = false,
    
    /** 删除时间 */
    var deletedAt: LocalDateTime? = null,
    
    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    var createdAt: LocalDateTime? = null,
    
    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    var updatedAt: LocalDateTime? = null
) {
    /** 子文件夹列表（不映射到数据库，用于构建树形结构） */
    @TableField(exist = false)
    var children: MutableList<FileFolder>? = null
    
    /** 文件数量（不映射到数据库，统计信息） */
    @TableField(exist = false)
    var fileCount: Int? = null
    
    /** 子文件夹数量（不映射到数据库，统计信息） */
    @TableField(exist = false)
    var subFolderCount: Int? = null
    
    /** 总大小（不映射到数据库，统计信息） */
    @TableField(exist = false)
    var totalSize: Long? = null
}

