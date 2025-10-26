package com.zzy.blog.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.zzy.blog.entity.Document
import org.apache.ibatis.annotations.Mapper

/**
 * 文档 Mapper（手动管理软删除）
 * @author ZZY
 * @date 2025-10-18
 */
@Mapper
interface DocumentMapper : BaseMapper<Document> {
    
    /**
     * 软删除文档
     */
    @org.apache.ibatis.annotations.Update("""
        UPDATE documents 
        SET deleted = 1, updated_at = NOW()
        WHERE id = #{id} AND deleted = 0
    """)
    fun softDelete(id: Long): Int
    
    /**
     * 批量软删除文档
     */
    @org.apache.ibatis.annotations.Update("""
        <script>
            UPDATE documents 
            SET deleted = 1, updated_at = NOW()
            WHERE deleted = 0 AND id IN
            <foreach collection="ids" item="id" open="(" close=")" separator=",">
                #{id}
            </foreach>
        </script>
    """)
    fun batchSoftDelete(ids: List<Long>): Int
}

