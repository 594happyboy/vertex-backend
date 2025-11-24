package com.zzy.blog.service

import com.zzy.blog.dto.BatchUploadProgressUpdate

fun interface BatchUploadProgressCallback {
    fun onProgress(update: BatchUploadProgressUpdate)
}
