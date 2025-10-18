package com.zzy.blog.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.zzy.blog.entity.Document
import org.apache.ibatis.annotations.Mapper

/**
 * 文档 Mapper
 * @author ZZY
 * @date 2025-10-18
 */
@Mapper
interface DocumentMapper : BaseMapper<Document>

