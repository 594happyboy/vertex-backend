package com.zzy.blog.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.zzy.blog.entity.Group
import org.apache.ibatis.annotations.Mapper

/**
 * 分组 Mapper
 * @author ZZY
 * @date 2025-10-18
 */
@Mapper
interface GroupMapper : BaseMapper<Group>

