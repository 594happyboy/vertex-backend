package com.zzy.file.schedule

import com.zzy.file.service.FileService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 文件清理定时任务
 * 每天凌晨2点执行，清理30天前删除的文件
 * @author ZZY
 * @date 2025-10-20
 */
@Component
class FileCleanupScheduler(
    private val fileService: FileService
) {
    
    private val logger = LoggerFactory.getLogger(FileCleanupScheduler::class.java)
    
    /**
     * 清理过期文件
     * Cron表达式: 0 0 2 * * ? 
     * 含义：每天凌晨2点执行
     */
    @Scheduled(cron = "0 0 2 * * ?")
    fun cleanupExpiredFiles() {
        logger.info("========== 开始执行文件清理任务 ==========")
        
        try {
            val retentionDays = 30  // 保留30天
            val cleanedCount = fileService.cleanupExpiredFiles(retentionDays)
            
            logger.info("========== 文件清理任务完成: 清理了 {} 个文件 ==========", cleanedCount)
        } catch (e: Exception) {
            logger.error("文件清理任务执行失败", e)
        }
    }
    
    /**
     * 手动触发清理（用于测试）
     * 注意：生产环境应该通过管理接口调用，而不是定时任务
     */
    fun manualCleanup(retentionDays: Int = 30): Int {
        logger.info("手动触发文件清理: retentionDays={}", retentionDays)
        return fileService.cleanupExpiredFiles(retentionDays)
    }
}

