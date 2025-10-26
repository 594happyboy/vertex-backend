package com.zzy.file.service

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper
import com.zzy.common.exception.BusinessException
import com.zzy.common.util.RedisUtil
import com.zzy.file.dto.*
import com.zzy.file.entity.FileFolder
import com.zzy.file.mapper.FolderMapper
import com.zzy.file.mapper.FileMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * 文件夹服务
 * @author ZZY
 * @date 2025-10-23
 */
@Service
class FolderService(
    private val folderMapper: FolderMapper,
    private val fileMapper: FileMapper,
    private val redisUtil: RedisUtil
) {
    
    private val logger = LoggerFactory.getLogger(FolderService::class.java)
    
    companion object {
        private const val CACHE_KEY_FOLDER_TREE = "folder:tree:"
        private const val CACHE_KEY_FOLDER_INFO = "folder:info:"
        private const val CACHE_KEY_FOLDER_PATH = "folder:path:"
        private const val CACHE_EXPIRE_TIME = 300L // 5分钟
    }
    
    /**
     * 获取用户的文件夹树
     */
    fun getFolderTree(userId: Long, includeStats: Boolean = false): FolderTreeResponse {
        // 尝试从缓存获取
        val cacheKey = "$CACHE_KEY_FOLDER_TREE$userId:$includeStats"
        val cached = redisUtil.get(cacheKey, FolderTreeResponse::class.java)
        if (cached != null) {
            logger.debug("从缓存获取文件夹树: userId={}", userId)
            return cached
        }
        
        // 查询所有未删除的文件夹（手动过滤已删除的记录）
        val allFolders = folderMapper.selectList(
            QueryWrapper<FileFolder>()
                .eq("user_id", userId)
                .eq("deleted", false)
                .orderByAsc("sort_index")
        )
        
        // 如果需要统计信息，填充统计数据
        if (includeStats) {
            allFolders.forEach { folder ->
                folder.fileCount = folderMapper.countFilesByFolderId(folder.id!!)
                folder.subFolderCount = folderMapper.countSubFoldersByFolderId(folder.id!!)
                folder.totalSize = folderMapper.calculateTotalSize(folder.id!!)
            }
        }
        
        // 构建树形结构
        val folderMap = allFolders.associateBy { it.id }
        val rootFolders = mutableListOf<FileFolder>()
        
        allFolders.forEach { folder ->
            if (folder.parentId == null) {
                rootFolders.add(folder)
            } else {
                val parent = folderMap[folder.parentId]
                if (parent != null) {
                    if (parent.children == null) {
                        parent.children = mutableListOf()
                    }
                    parent.children!!.add(folder)
                }
            }
        }
        
        // 统计总数
        val totalFiles = fileMapper.countByUserId(userId)
        val totalSize = fileMapper.sumSizeByUserId(userId)
        
        val response = FolderTreeResponse(
            rootFolders = rootFolders.map { FolderResponse.fromEntity(it) },
            totalFolders = allFolders.size,
            totalFiles = totalFiles,
            totalSize = totalSize,
            totalSizeFormatted = formatFileSize(totalSize)
        )
        
        // 缓存结果
        redisUtil.set(cacheKey, response, CACHE_EXPIRE_TIME, TimeUnit.SECONDS)
        
        return response
    }
    
    /**
     * 获取文件夹详情
     */
    fun getFolderInfo(folderId: Long, userId: Long): FolderResponse {
        // 尝试从缓存获取
        val cacheKey = "$CACHE_KEY_FOLDER_INFO$folderId"
        val cached = redisUtil.get(cacheKey, FolderResponse::class.java)
        if (cached != null) {
            logger.debug("从缓存获取文件夹信息: folderId={}", folderId)
            return cached
        }
        
        val folder = folderMapper.selectById(folderId)
            ?: throw BusinessException(404, "文件夹不存在")
        
        // 权限检查
        if (folder.userId != userId) {
            throw BusinessException(403, "无权访问该文件夹")
        }
        
        if (folder.deleted) {
            throw BusinessException(404, "文件夹已被删除")
        }
        
        // 填充统计信息
        folder.fileCount = folderMapper.countFilesByFolderId(folderId)
        folder.subFolderCount = folderMapper.countSubFoldersByFolderId(folderId)
        folder.totalSize = folderMapper.calculateTotalSize(folderId)
        
        val response = FolderResponse.fromEntity(folder)
        
        // 缓存结果
        redisUtil.set(cacheKey, response, CACHE_EXPIRE_TIME, TimeUnit.SECONDS)
        
        return response
    }
    
    /**
     * 创建文件夹
     */
    @Transactional(rollbackFor = [Exception::class])
    fun createFolder(userId: Long, request: CreateFolderRequest): FolderResponse {
        // 验证文件夹名称
        if (request.name.isBlank()) {
            throw BusinessException(400, "文件夹名称不能为空")
        }
        
        // 检查父文件夹是否存在
        if (request.parentId != null) {
            val parentFolder = folderMapper.selectById(request.parentId)
            if (parentFolder == null || parentFolder.deleted) {
                throw BusinessException(404, "父文件夹不存在")
            }
            if (parentFolder.userId != userId) {
                throw BusinessException(403, "无权在该文件夹下创建子文件夹")
            }
        }
        
        // 检查同名文件夹（手动过滤已删除的记录）
        val existingFolder = folderMapper.selectOne(
            QueryWrapper<FileFolder>()
                .eq("user_id", userId)
                .eq("name", request.name)
                .eq("deleted", false)
                .apply {
                    if (request.parentId != null) {
                        eq("parent_id", request.parentId)
                    } else {
                        isNull("parent_id")
                    }
                }
        )
        
        if (existingFolder != null) {
            throw BusinessException(400, "该位置已存在同名文件夹")
        }
        
        // 获取下一个排序索引
        val maxSortIndex = folderMapper.getMaxSortIndex(userId, request.parentId)
        
        // 创建文件夹
        val folder = FileFolder(
            userId = userId,
            name = request.name,
            parentId = request.parentId,
            sortIndex = maxSortIndex + 1,
            color = request.color,
            description = request.description,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        
        folderMapper.insert(folder)
        
        // 清除缓存
        clearFolderCache(userId)
        
        logger.info("创建文件夹成功: userId={}, folderName={}, folderId={}", userId, request.name, folder.id)
        
        return FolderResponse.fromEntity(folder)
    }
    
    /**
     * 更新文件夹
     */
    @Transactional(rollbackFor = [Exception::class])
    fun updateFolder(folderId: Long, userId: Long, request: UpdateFolderRequest): FolderResponse {
        val folder = folderMapper.selectById(folderId)
            ?: throw BusinessException(404, "文件夹不存在")
        
        // 权限检查
        if (folder.userId != userId) {
            throw BusinessException(403, "无权修改该文件夹")
        }
        
        if (folder.deleted) {
            throw BusinessException(404, "文件夹已被删除")
        }
        
        // 更新字段
        var updated = false
        
        if (request.name != null && request.name != folder.name) {
            // 检查同名（手动过滤已删除的记录）
            val existingFolder = folderMapper.selectOne(
                QueryWrapper<FileFolder>()
                    .eq("user_id", userId)
                    .eq("name", request.name)
                    .eq("deleted", false)
                    .apply {
                        if (folder.parentId != null) {
                            eq("parent_id", folder.parentId)
                        } else {
                            isNull("parent_id")
                        }
                    }
                    .ne("id", folderId)
            )
            
            if (existingFolder != null) {
                throw BusinessException(400, "该位置已存在同名文件夹")
            }
            
            folder.name = request.name
            updated = true
        }
        
        if (request.color != null && request.color != folder.color) {
            folder.color = request.color
            updated = true
        }
        
        if (request.description != null && request.description != folder.description) {
            folder.description = request.description
            updated = true
        }
        
        if (request.sortIndex != null && request.sortIndex != folder.sortIndex) {
            folder.sortIndex = request.sortIndex
            updated = true
        }
        
        // 处理移动到其他父文件夹
        if (request.parentId != folder.parentId) {
            // 防止循环引用
            if (request.parentId != null) {
                val descendantIds = folderMapper.getDescendantIds(folderId)
                if (descendantIds.contains(request.parentId)) {
                    throw BusinessException(400, "不能将文件夹移动到其子文件夹中")
                }
                
                // 检查目标父文件夹是否存在
                val parentFolder = folderMapper.selectById(request.parentId)
                if (parentFolder == null || parentFolder.deleted) {
                    throw BusinessException(404, "目标父文件夹不存在")
                }
                if (parentFolder.userId != userId) {
                    throw BusinessException(403, "无权移动到该文件夹")
                }
            }
            
            folder.parentId = request.parentId
            updated = true
        }
        
        if (updated) {
            folder.updatedAt = LocalDateTime.now()
            folderMapper.updateById(folder)
            
            // 清除缓存
            clearFolderCache(userId)
            clearFolderInfoCache(folderId)
            
            logger.info("更新文件夹成功: folderId={}, folderName={}", folderId, folder.name)
        }
        
        return FolderResponse.fromEntity(folder)
    }
    
    /**
     * 删除文件夹（软删除）
     */
    @Transactional(rollbackFor = [Exception::class])
    fun deleteFolder(folderId: Long, userId: Long, recursive: Boolean = false): Boolean {
        val folder = folderMapper.selectById(folderId)
            ?: throw BusinessException(404, "文件夹不存在")
        
        // 权限检查
        if (folder.userId != userId) {
            throw BusinessException(403, "无权删除该文件夹")
        }
        
        if (folder.deleted) {
            throw BusinessException(404, "文件夹已被删除")
        }
        
        // 检查是否有子文件夹或文件
        val hasSubFolders = folderMapper.countSubFoldersByFolderId(folderId) > 0
        val hasFiles = folderMapper.countFilesByFolderId(folderId) > 0
        
        if (!recursive && (hasSubFolders || hasFiles)) {
            throw BusinessException(400, "文件夹不为空，无法删除。请先删除文件夹中的内容或使用递归删除")
        }
        
        // 递归删除子文件夹和文件
        if (recursive) {
            val descendantIds = folderMapper.getDescendantIds(folderId)
            
            // 批量软删除所有子文件夹
            if (descendantIds.isNotEmpty()) {
                folderMapper.batchSoftDelete(descendantIds)
            }
            
            // 软删除文件夹中的所有文件（手动过滤已删除的记录）
            val files = fileMapper.selectList(
                QueryWrapper<com.zzy.file.entity.FileMetadata>()
                    .`in`("folder_id", descendantIds)
                    .eq("deleted", false)
            )
            
            if (files.isNotEmpty()) {
                // 批量软删除文件
                fileMapper.batchSoftDelete(files.map { it.id!! })
            }
        }
        
        // 软删除文件夹
        folderMapper.softDelete(folderId)
        
        // 清除缓存
        clearFolderCache(userId)
        clearFolderInfoCache(folderId)
        
        logger.info("删除文件夹成功: folderId={}, folderName={}, recursive={}", folderId, folder.name, recursive)
        
        return true
    }
    
    /**
     * 获取文件夹路径（面包屑导航）
     */
    fun getFolderPath(folderId: Long?, userId: Long): FolderPathResponse {
        if (folderId == null) {
            return FolderPathResponse(path = emptyList())
        }
        
        // 尝试从缓存获取
        val cacheKey = "$CACHE_KEY_FOLDER_PATH$folderId"
        val cached = redisUtil.get(cacheKey, FolderPathResponse::class.java)
        if (cached != null) {
            return cached
        }
        
        val path = mutableListOf<FolderPathItem>()
        var currentId: Long? = folderId
        
        while (currentId != null) {
            val folder = folderMapper.selectById(currentId)
            if (folder == null || folder.deleted) {
                break
            }
            
            // 权限检查
            if (folder.userId != userId) {
                break
            }
            
            path.add(0, FolderPathItem(id = folder.id!!, name = folder.name))
            currentId = folder.parentId
        }
        
        val response = FolderPathResponse(path = path)
        
        // 缓存结果
        redisUtil.set(cacheKey, response, CACHE_EXPIRE_TIME, TimeUnit.SECONDS)
        
        return response
    }
    
    /**
     * 批量排序文件夹
     */
    @Transactional(rollbackFor = [Exception::class])
    fun batchSortFolders(userId: Long, request: BatchSortFoldersRequest): Boolean {
        // 验证所有文件夹的归属
        val folderIds = request.items.map { it.id }
        val folders = folderMapper.selectBatchIds(folderIds)
        
        folders.forEach { folder ->
            if (folder.userId != userId) {
                throw BusinessException(403, "无权操作文件夹: ${folder.name}")
            }
        }
        
        // 批量更新排序索引
        val sortItems = request.items.map { 
            com.zzy.file.mapper.FolderSortItem(id = it.id, sortIndex = it.sortIndex)
        }
        
        folderMapper.batchUpdateSortIndex(sortItems)
        
        // 清除缓存
        clearFolderCache(userId)
        
        logger.info("批量排序文件夹成功: userId={}, count={}", userId, request.items.size)
        
        return true
    }
    
    /**
     * 格式化文件大小
     */
    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format("%.2f KB", size / 1024.0)
            size < 1024 * 1024 * 1024 -> String.format("%.2f MB", size / (1024.0 * 1024))
            else -> String.format("%.2f GB", size / (1024.0 * 1024 * 1024))
        }
    }
    
    /**
     * 清除文件夹树缓存
     */
    private fun clearFolderCache(userId: Long) {
        redisUtil.deleteByPattern("$CACHE_KEY_FOLDER_TREE$userId:*")
    }
    
    /**
     * 清除文件夹信息缓存
     */
    private fun clearFolderInfoCache(folderId: Long) {
        redisUtil.delete("$CACHE_KEY_FOLDER_INFO$folderId")
        redisUtil.delete("$CACHE_KEY_FOLDER_PATH$folderId")
    }
}

