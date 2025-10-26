package com.zzy.blog.config

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler
import org.apache.ibatis.reflection.MetaObject
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * MyBatis-Plus 自动填充处理器
 * @author ZZY
 * @date 2025-10-18
 */
@Component
class MyBatisPlusHandler : MetaObjectHandler {
    
    /**
     * 插入时自动填充
     */
    override fun insertFill(metaObject: MetaObject) {
        val now = LocalDateTime.now()
        
        // 自动填充创建时间
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime::class.java, now)
        
        // 自动填充更新时间
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime::class.java, now)
    }
    
    /**
     * 更新时自动填充
     */
    override fun updateFill(metaObject: MetaObject) {
        // 自动填充更新时间
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime::class.java, LocalDateTime.now())
    }
}

