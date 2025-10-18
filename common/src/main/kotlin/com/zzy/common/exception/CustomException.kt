package com.zzy.common.exception

/**
 * 自定义业务异常
 * @author ZZY
 * @date 2025-10-09
 */
open class BusinessException(
    val code: Int = 500,
    override val message: String = "业务异常"
) : RuntimeException(message)

/**
 * 文件异常
 */
open class FileException(message: String) : BusinessException(500, message)

/**
 * 文件未找到异常
 */
class FileNotFoundException(message: String = "文件不存在") : BusinessException(404, message)

/**
 * 文件上传异常
 */
class FileUploadException(message: String = "文件上传失败") : BusinessException(500, message)

/**
 * 文件大小超限异常
 */
class FileSizeExceededException(message: String = "文件大小超过限制") : BusinessException(400, message)

/**
 * 文件类型不支持异常
 */
class FileTypeNotSupportedException(message: String = "不支持的文件类型") : BusinessException(400, message)

