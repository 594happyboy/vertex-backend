package com.zzy.common.pagination

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import java.util.Base64

/**
 * 游标工具类
 * @author ZZY
 * @date 2025-11-02
 */
object CursorUtil {
    
    private val logger = LoggerFactory.getLogger(CursorUtil::class.java)
    private val objectMapper = ObjectMapper()
    
    /**
     * 编码游标
     */
    fun encodeCursor(params: CursorParams): String {
        return try {
            val json = objectMapper.writeValueAsString(params)
            Base64.getEncoder().encodeToString(json.toByteArray())
        } catch (e: Exception) {
            logger.error("编码游标失败", e)
            ""
        }
    }
    
    /**
     * 解码游标
     */
    fun decodeCursor(cursor: String): CursorParams? {
        return try {
            val json = String(Base64.getDecoder().decode(cursor))
            objectMapper.readValue<CursorParams>(json)
        } catch (e: Exception) {
            logger.error("解码游标失败: cursor={}", cursor, e)
            null
        }
    }
}

