package com.zzy.blog.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

/**
 * 评论实体类
 */
@TableName("blog_comments")
data class Comment(
    /**
     * 评论ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    var id: Long? = null,
    
    /**
     * 文章ID
     */
    var articleId: Long,
    
    /**
     * 昵称
     */
    var nickname: String = "访客",
    
    /**
     * 评论内容
     */
    var content: String,
    
    /**
     * 创建时间
     */
    var createdAt: LocalDateTime? = null
)

