package com.zzy.blog.dto

/**
 * 批量上传任务状态
 */
enum class BatchUploadJobStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}

/**
 * 批量上传进度信息
 */
data class BatchUploadProgress(
    val jobId: String,
    val status: BatchUploadJobStatus = BatchUploadJobStatus.PENDING,
    val totalFiles: Int = 0,
    val totalFolders: Int = 0,
    val processedFiles: Int = 0,
    val successCount: Int = 0,
    val failedCount: Int = 0,
    val message: String = "",
    val result: BatchUploadResponse? = null
)

/**
 * 批量上传进度更新
 */
data class BatchUploadProgressUpdate(
    val status: BatchUploadJobStatus? = null,
    val totalFiles: Int? = null,
    val totalFolders: Int? = null,
    val processedFiles: Int? = null,
    val successCount: Int? = null,
    val failedCount: Int? = null,
    val message: String? = null,
    val result: BatchUploadResponse? = null
)

/**
 * 异步任务创建响应
 */
data class BatchUploadJobCreatedResponse(
    val jobId: String
)
