package com.zzy.blog.service

import com.zzy.blog.dto.BatchUploadJobStatus
import com.zzy.blog.dto.BatchUploadProgress
import com.zzy.blog.dto.BatchUploadProgressUpdate
import com.zzy.common.exception.ResourceNotFoundException
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 批量上传任务管理器，负责在内存中维护任务进度
 */
@Component
class BatchUploadJobManager {

    private val jobs = ConcurrentHashMap<String, BatchUploadJobRecord>()
    private val ttl = Duration.ofMinutes(60)

    fun createJob(userId: Long): BatchUploadProgress {
        cleanupExpiredJobs()
        val jobId = generateJobId()
        val progress = BatchUploadProgress(
            jobId = jobId,
            status = BatchUploadJobStatus.PENDING,
            message = "任务已创建，等待执行"
        )
        jobs[jobId] = BatchUploadJobRecord(userId = userId, progress = progress)
        return progress
    }

    fun updateProgress(jobId: String, update: BatchUploadProgressUpdate): BatchUploadProgress {
        val job = jobs[jobId] ?: throw ResourceNotFoundException("任务不存在或已过期")
        synchronized(job) {
            job.progress = job.progress.copy(
                status = update.status ?: job.progress.status,
                totalFiles = update.totalFiles ?: job.progress.totalFiles,
                totalFolders = update.totalFolders ?: job.progress.totalFolders,
                processedFiles = update.processedFiles ?: job.progress.processedFiles,
                successCount = update.successCount ?: job.progress.successCount,
                failedCount = update.failedCount ?: job.progress.failedCount,
                message = update.message ?: job.progress.message,
                result = update.result ?: job.progress.result
            )
            job.lastUpdated = Instant.now()
            return job.progress
        }
    }

    fun getJob(jobId: String, userId: Long): BatchUploadProgress {
        cleanupExpiredJobs()
        val job = jobs[jobId] ?: throw ResourceNotFoundException("任务不存在或已过期")
        if (job.userId != userId) {
            throw ResourceNotFoundException("任务不存在或已过期")
        }
        return job.progress
    }

    private fun cleanupExpiredJobs() {
        val now = Instant.now()
        val expireBefore = now.minus(ttl)
        jobs.entries.removeIf { (_, record) ->
            record.lastUpdated.isBefore(expireBefore)
        }
    }

    private fun generateJobId(): String {
        val timestamp = System.currentTimeMillis()
        val random = UUID.randomUUID().toString().substring(0, 8)
        return "$timestamp-$random"
    }

    private data class BatchUploadJobRecord(
        val userId: Long,
        @Volatile var progress: BatchUploadProgress,
        @Volatile var lastUpdated: Instant = Instant.now()
    )
}
