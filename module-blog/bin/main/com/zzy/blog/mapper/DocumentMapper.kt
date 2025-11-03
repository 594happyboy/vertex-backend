package com.zzy.blog.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.zzy.blog.entity.Document
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select

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
    
    /**
     * 游标分页查询文档（按标题升序）
     */
    @Select("""
        <script>
            SELECT * FROM documents
            WHERE user_id = #{userId}
            AND deleted = 0
            <if test="groupId != null">
                AND group_id = #{groupId}
            </if>
            <if test="keyword != null and keyword != ''">
                AND title LIKE CONCAT('%', #{keyword}, '%')
            </if>
            <if test="lastId != null and lastSortValue != null">
                AND (title > #{lastSortValue} OR (title = #{lastSortValue} AND id > #{lastId}))
            </if>
            ORDER BY title ASC, id ASC
            LIMIT #{limit}
        </script>
    """)
    fun selectDocumentsByTitleAsc(
        @Param("userId") userId: Long,
        @Param("groupId") groupId: Long?,
        @Param("keyword") keyword: String?,
        @Param("lastId") lastId: Long?,
        @Param("lastSortValue") lastSortValue: String?,
        @Param("limit") limit: Int
    ): List<Document>
    
    /**
     * 游标分页查询文档（按标题降序）
     */
    @Select("""
        <script>
            SELECT * FROM documents
            WHERE user_id = #{userId}
            AND deleted = 0
            <if test="groupId != null">
                AND group_id = #{groupId}
            </if>
            <if test="keyword != null and keyword != ''">
                AND title LIKE CONCAT('%', #{keyword}, '%')
            </if>
            <if test="lastId != null and lastSortValue != null">
                AND (title &lt; #{lastSortValue} OR (title = #{lastSortValue} AND id > #{lastId}))
            </if>
            ORDER BY title DESC, id ASC
            LIMIT #{limit}
        </script>
    """)
    fun selectDocumentsByTitleDesc(
        @Param("userId") userId: Long,
        @Param("groupId") groupId: Long?,
        @Param("keyword") keyword: String?,
        @Param("lastId") lastId: Long?,
        @Param("lastSortValue") lastSortValue: String?,
        @Param("limit") limit: Int
    ): List<Document>
    
    /**
     * 游标分页查询文档（按创建时间升序）
     */
    @Select("""
        <script>
            SELECT * FROM documents
            WHERE user_id = #{userId}
            AND deleted = 0
            <if test="groupId != null">
                AND group_id = #{groupId}
            </if>
            <if test="keyword != null and keyword != ''">
                AND title LIKE CONCAT('%', #{keyword}, '%')
            </if>
            <if test="lastId != null and lastSortValue != null">
                AND (created_at > #{lastSortValue} OR (created_at = #{lastSortValue} AND id > #{lastId}))
            </if>
            ORDER BY created_at ASC, id ASC
            LIMIT #{limit}
        </script>
    """)
    fun selectDocumentsByCreatedAtAsc(
        @Param("userId") userId: Long,
        @Param("groupId") groupId: Long?,
        @Param("keyword") keyword: String?,
        @Param("lastId") lastId: Long?,
        @Param("lastSortValue") lastSortValue: String?,
        @Param("limit") limit: Int
    ): List<Document>
    
    /**
     * 游标分页查询文档（按创建时间降序）
     */
    @Select("""
        <script>
            SELECT * FROM documents
            WHERE user_id = #{userId}
            AND deleted = 0
            <if test="groupId != null">
                AND group_id = #{groupId}
            </if>
            <if test="keyword != null and keyword != ''">
                AND title LIKE CONCAT('%', #{keyword}, '%')
            </if>
            <if test="lastId != null and lastSortValue != null">
                AND (created_at &lt; #{lastSortValue} OR (created_at = #{lastSortValue} AND id > #{lastId}))
            </if>
            ORDER BY created_at DESC, id ASC
            LIMIT #{limit}
        </script>
    """)
    fun selectDocumentsByCreatedAtDesc(
        @Param("userId") userId: Long,
        @Param("groupId") groupId: Long?,
        @Param("keyword") keyword: String?,
        @Param("lastId") lastId: Long?,
        @Param("lastSortValue") lastSortValue: String?,
        @Param("limit") limit: Int
    ): List<Document>
    
    /**
     * 游标分页查询文档（按更新时间升序）
     */
    @Select("""
        <script>
            SELECT * FROM documents
            WHERE user_id = #{userId}
            AND deleted = 0
            <if test="groupId != null">
                AND group_id = #{groupId}
            </if>
            <if test="keyword != null and keyword != ''">
                AND title LIKE CONCAT('%', #{keyword}, '%')
            </if>
            <if test="lastId != null and lastSortValue != null">
                AND (updated_at > #{lastSortValue} OR (updated_at = #{lastSortValue} AND id > #{lastId}))
            </if>
            ORDER BY updated_at ASC, id ASC
            LIMIT #{limit}
        </script>
    """)
    fun selectDocumentsByUpdatedAtAsc(
        @Param("userId") userId: Long,
        @Param("groupId") groupId: Long?,
        @Param("keyword") keyword: String?,
        @Param("lastId") lastId: Long?,
        @Param("lastSortValue") lastSortValue: String?,
        @Param("limit") limit: Int
    ): List<Document>
    
    /**
     * 游标分页查询文档（按更新时间降序）
     */
    @Select("""
        <script>
            SELECT * FROM documents
            WHERE user_id = #{userId}
            AND deleted = 0
            <if test="groupId != null">
                AND group_id = #{groupId}
            </if>
            <if test="keyword != null and keyword != ''">
                AND title LIKE CONCAT('%', #{keyword}, '%')
            </if>
            <if test="lastId != null and lastSortValue != null">
                AND (updated_at &lt; #{lastSortValue} OR (updated_at = #{lastSortValue} AND id > #{lastId}))
            </if>
            ORDER BY updated_at DESC, id ASC
            LIMIT #{limit}
        </script>
    """)
    fun selectDocumentsByUpdatedAtDesc(
        @Param("userId") userId: Long,
        @Param("groupId") groupId: Long?,
        @Param("keyword") keyword: String?,
        @Param("lastId") lastId: Long?,
        @Param("lastSortValue") lastSortValue: String?,
        @Param("limit") limit: Int
    ): List<Document>
    
    /**
     * 游标分页查询文档（默认排序）
     */
    @Select("""
        <script>
            SELECT * FROM documents
            WHERE user_id = #{userId}
            AND deleted = 0
            <if test="groupId != null">
                AND group_id = #{groupId}
            </if>
            <if test="keyword != null and keyword != ''">
                AND title LIKE CONCAT('%', #{keyword}, '%')
            </if>
            <if test="lastId != null">
                AND id > #{lastId}
            </if>
            ORDER BY sort_index ASC, created_at DESC, id ASC
            LIMIT #{limit}
        </script>
    """)
    fun selectDocumentsDefault(
        @Param("userId") userId: Long,
        @Param("groupId") groupId: Long?,
        @Param("keyword") keyword: String?,
        @Param("lastId") lastId: Long?,
        @Param("limit") limit: Int
    ): List<Document>
}

