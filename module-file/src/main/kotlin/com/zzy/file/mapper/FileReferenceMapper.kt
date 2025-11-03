package com.zzy.file.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.zzy.file.entity.FileReference
import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

/**
 * 文件引用关系 Mapper
 * @author ZZY
 * @date 2025-11-03
 */
@Mapper
interface FileReferenceMapper : BaseMapper<FileReference> {
    
    /**
     * 原子性地添加引用（避免重复）
     * 使用 INSERT IGNORE 确保线程安全，避免检查-插入的竞态条件
     * 
     * @return 插入的行数（0表示已存在，1表示成功插入）
     */
    @Insert("""
        INSERT IGNORE INTO file_references 
        (file_id, reference_type, reference_id, reference_field, created_at)
        VALUES (#{fileId}, #{referenceType}, #{referenceId}, #{referenceField}, NOW())
    """)
    fun insertReferenceIgnoreDuplicate(
        @Param("fileId") fileId: Long,
        @Param("referenceType") referenceType: String,
        @Param("referenceId") referenceId: Long,
        @Param("referenceField") referenceField: String?
    ): Int
}

