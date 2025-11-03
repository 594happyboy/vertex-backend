package com.zzy.file.dto.pagination

/**
 * 此文件已废弃，请使用 com.zzy.common.pagination.CursorUtil
 * @deprecated 使用 com.zzy.common.pagination.CursorUtil 替代
 */
@Deprecated("使用 com.zzy.common.pagination.CursorUtil 替代")
object CursorUtil {
    @Deprecated("使用 com.zzy.common.pagination.CursorUtil.encodeCursor 替代")
    fun encodeCursor(params: CursorParams): String = com.zzy.common.pagination.CursorUtil.encodeCursor(params)
    
    @Deprecated("使用 com.zzy.common.pagination.CursorUtil.decodeCursor 替代")
    fun decodeCursor(cursor: String): CursorParams? = com.zzy.common.pagination.CursorUtil.decodeCursor(cursor)
    
    @Deprecated("使用 com.zzy.common.pagination.CursorUtil.validateCursorParams 替代")
    fun validateCursorParams(
        cursorParams: CursorParams,
        sortField: String,
        sortOrder: String,
        keyword: String?,
        type: String?
    ): Boolean = com.zzy.common.pagination.CursorUtil.validateCursorParams(cursorParams, sortField, sortOrder, keyword, type)
}

