package com.zzy.file.service

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import org.springframework.stereotype.Service
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.CacheEvict
import com.zzy.file.dto.CreateFolderRequest
import com.zzy.file.entity.FileFolder
import com.zzy.file.mapper.FolderMapper
import com.zzy.common.exception.BusinessException
import org.slf4j.LoggerFactory

/**
 * 系统文件夹管理器 - 统一管理所有系统级文件夹
 * 
 * 负责创建和维护系统级文件夹结构：
 * - 系统（根）
 *   ├── 知识库（知识库文档）
 *   └── 附件（通用附件）
 * 
 * @author ZZY
 * @date 2025-11-03
 */
@Service
class SystemFolderManager(
    private val folderService: FolderService,
    private val folderMapper: FolderMapper
) {
    private val logger = LoggerFactory.getLogger(SystemFolderManager::class.java)
    
    companion object {
        // 系统文件夹名称常量
        const val SYSTEM_ROOT = "系统"
        const val KNOWLEDGE_BASE = "知识库"
        const val ATTACHMENTS = "附件"
        
        // 文件夹描述
        private val FOLDER_DESCRIPTIONS = mapOf(
            SYSTEM_ROOT to "系统文件夹",
            KNOWLEDGE_BASE to "知识库文档存储",
            ATTACHMENTS to "附件文件存储"
        )
    }
    
    /**
     * 系统文件夹类型枚举
     */
    enum class SystemFolderType(val folderName: String) {
        /** 知识库文档 */
        KNOWLEDGE_BASE(SystemFolderManager.KNOWLEDGE_BASE),
        /** 通用附件 */
        ATTACHMENTS(SystemFolderManager.ATTACHMENTS)
    }
    
    /**
     * 获取或创建系统文件夹
     * 
     * 此方法会自动：
     * 1. 确保"系统"根文件夹存在
     * 2. 在"系统"文件夹下创建指定类型的子文件夹
     * 3. 缓存结果以提高性能
     * 
     * @param userId 用户ID
     * @param type 系统文件夹类型
     * @return 文件夹ID
     */
    @Cacheable(value = ["systemFolder"], key = "#userId + '_' + #type.name")
    fun getOrCreateSystemFolder(userId: Long, type: SystemFolderType): Long {
        logger.debug("获取或创建系统文件夹: userId={}, type={}", userId, type.name)
        
        // 1. 获取或创建"系统"根文件夹
        var systemFolder = folderMapper.selectOne(
            QueryWrapper<FileFolder>()
                .eq("user_id", userId)
                .eq("name", SYSTEM_ROOT)
                .isNull("parent_id")
                .eq("deleted", false)
        )
        
        if (systemFolder == null) {
            logger.info("创建系统根文件夹: userId={}", userId)
            folderService.createFolder(
                userId = userId,
                request = CreateFolderRequest(
                    name = SYSTEM_ROOT,
                    parentId = null,
                    color = "#607D8B", // 灰蓝色
                    description = FOLDER_DESCRIPTIONS[SYSTEM_ROOT]
                )
            )
            // 重新查询获取ID
            systemFolder = folderMapper.selectOne(
                QueryWrapper<FileFolder>()
                    .eq("user_id", userId)
                    .eq("name", SYSTEM_ROOT)
                    .isNull("parent_id")
                    .eq("deleted", false)
            ) ?: throw BusinessException(500, "创建系统根文件夹失败")
        }
        
        // 2. 获取或创建指定类型的子文件夹
        val targetFolderName = type.folderName
        var targetFolder = folderMapper.selectOne(
            QueryWrapper<FileFolder>()
                .eq("user_id", userId)
                .eq("name", targetFolderName)
                .eq("parent_id", systemFolder.id)
                .eq("deleted", false)
        )
        
        if (targetFolder == null) {
            logger.info("创建系统子文件夹: userId={}, type={}, parentId={}", 
                userId, targetFolderName, systemFolder.id)
            folderService.createFolder(
                userId = userId,
                request = CreateFolderRequest(
                    name = targetFolderName,
                    parentId = systemFolder.publicId,  // 使用公开ID
                    color = getColorForFolderType(type),
                    description = FOLDER_DESCRIPTIONS[targetFolderName]
                )
            )
            // 重新查询获取ID
            targetFolder = folderMapper.selectOne(
                QueryWrapper<FileFolder>()
                    .eq("user_id", userId)
                    .eq("name", targetFolderName)
                    .eq("parent_id", systemFolder.id)
                    .eq("deleted", false)
            ) ?: throw BusinessException(500, "创建系统子文件夹失败")
        }
        
        logger.debug("系统文件夹ID: {}", targetFolder.id)
        return targetFolder.id!!
    }
    
    /**
     * 根据文件夹类型获取对应的颜色标识
     */
    private fun getColorForFolderType(type: SystemFolderType): String = when(type) {
        SystemFolderType.KNOWLEDGE_BASE -> "#2196F3" // 蓝色 - 知识
        SystemFolderType.ATTACHMENTS -> "#FF9800"    // 橙色 - 附件
    }
    
    /**
     * 清除指定用户和类型的系统文件夹缓存
     * 
     * @param userId 用户ID
     * @param type 系统文件夹类型
     */
    @CacheEvict(value = ["systemFolder"], key = "#userId + '_' + #type.name")
    fun clearCache(userId: Long, type: SystemFolderType) {
        logger.info("清除系统文件夹缓存: userId={}, type={}", userId, type.name)
    }
    
    /**
     * 清除指定用户的所有系统文件夹缓存
     * 
     * @param userId 用户ID
     */
    @CacheEvict(value = ["systemFolder"], allEntries = true)
    fun clearAllCache(userId: Long) {
        logger.info("清除所有系统文件夹缓存: userId={}", userId)
    }
}

