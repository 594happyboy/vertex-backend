package com.zzy.blog.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor

/**
 * 异步任务配置
 * 用于处理文件引用同步等异步任务
 * @author ZZY
 * @date 2025-11-03
 */
@Configuration
@EnableAsync
class AsyncConfig {
    
    /**
     * 文件引用异步任务执行器
     */
    @Bean("fileReferenceExecutor")
    fun fileReferenceExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        
        // 核心线程数
        executor.corePoolSize = 4
        
        // 最大线程数
        executor.maxPoolSize = 8
        
        // 队列容量
        executor.queueCapacity = 100
        
        // 线程名称前缀
        executor.setThreadNamePrefix("file-ref-async-")
        
        // 拒绝策略：由调用线程执行（降级处理）
        executor.setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
        
        // 线程空闲时间（秒）
        executor.keepAliveSeconds = 60
        
        // 允许核心线程超时
        executor.setAllowCoreThreadTimeOut(true)
        
        // 等待所有任务完成后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true)
        
        // 等待时间（秒）
        executor.setAwaitTerminationSeconds(60)
        
        executor.initialize()
        return executor
    }

    /**
     * 批量上传任务执行器
     */
    @Bean("batchUploadExecutor")
    fun batchUploadExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 2
        executor.maxPoolSize = 4
        executor.queueCapacity = 50
        executor.setThreadNamePrefix("batch-upload-async-")
        executor.setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
        executor.keepAliveSeconds = 60
        executor.setAllowCoreThreadTimeOut(false)
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.setAwaitTerminationSeconds(60)
        executor.initialize()
        return executor
    }
}
