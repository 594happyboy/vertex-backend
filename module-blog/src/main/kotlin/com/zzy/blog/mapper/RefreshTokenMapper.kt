package com.zzy.blog.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.zzy.blog.entity.RefreshTokenEntity
import org.apache.ibatis.annotations.Mapper

@Mapper
interface RefreshTokenMapper : BaseMapper<RefreshTokenEntity>
