package com.zzy.blog.service

import com.zzy.blog.dto.BatchUploadJobCreatedResponse
import com.zzy.blog.dto.BatchUploadJobStatus
import com.zzy.blog.dto.BatchUploadProgress
import com.zzy.blog.dto.BatchUploadProgressUpdate
import com.zzy.blog.support.FileBackedMultipartFile
import com.zzy.common.context.AuthContextHolder
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.util.concurrent.Executor

/**
 * 批量上传异步任务服务
 */
@Service
class BatchUploadJobService(
    private val batchUploadService: BatchUploadService,
    private val jobManager: BatchUploadJobManager,
    @Qualifier("batchUploadExecutor") private val batchUploadExecutor: Executor
) {

    private val logger = LoggerFactory.getLogger(BatchUploadJobService::class.java)

    fun startAsyncUpload(file: MultipartFile, parentGroupId: Long?): BatchUploadJobCreatedResponse {
        val userId = AuthContextHolder.getCurrentUserId()
        batchUploadService.validateBatchUploadRequest(file, parentGroupId, userId)

        val tempZip = Files.createTempFile("batch-upload-job-", ".zip").toFile()
        try {
            file.transferTo(tempZip)
        } catch (ex: Exception) {
            tempZip.delete()
            throw ex
        }

        val job = jobManager.createJob(userId)
        val jobId = job.jobId

        try {
            batchUploadExecutor.execute {
                executeJob(
                    jobId = jobId,
                    tempFile = tempZip,
                    originalFilename = file.originalFilename,
                    contentType = file.contentType,
                    parentGroupId = parentGroupId,
                    userId = userId
                )
            }
        } catch (ex: Exception) {
            tempZip.delete()
            logger.error("提交批量上传任务失败: {}", jobId, ex)
            jobManager.updateProgress(
                jobId,
                BatchUploadProgressUpdate(
                    status = BatchUploadJobStatus.FAILED,
                    message = "任务提交失败: ${ex.message}"
                )
            )
            throw ex
        }

        return BatchUploadJobCreatedResponse(jobId)
    }

    fun getProgress(jobId: String): BatchUploadProgress {
        val userId = AuthContextHolder.getCurrentUserId()
        return jobManager.getJob(jobId, userId)
    }

    private fun executeJob(
        jobId: String,
        tempFile: java.io.File,
        originalFilename: String?,
        contentType: String?,
        parentGroupId: Long?,
        userId: Long
    ) {
        try {
            jobManager.updateProgress(
                jobId,
                BatchUploadProgressUpdate(
                    status = BatchUploadJobStatus.RUNNING,
                    message = "任务开始执行"
                )
            )

            val multipartFile = FileBackedMultipartFile(
                file = tempFile,
                originalFilename = originalFilename ?: tempFile.name,
                contentType = contentType
            )

            val response = batchUploadService.batchUploadWithProgress(
                file = multipartFile,
                parentGroupId = parentGroupId,
                userId = userId,
                progressCallback = BatchUploadProgressCallback { update ->
                    jobManager.updateProgress(jobId, update)
                }
            )

            jobManager.updateProgress(
                jobId,
                BatchUploadProgressUpdate(
                    status = BatchUploadJobStatus.COMPLETED,
                    message = response.message,
                    result = response
                )
            )
        } catch (ex: Exception) {
            logger.error("批量上传任务执行失败: {}", jobId, ex)
            jobManager.updateProgress(
                jobId,
                BatchUploadProgressUpdate(
                    status = BatchUploadJobStatus.FAILED,
                    message = ex.message ?: "批量上传失败"
                )
            )
        } finally {
            tempFile.delete()
        }
    }
}
