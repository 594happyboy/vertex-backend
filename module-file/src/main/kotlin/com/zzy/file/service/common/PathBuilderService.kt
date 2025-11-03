package com.zzy.file.service.common

import com.zzy.file.dto.FolderAncestor
import com.zzy.file.dto.FolderPathItem
import com.zzy.file.mapper.FolderMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 路径构建服务 - 统一处理文件夹路径和面包屑导航
 * 
 * 消除了多个服务中重复的路径构建逻辑
 * 
 * @author ZZY
 * @date 2025-11-03
 */
@Service
class PathBuilderService(
    private val folderMapper: FolderMapper
) {
    
    private val logger = LoggerFactory.getLogger(PathBuilderService::class.java)
    
    /**
     * 构建文件夹路径（返回 FolderPathItem 列表）
     * 用于面包屑导航
     */
    fun buildFolderPath(folderId: Long, userId: Long): List<FolderPathItem> {
        return buildPath(folderId, userId) { folder ->
            FolderPathItem(id = folder.id!!, name = folder.name)
        }
    }
    
    /**
     * 构建祖先路径（返回 FolderAncestor 列表，包含根目录）
     * 用于文件夹详情展示
     */
    fun buildAncestors(folderId: Long, userId: Long): List<FolderAncestor> {
        val ancestors = buildPath(folderId, userId) { folder ->
            FolderAncestor(id = folder.id.toString(), name = folder.name)
        }.toMutableList()
        
        // 添加根目录
        ancestors.add(0, FolderAncestor(id = "root", name = "我的文件"))
        
        return ancestors
    }
    
    /**
     * 通用的路径构建方法
     * 使用泛型和高阶函数消除重复代码
     */
    private fun <T> buildPath(folderId: Long, userId: Long, mapper: (com.zzy.file.entity.FileFolder) -> T): List<T> {
        val path = mutableListOf<T>()
        var currentId: Long? = folderId
        val visited = mutableSetOf<Long>() // 防止循环引用
        
        while (currentId != null) {
            // 防止无限循环
            if (currentId in visited) {
                logger.warn("检测到文件夹循环引用: folderId={}", currentId)
                break
            }
            visited.add(currentId)
            
            val folder = folderMapper.selectById(currentId)
            if (folder == null || folder.deleted || folder.userId != userId) {
                break
            }
            
            path.add(0, mapper(folder))
            currentId = folder.parentId
        }
        
        return path
    }
}

