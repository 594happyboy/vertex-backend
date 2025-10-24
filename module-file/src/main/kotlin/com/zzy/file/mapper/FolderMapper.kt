package com.zzy.file.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.zzy.file.entity.FileFolder
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update

/**
 * 文件夹Mapper
 * @author ZZY
 * @date 2025-10-23
 */
@Mapper
interface FolderMapper : BaseMapper<FileFolder> {
    
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
}

/**
 * 用于批量更新排序索引的辅助类
 */
data class FolderSortItem(
    val id: Long,
    val sortIndex: Int
)

