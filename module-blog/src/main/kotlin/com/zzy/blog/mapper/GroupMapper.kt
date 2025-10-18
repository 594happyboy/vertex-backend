package com.zzy.blog.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.zzy.blog.entity.Group
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Select

/**
 * 分组 Mapper
 */
@Mapper
interface GroupMapper : BaseMapper<Group> {
    
    /**
     * 查询分组及其文章数量
     */
    @Select("""
        SELECT g.*, COUNT(a.id) as article_count 
        FROM blog_groups g 
        LEFT JOIN blog_articles a ON g.id = a.group_id AND a.status = 'published'
        GROUP BY g.id 
        ORDER BY g.order_index
    """)
    fun selectGroupsWithArticleCount(): List<Map<String, Any>>
}

