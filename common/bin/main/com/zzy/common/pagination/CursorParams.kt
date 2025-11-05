package com.zzy.common.pagination

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 游标参数（用于解析和构建游标）
 * @author ZZY
 * @date 2025-11-02
 */
data class CursorParams(
    /** 上一页最后一项的ID */
    @JsonProperty("lastId")
    val lastId: Long,
    
    /** 上一页最后一项的排序字段值 */
    @JsonProperty("lastSortValue")
    val lastSortValue: String,
    
    /** 排序字段 */
    @JsonProperty("sortField")
    val sortField: String,
    
    /** 排序顺序 */
    @JsonProperty("sortOrder")
    val sortOrder: String,
    
    /** 搜索关键词 */
    @JsonProperty("keyword")
    val keyword: String? = null,
    
    /** 资源类型过滤 */
    @JsonProperty("type")
    val type: String? = null,
    
    /** 资源类型标识（用于混合查询时区分文件夹/文件阶段） */
    @JsonProperty("resourceType")
    val resourceType: String? = null  // "folder" 或 "file"
)

