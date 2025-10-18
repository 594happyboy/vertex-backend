package com.zzy.file.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.zzy.file.entity.FileMetadata
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update

/**
 * 文件Mapper接口
 * @author ZZY
 * @date 2025-10-09
 */
@Mapper
interface FileMapper : BaseMapper<FileMetadata> {
    
    /**
     * 增加下载次数
     */
    @Update("UPDATE file_metadata SET download_count = download_count + 1 WHERE id = #{id}")
    fun increaseDownloadCount(@Param("id") id: Long): Int
    
    /**
     * 根据MD5查询文件
     */
    @Select("SELECT * FROM file_metadata WHERE file_md5 = #{md5} AND status = 1 LIMIT 1")
    fun selectByMd5(@Param("md5") md5: String): FileMetadata?
}

