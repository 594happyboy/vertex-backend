package com.zzy.file.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.zzy.file.entity.FileFolder
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update

/**
 * 文件夹Mapper（手动管理软删除）
 * @author ZZY
 * @date 2025-10-23
 */
@Mapper
interface FolderMapper : BaseMapper<FileFolder> {
    
    /**
     * 通过公开ID查询文件夹
     */
    @Select("""
        SELECT * FROM file_folders 
        WHERE public_id = #{publicId}
    """)
    fun selectByPublicId(publicId: String): FileFolder?
    
    /**
     * 检查公开ID是否存在
     */
    @Select("""
        SELECT COUNT(*) FROM file_folders 
        WHERE public_id = #{publicId}
    """)
    fun existsByPublicId(publicId: String): Int
    
    /**
     * 批量查询（通过公开ID列表）
     */
    @Select("""
        <script>
            SELECT * FROM file_folders 
            WHERE public_id IN
            <foreach collection="publicIds" item="publicId" open="(" close=")" separator=",">
                #{publicId}
            </foreach>
        </script>
    """)
    fun selectBatchByPublicIds(@Param("publicIds") publicIds: List<String>): List<FileFolder>
    
    /**
     * 软删除文件夹
     */
    @Update("""
        UPDATE file_folders 
        SET deleted = 1, deleted_at = NOW(), updated_at = NOW()
        WHERE id = #{id} AND deleted = 0
    """)
    fun softDelete(id: Long): Int
    
    /**
     * 批量软删除文件夹
     */
    @Update("""
        <script>
            UPDATE file_folders 
            SET deleted = 1, deleted_at = NOW(), updated_at = NOW()
            WHERE deleted = 0 AND id IN
            <foreach collection="ids" item="id" open="(" close=")" separator=",">
                #{id}
            </foreach>
        </script>
    """)
    fun batchSoftDelete(@Param("ids") ids: List<Long>): Int
    
    /**
     * 查询某个文件夹下的文件数量
     */
    @Select("""
        SELECT COUNT(*) 
        FROM file_metadata 
        WHERE folder_id = #{folderId} AND deleted = 0
    """)
    fun countFilesByFolderId(folderId: Long): Int
    
    /**
     * 查询某个文件夹下的子文件夹数量
     */
    @Select("""
        SELECT COUNT(*) 
        FROM file_folders 
        WHERE parent_id = #{folderId} AND deleted = 0
    """)
    fun countSubFoldersByFolderId(folderId: Long): Int
    
    /**
     * 查询某个文件夹及其所有子文件夹中的文件总大小
     */
    @Select("""
        WITH RECURSIVE folder_tree AS (
            SELECT id FROM file_folders WHERE id = #{folderId} AND deleted = 0
            UNION ALL
            SELECT f.id FROM file_folders f
            INNER JOIN folder_tree ft ON f.parent_id = ft.id
            WHERE f.deleted = 0
        )
        SELECT COALESCE(SUM(fm.file_size), 0)
        FROM file_metadata fm
        WHERE fm.folder_id IN (SELECT id FROM folder_tree) 
        AND fm.deleted = 0
    """)
    fun calculateTotalSize(folderId: Long): Long
    
    /**
     * 查询某个用户在某个父文件夹下的最大排序索引
     */
    @Select("""
        SELECT COALESCE(MAX(sort_index), -1)
        FROM file_folders
        WHERE user_id = #{userId} 
        AND (parent_id = #{parentId} OR (parent_id IS NULL AND #{parentId} IS NULL))
        AND deleted = 0
    """)
    fun getMaxSortIndex(@Param("userId") userId: Long, @Param("parentId") parentId: Long?): Int
    
    /**
     * 批量更新排序索引
     */
    @Update("""
        <script>
            <foreach collection="items" item="item" separator=";">
                UPDATE file_folders 
                SET sort_index = #{item.sortIndex}, updated_at = NOW() 
                WHERE id = #{item.id}
            </foreach>
        </script>
    """)
    fun batchUpdateSortIndex(items: List<FolderSortItem>): Int
    
    /**
     * 递归查询某个文件夹的所有子文件夹ID（包括自己）
     */
    @Select("""
        WITH RECURSIVE folder_tree AS (
            SELECT id FROM file_folders WHERE id = #{folderId} AND deleted = 0
            UNION ALL
            SELECT f.id FROM file_folders f
            INNER JOIN folder_tree ft ON f.parent_id = ft.id
            WHERE f.deleted = 0
        )
        SELECT id FROM folder_tree
    """)
    fun getDescendantIds(folderId: Long): List<Long>
    
    /**
     * 查询根目录下的子文件夹数量
     */
    @Select("""
        SELECT COUNT(*) 
        FROM file_folders 
        WHERE user_id = #{userId} AND parent_id IS NULL AND deleted = 0
    """)
    fun countRootFolders(userId: Long): Int
    
    /**
     * 查询根目录下的文件数量
     */
    @Select("""
        SELECT COUNT(*) 
        FROM file_metadata 
        WHERE user_id = #{userId} AND folder_id IS NULL AND deleted = 0
    """)
    fun countRootFiles(userId: Long): Int
    
    /**
     * 游标分页查询子文件夹（仅文件夹）
     * 注意：sortField 和 sortOrder 使用 ${} 进行字符串替换，需要在调用时确保参数安全
     */
    @Select("""
        <script>
            SELECT * FROM file_folders 
            WHERE user_id = #{userId} 
            AND deleted = 0
            <if test="parentId != null">
                AND parent_id = #{parentId}
            </if>
            <if test="parentId == null">
                AND parent_id IS NULL
            </if>
            <if test="keyword != null and keyword != ''">
                AND name LIKE CONCAT('%', #{keyword}, '%')
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
    fun selectFoldersWithCursor(
        @Param("userId") userId: Long,
        @Param("parentId") parentId: Long?,
        @Param("keyword") keyword: String?,
        @Param("sortField") sortField: String,
        @Param("sortOrder") sortOrder: String,
        @Param("lastId") lastId: Long?,
        @Param("lastSortValue") lastSortValue: String?,
        @Param("limit") limit: Int
    ): List<FileFolder>
}

/**
 * 用于批量更新排序索引的辅助类
 */
data class FolderSortItem(
    val id: Long,
    val sortIndex: Int
)

