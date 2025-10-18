package com.zzy.file.dto

/**
 * 文件列表响应DTO
 * @author ZZY
 * @date 2025-10-09
 */
data class FileListResponse(
    /** 总记录数 */
    val total: Long,
    
    /** 当前页码 */
    val page: Int,
    
    /** 每页数量 */
    val size: Int,
    
    /** 文件列表 */
    val files: List<FileResponse>
)

