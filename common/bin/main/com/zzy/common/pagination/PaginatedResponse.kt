package com.zzy.common.pagination

/**
 * 游标分页响应DTO (统一规范)
 * @author ZZY
 * @date 2025-11-02
 */
data class PaginatedResponse<T>(
    /** 当前页数据 */
    val items: List<T>,
    
    /** 分页信息 */
    val pagination: PaginationInfo
) {
    companion object {
        /**
         * 创建空响应
         */
        fun <T> empty(limit: Int = 50): PaginatedResponse<T> {
            return PaginatedResponse(
                items = emptyList(),
                pagination = PaginationInfo(
                    limit = limit,
                    nextCursor = null,
                    hasMore = false,
                    total = 0
                )
            )
        }
        
        /**
         * 创建响应
         */
        fun <T> of(
            items: List<T>,
            limit: Int,
            nextCursor: String?,
            hasMore: Boolean,
            total: Long? = null
        ): PaginatedResponse<T> {
            return PaginatedResponse(
                items = items,
                pagination = PaginationInfo(
                    limit = limit,
                    nextCursor = nextCursor,
                    hasMore = hasMore,
                    total = total
                )
            )
        }
    }
}

/**
 * 分页信息
 */
data class PaginationInfo(
    /** 每页大小 */
    val limit: Int,
    
    /** 下一页游标（Base64编码），null表示无更多数据 */
    val nextCursor: String?,
    
    /** 是否有更多数据 */
    val hasMore: Boolean,
    
    /** 总数（可选，统计代价高时可省略） */
    val total: Long? = null
)

