package com.zzy.common.config

import com.baomidou.mybatisplus.annotation.DbType
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor
import org.mybatis.spring.annotation.MapperScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * MyBatis-Plus配置
 * 统一数据库配置，扫描所有模块的 Mapper
 * @author ZZY
 * @date 2025-10-09
 */
@Configuration
@MapperScan(value = ["com.zzy.file.mapper", "com.zzy.blog.mapper"])
class MyBatisPlusConfig {
    
    /**
     * 分页插件
     */
    @Bean
    fun mybatisPlusInterceptor(): MybatisPlusInterceptor {
        val interceptor = MybatisPlusInterceptor()
        interceptor.addInnerInterceptor(PaginationInnerInterceptor(DbType.MYSQL))
        return interceptor
    }
}

