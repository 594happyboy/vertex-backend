package com.zzy.file.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.zzy.file.entity.FileMetadata
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update

/**
 * 文件Mapper（重构版，手动管理软删除）
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
     * 软删除文件
     */
    @Update("""
        UPDATE file_metadata 
        SET deleted = 1, deleted_at = NOW(), update_time = NOW()
        WHERE id = #{id} AND deleted = 0
    """)
    fun softDelete(id: Long): Int
    
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
            WHERE deleted = 0 AND id IN
            <foreach collection="ids" item="id" open="(" close=")" separator=",">
                #{id}
            </foreach>
        </script>
    """)
    fun batchSoftDelete(@Param("ids") ids: List<Long>): Int
    
    /**
     * 恢复文件
     */
    @Update("""
        UPDATE file_metadata 
        SET deleted = 0, deleted_at = NULL, update_time = NOW()
        WHERE id = #{id} AND deleted = 1
    """)
    fun restore(id: Long): Int
    
    /**
     * 查询回收站文件列表（手动查询已删除的记录）
     */
    @Select("""
        SELECT * FROM file_metadata 
        WHERE user_id = #{userId} AND deleted = 1 AND deleted_at IS NOT NULL
        ORDER BY deleted_at DESC
        LIMIT #{offset}, #{limit}
    """)
    fun selectRecycleBinFiles(
        @Param("userId") userId: Long,
        @Param("offset") offset: Long,
        @Param("limit") limit: Long
    ): List<FileMetadata>
    
    /**
     * 游标分页查询回收站文件
     */
    @Select("""
        <script>
            SELECT * FROM file_metadata 
            WHERE user_id = #{userId} 
            AND deleted = 1 
            AND deleted_at IS NOT NULL
            <if test="lastId != null and lastDeletedAt != null">
                AND (
                    (deleted_at &lt; #{lastDeletedAt})
                    OR (deleted_at = #{lastDeletedAt} AND id &lt; #{lastId})
                )
            </if>
            ORDER BY deleted_at DESC, id DESC
            LIMIT #{limit}
        </script>
    """)
    fun selectRecycleBinWithCursor(
        @Param("userId") userId: Long,
        @Param("lastId") lastId: Long?,
        @Param("lastDeletedAt") lastDeletedAt: String?,
        @Param("limit") limit: Int
    ): List<FileMetadata>
    
    /**
     * 统计回收站文件数量
     */
    @Select("""
        SELECT COUNT(*) 
        FROM file_metadata 
        WHERE user_id = #{userId} AND deleted = 1 AND deleted_at IS NOT NULL
    """)
    fun countRecycleBinFiles(userId: Long): Long
    
    /**
     * 根据ID查询文件（包括已删除的文件）
     */
    @Select("""
        SELECT * FROM file_metadata 
        WHERE id = #{id}
    """)
    fun selectByIdIncludeDeleted(id: Long): FileMetadata?
    
    /**
     * 物理删除文件（真正的 DELETE 语句）
     */
    @Update("""
        DELETE FROM file_metadata 
        WHERE id = #{id}
    """)
    fun hardDelete(id: Long): Int
    
    /**
     * 游标分页查询文件（仅文件）
     * 注意：sortField 和 sortOrder 使用 ${} 进行字符串替换，需要在调用时确保参数安全
     */
    @Select("""
        <script>
            SELECT * FROM file_metadata 
            WHERE user_id = #{userId} 
            AND deleted = 0
            <if test="folderId != null">
                AND folder_id = #{folderId}
            </if>
            <if test="folderId == null">
                AND folder_id IS NULL
            </if>
            <if test="keyword != null and keyword != ''">
                AND file_name LIKE CONCAT('%', #{keyword}, '%')
            </if>
            <if test="lastId != null and lastSortValue != null">
                <choose>
                    <when test='sortOrder == "desc"'>
                        AND (
                            (${'$'}{sortField} &lt; #{lastSortValue}) 
                            OR (${'$'}{sortField} = #{lastSortValue} AND id &lt; #{lastId})
                        )
                    </when>
                    <otherwise>
                        AND (
                            (${'$'}{sortField} &gt; #{lastSortValue}) 
                            OR (${'$'}{sortField} = #{lastSortValue} AND id &gt; #{lastId})
                        )
                    </otherwise>
                </choose>
            </if>
            ORDER BY ${'$'}{sortField} ${'$'}{sortOrder}, id ${'$'}{sortOrder}
            LIMIT #{limit}
        </script>
    """)
    fun selectFilesWithCursor(
        @Param("userId") userId: Long,
        @Param("folderId") folderId: Long?,
        @Param("keyword") keyword: String?,
        @Param("sortField") sortField: String,
        @Param("sortOrder") sortOrder: String,
        @Param("lastId") lastId: Long?,
        @Param("lastSortValue") lastSortValue: String?,
        @Param("limit") limit: Int
    ): List<FileMetadata>
    
    /**
     * 增加引用计数
     */
    @Update("""
        UPDATE file_metadata 
        SET reference_count = reference_count + 1, 
            last_referenced_at = NOW() 
        WHERE id = #{fileId}
    """)
    fun incrementReferenceCount(@Param("fileId") fileId: Long): Int
    
    /**
     * 减少引用计数
     */
    @Update("""
        UPDATE file_metadata 
        SET reference_count = reference_count - 1 
        WHERE id = #{fileId} AND reference_count > 0
    """)
    fun decrementReferenceCount(@Param("fileId") fileId: Long): Int
    
    /**
     * 查询未被引用的文件
     */
    @Select("""
        SELECT * FROM file_metadata 
        WHERE deleted = FALSE 
        AND reference_count = 0 
        AND upload_time < #{threshold}
        ORDER BY upload_time ASC
        LIMIT 1000
    """)
    fun selectUnreferencedFiles(@Param("threshold") threshold: String): List<FileMetadata>
}
