package com.zzy.common.config

import com.baomidou.mybatisplus.annotation.DbType
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor
import org.mybatis.spring.annotation.MapperScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * MyBatis-Plus ORM框架配置
 * 
 * ## 功能特性
 * - 自动分页：支持物理分页，自动处理count查询
 * - 乐观锁：防止并发更新冲突
 * - 代码生成：可通过代码生成器快速生成CRUD代码
 * - 通用CRUD：BaseMapper提供基础的增删改查方法
 * 
 * ## Mapper扫描
 * 统一扫描所有模块的Mapper接口：
 * - com.zzy.file.mapper：文件管理模块
 * - com.zzy.blog.mapper：博客管理模块
 * 
 * ## 注意事项
 * ⚠️ 本项目不使用MyBatis-Plus的@TableLogic自动逻辑删除
 * ⚠️ 所有软删除通过手动编写的softDelete()方法明确管理
 * 
 * @author ZZY
 * @date 2025-10-09
 */
@Configuration
@MapperScan(value = ["com.zzy.file.mapper", "com.zzy.blog.mapper"])
class MyBatisPlusConfig {
    
    /**
     * MyBatis-Plus拦截器配置
     * 
     * ## 包含的插件
     * 1. 分页插件：自动处理分页查询
     * 2. 乐观锁插件：支持@Version注解实现乐观锁
     */
    @Bean
    fun mybatisPlusInterceptor(): MybatisPlusInterceptor {
        val interceptor = MybatisPlusInterceptor()
        
        // 1. 分页插件（必须配置）
        interceptor.addInnerInterceptor(
            PaginationInnerInterceptor(DbType.MYSQL).apply {
                // 设置请求的页面大于最大页后操作，true返回首页，false继续请求
                isOverflow = false
                // 单页分页条数限制，默认无限制
                maxLimit = 1000L
            }
        )
        
        // 2. 乐观锁插件（可选）
        interceptor.addInnerInterceptor(OptimisticLockerInnerInterceptor())
        
        return interceptor
    }
}

