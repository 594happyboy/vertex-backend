package com.zzy.file.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.zzy.file.entity.FileMetadata
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update

/**
 * 文件Mapper（重构版）
 * @author ZZY
 * @date 2025-10-23
 */
@Mapper
interface FileMapper : BaseMapper<FileMetadata> {
    
    /**
     * 根据MD5查询文件（用于秒传）
     */
    @Select("""
        SELECT * FROM file_metadata 
        WHERE file_md5 = #{md5} AND deleted = 0
        LIMIT 1
    """)
    fun selectByMd5(md5: String): FileMetadata?
    
    /**
     * 增加下载次数
     */
    @Update("""
        UPDATE file_metadata 
        SET download_count = download_count + 1 
        WHERE id = #{id}
    """)
    fun increaseDownloadCount(id: Long): Int
    
    /**
     * 统计某个用户的文件总数
     */
    @Select("""
        SELECT COUNT(*) 
        FROM file_metadata 
        WHERE user_id = #{userId} AND deleted = 0
    """)
    fun countByUserId(userId: Long): Int
    
    /**
     * 统计某个用户的文件总大小
     */
    @Select("""
        SELECT COALESCE(SUM(file_size), 0) 
        FROM file_metadata 
        WHERE user_id = #{userId} AND deleted = 0
    """)
    fun sumSizeByUserId(userId: Long): Long
    
    /**
     * 查询某个用户的文件类型分布
     */
    @Select("""
        SELECT file_extension, COUNT(*) as count
        FROM file_metadata
        WHERE user_id = #{userId} AND deleted = 0
        GROUP BY file_extension
        ORDER BY count DESC
    """)
    fun getFileTypeDistribution(userId: Long): List<Map<String, Any>>
    
    /**
     * 批量移动文件到指定文件夹
     */
    @Update("""
        <script>
            UPDATE file_metadata 
            SET folder_id = #{targetFolderId}, update_time = NOW()
            WHERE id IN
            <foreach collection="fileIds" item="id" open="(" close=")" separator=",">
                #{id}
            </foreach>
        </script>
    """)
    fun batchMoveFiles(@Param("fileIds") fileIds: List<Long>, @Param("targetFolderId") targetFolderId: Long?): Int
    
    /**
     * 批量软删除文件
     */
    @Update("""
        <script>
            UPDATE file_metadata 
            SET deleted = 1, deleted_at = NOW(), update_time = NOW()
            WHERE id IN
            <foreach collection="ids" item="id" open="(" close=")" separator=",">
                #{id}
            </foreach>
        </script>
    """)
    fun batchSoftDelete(ids: List<Long>): Int
}
