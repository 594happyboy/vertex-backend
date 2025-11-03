package com.zzy.file.schedule

import com.zzy.file.service.FileService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 文件清理定时任务
 * 每天凌晨2点执行，清理30天前删除的文件
 * 每天凌晨3点执行，清理7天前上传但无引用的文件
 * @author ZZY
 * @date 2025-10-20
 */
@Component
class FileCleanupScheduler(
    private val fileService: FileService,
    private val fileReferenceService: com.zzy.file.service.FileReferenceService
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
    
    /**
     * 清理未引用的文件
     * Cron表达式: 0 0 3 * * ?
     * 含义：每天凌晨3点执行
     * 
     * 清理规则：
     * - 只清理"系统"文件夹及其子文件夹下的文件（如系统/附件、系统/知识库等）
     * - 上传7天后仍无任何引用的文件会被清理
     * - 用户主动上传到普通文件夹的文件永久保留，不会被清理
     */
    @Scheduled(cron = "0 0 3 * * ?")
    fun cleanupUnreferencedFiles() {
        logger.info("========== 开始清理系统文件夹下的无引用文件 ==========")
        
        try {
            val gracePeriodDays = 7  // 宽限期7天
            val cleanedCount = fileReferenceService.cleanupUnreferencedFiles(gracePeriodDays)
            
            logger.info("========== 系统文件夹无引用文件清理完成: 清理了 {} 个文件 ==========", cleanedCount)
        } catch (e: Exception) {
            logger.error("无引用文件清理任务执行失败", e)
        }
    }
    
    /**
     * 手动触发无引用文件清理（用于测试）
     */
    fun manualCleanupUnreferenced(gracePeriodDays: Int = 7): Int {
        logger.info("手动触发无引用文件清理: gracePeriodDays={}", gracePeriodDays)
        return fileReferenceService.cleanupUnreferencedFiles(gracePeriodDays)
    }
}

