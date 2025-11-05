package com.zzy.common.pagination

/**
 * 分页核心接口和扩展函数
 * @author ZZY
 * @date 2025-11-04
 */

/**
 * 实体ID接口 - 所有需要分页的实体必须实现此接口
 */
interface HasId {
    val id: Long?
}

/**
 * 统一的游标分页请求接口
 */
interface CursorPageRequest {
    val cursor: String?
    val limit: Int
    val sortField: String
    val sortOrder: String
    val keyword: String?
    val type: String?
}

/**
 * 查询参数
 */
data class QueryParams(
    val lastId: Long?,
    val lastSortValue: String?,
    val limit: Int
)

/**
 * 游标失效异常
 */
class CursorExpiredException(message: String) : RuntimeException(message)

/**
 * 扩展函数：验证游标是否匹配请求
 */
fun CursorParams.matches(request: CursorPageRequest): Boolean {
    return this.sortField == request.sortField &&
           this.sortOrder == request.sortOrder &&
           this.keyword == request.keyword &&
           this.type == request.type
}

/**
 * 游标分页扩展函数 - DSL 风格
 * 
 * @param T 返回的DTO类型
 * @param E 实体类型，必须实现 HasId 接口
 * @param request 分页请求
 * @param query 查询函数，接收 QueryParams 返回实体列表
 * @param mapper 实体到DTO的映射函数
 * @param sortValueExtractor 排序字段值提取函数
 * @return 分页响应
 * 
 * @example
 * ```kotlin
 * return paginate(
 *     request = request,
 *     query = { params -> mapper.selectWithCursor(params.lastId, params.lastSortValue, params.limit) },
 *     mapper = { entity -> EntityDto.fromEntity(it) },
 *     sortValueExtractor = { it.name }
 * )
 * ```
 */
inline fun <T, E : HasId> paginate(
    request: CursorPageRequest,
    crossinline query: (QueryParams) -> List<E>,
    crossinline mapper: (E) -> T,
    crossinline sortValueExtractor: (E) -> String
): PaginatedResponse<T> {
    
    // 解析并验证游标
    val cursor = request.cursor?.let { 
        CursorUtil.decodeCursor(it)?.also { params ->
            if (!params.matches(request)) {
                throw CursorExpiredException("游标已失效，请重新请求")
            }
        }
    }
    
    // 构建查询参数（多查一个用于判断 hasMore）
    val queryParams = QueryParams(
        lastId = cursor?.lastId,
        lastSortValue = cursor?.lastSortValue,
        limit = request.limit + 1
    )
    
    // 执行查询
    val entities = query(queryParams)
    val hasMore = entities.size > request.limit
    val items = entities.take(request.limit).map(mapper)
    
    // 构建下一页游标
    val nextCursor = if (hasMore && entities.isNotEmpty()) {
        val lastEntity = entities[request.limit - 1]
        CursorUtil.encodeCursor(
            CursorParams(
                lastId = lastEntity.id ?: throw IllegalStateException("Entity ID cannot be null"),
                lastSortValue = sortValueExtractor(lastEntity),
                sortField = request.sortField,
                sortOrder = request.sortOrder,
                keyword = request.keyword,
                type = request.type
            )
        )
    } else null
    
    return PaginatedResponse.of(
        items = items,
        limit = request.limit,
        nextCursor = nextCursor,
        hasMore = hasMore
    )
}

