package com.zzy.file.service

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.zzy.common.exception.BusinessException
import com.zzy.common.util.RedisUtil
import com.zzy.file.constants.FileConstants
import com.zzy.file.util.FileSizeFormatter
import com.zzy.file.util.NanoIdGenerator
import com.zzy.file.dto.*
import com.zzy.file.entity.FileFolder
import com.zzy.file.mapper.FolderMapper
import com.zzy.file.mapper.FileMapper
import com.zzy.file.service.common.ValidationService
import com.zzy.file.service.common.PathBuilderService
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
    private val redisUtil: RedisUtil,
    private val validationService: ValidationService,
    private val pathBuilderService: PathBuilderService
) {
    
    private val logger = LoggerFactory.getLogger(FolderService::class.java)
    
    /**
     * 获取用户的文件夹树
     */
    fun getFolderTree(userId: Long, includeStats: Boolean = false): FolderTreeResponse {
        // 尝试从缓存获取
        val cacheKey = "${FileConstants.Cache.KEY_FOLDER_TREE}$userId:$includeStats"
        redisUtil.get(cacheKey, FolderTreeResponse::class.java)?.let {
            logger.debug("从缓存获取文件夹树: userId={}", userId)
            return it
        }
        
        // 查询所有未删除的文件夹
        val allFolders = folderMapper.selectList(
            QueryWrapper<FileFolder>()
                .eq("user_id", userId)
                .eq("deleted", false)
                .orderByAsc("sort_index")
        )
        
        // 如果需要统计信息，填充统计数据
        if (includeStats) {
            allFolders.forEach { folder ->
                fillFolderStatistics(folder)
            }
        }
        
        // 构建树形结构
        val rootFolders = buildFolderTree(allFolders)
        
        // 统计总数
        val totalFiles = fileMapper.countByUserId(userId)
        val totalSize = fileMapper.sumSizeByUserId(userId)
        
        val response = FolderTreeResponse(
            rootFolders = rootFolders.map { FolderTreeNode.fromEntity(it) },
            totalFolders = allFolders.size,
            totalFiles = totalFiles,
            totalSize = totalSize,
            totalSizeFormatted = FileSizeFormatter.format(totalSize)
        )
        
        // 缓存结果
        redisUtil.set(cacheKey, response, FileConstants.Cache.EXPIRE_SHORT, TimeUnit.SECONDS)
        return response
    }
    
    /**
     * 构建文件夹树形结构
     */
    private fun buildFolderTree(folders: List<FileFolder>): List<FileFolder> {
        val folderMap = folders.associateBy { it.id }
        val rootFolders = mutableListOf<FileFolder>()
        
        folders.forEach { folder ->
            if (folder.parentId == null) {
                rootFolders.add(folder)
            } else {
                folderMap[folder.parentId]?.let { parent ->
                    parent.children = (parent.children ?: mutableListOf()).apply { add(folder) }
                }
            }
        }
        
        return rootFolders
    }
    
    /**
     * 填充文件夹统计信息
     */
    private fun fillFolderStatistics(folder: FileFolder) {
        folder.id?.let { folderId ->
            folder.fileCount = folderMapper.countFilesByFolderId(folderId)
            folder.subFolderCount = folderMapper.countSubFoldersByFolderId(folderId)
            folder.totalSize = folderMapper.calculateTotalSize(folderId)
        }
    }
    
    /**
     * 获取文件夹详情（通过数字ID - 内部使用）
     */
    fun getFolderInfo(folderId: Long, userId: Long): FolderDetail {
        // 尝试从缓存获取
        val cacheKey = "${FileConstants.Cache.KEY_FOLDER_INFO}$folderId"
        redisUtil.get(cacheKey, FolderDetail::class.java)?.let {
            logger.debug("从缓存获取文件夹信息: folderId={}", folderId)
            return it
        }
        
        val folder = validationService.validateAndGetFolder(folderId, userId)
        fillFolderStatistics(folder)
        
        // 获取父文件夹的公开ID
        val parentPublicId = folder.parentId?.let { 
            folderMapper.selectById(it)?.publicId 
        }
        
        val response = FolderDetail.fromEntity(folder, parentPublicId)
        
        // 缓存结果
        redisUtil.set(cacheKey, response, FileConstants.Cache.EXPIRE_SHORT, TimeUnit.SECONDS)
        return response
    }
    
    /**
     * 获取文件夹详情（通过公开ID - 对外接口）
     */
    fun getFolderInfoByPublicId(publicId: String, userId: Long): FolderDetail {
        val folder = validationService.validateAndGetFolderByPublicId(publicId, userId)
        fillFolderStatistics(folder)
        
        // 获取父文件夹的公开ID
        val parentPublicId = folder.parentId?.let { 
            folderMapper.selectById(it)?.publicId 
        }
        
        return FolderDetail.fromEntity(folder, parentPublicId)
    }
    
    /**
     * 获取文件夹详情（包含祖先路径）
     */
    fun getFolderInfoWithAncestors(folderId: Long, userId: Long): FolderInfoResponse {
        val folder = validationService.validateAndGetFolder(folderId, userId)
        
        // 获取祖先路径
        val ancestors = pathBuilderService.buildAncestors(folderId, userId)
        
        // 统计信息
        val childFolderCount = folderMapper.countSubFoldersByFolderId(folderId)
        val childFileCount = folderMapper.countFilesByFolderId(folderId)
        val totalSize = folderMapper.calculateTotalSize(folderId)
        
        return FolderInfoResponse(
            id = folder.id.toString(),
            name = folder.name,
            parentId = folder.parentId?.toString(),
            childFolderCount = childFolderCount,
            childFileCount = childFileCount,
            color = folder.color,
            ancestors = ancestors,
            statistics = FolderStatistics(
                totalSize = totalSize,
                totalSizeFormatted = FileSizeFormatter.format(totalSize)
            ),
            createdAt = folder.createdAt?.toString() ?: "",
            updatedAt = folder.updatedAt?.toString() ?: ""
        )
    }
    
    /**
     * 创建文件夹
     */
    @Transactional(rollbackFor = [Exception::class])
    fun createFolder(userId: Long, request: CreateFolderRequest): FolderDetail {
        // 验证文件夹名称
        if (request.name.isBlank()) {
            throw BusinessException(400, "文件夹名称不能为空")
        }
        
        // 生成唯一的公开ID
        val publicId = NanoIdGenerator.generateUnique({ id: String ->
            folderMapper.existsByPublicId(id) > 0
        })
        
        // 检查父文件夹是否存在（将 publicId 转换为内部 ID）
        val parentFolderId: Long? = if (request.parentId != null) {
            val parentFolder = folderMapper.selectByPublicId(request.parentId)
            if (parentFolder == null || parentFolder.deleted) {
                throw BusinessException(404, "父文件夹不存在")
            }
            if (parentFolder.userId != userId) {
                throw BusinessException(403, "无权在该文件夹下创建子文件夹")
            }
            parentFolder.id
        } else {
            null
        }
        
        // 检查同名文件夹（手动过滤已删除的记录）
        val existingFolder = folderMapper.selectOne(
            QueryWrapper<FileFolder>()
                .eq("user_id", userId)
                .eq("name", request.name)
                .eq("deleted", false)
                .apply {
                    if (parentFolderId != null) {
                        eq("parent_id", parentFolderId)
                    } else {
                        isNull("parent_id")
                    }
                }
        )
        
        if (existingFolder != null) {
            throw BusinessException(400, "该位置已存在同名文件夹")
        }
        
        // 获取下一个排序索引
        val maxSortIndex = folderMapper.getMaxSortIndex(userId, parentFolderId)
        
        // 创建文件夹
        val folder = FileFolder(
            publicId = publicId,
            userId = userId,
            name = request.name,
            parentId = parentFolderId,  // 使用内部 Long ID
            sortIndex = maxSortIndex + 1,
            color = request.color,
            description = request.description,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        
        folderMapper.insert(folder)
        
        // 清除缓存
        clearFolderCache(userId)
        
        logger.info("创建文件夹成功: userId={}, folderName={}, publicId={}, folderId={}", 
            userId, request.name, publicId, folder.id)
        
        return FolderDetail.fromEntity(folder, request.parentId)
    }
    
    /**
     * 更新文件夹（通过数字ID - 内部使用）
     */
    @Transactional(rollbackFor = [Exception::class])
    fun updateFolder(folderId: Long, userId: Long, request: UpdateFolderRequest): FolderDetail {
        val folder = validationService.validateAndGetFolder(folderId, userId)
        
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
        
        // 处理移动到其他父文件夹（将 publicId 转换为内部 ID）
        request.parentId?.let { parentPublicId ->
            val targetParentFolder = folderMapper.selectByPublicId(parentPublicId)
            if (targetParentFolder == null || targetParentFolder.deleted) {
                throw BusinessException(404, "目标父文件夹不存在")
            }
            if (targetParentFolder.userId != userId) {
                throw BusinessException(403, "无权移动到该文件夹")
            }
            
            val targetParentId = targetParentFolder.id
            if (targetParentId != folder.parentId) {
                // 防止循环引用
                val descendantIds = folderMapper.getDescendantIds(folderId)
                if (descendantIds.contains(targetParentId)) {
                    throw BusinessException(400, "不能将文件夹移动到其子文件夹中")
                }
                
                folder.parentId = targetParentId
                updated = true
            }
        }
        
        if (updated) {
            folder.updatedAt = LocalDateTime.now()
            folderMapper.updateById(folder)
            
            // 清除缓存
            clearFolderCache(userId)
            clearFolderInfoCache(folderId)
            
            logger.info("更新文件夹成功: folderId={}, folderName={}", folderId, folder.name)
        }
        
        // 获取父文件夹的公开ID
        val parentPublicId = folder.parentId?.let { 
            folderMapper.selectById(it)?.publicId 
        }
        
        return FolderDetail.fromEntity(folder, parentPublicId)
    }
    
    /**
     * 更新文件夹（通过公开ID - 对外接口）
     */
    @Transactional(rollbackFor = [Exception::class])
    fun updateFolderByPublicId(publicId: String, userId: Long, request: UpdateFolderRequest): FolderDetail {
        val folder = validationService.validateAndGetFolderByPublicId(publicId, userId)
        val folderId = folder.id!!
        var updated = false
        
        if (request.name != null && request.name != folder.name) {
            // 检查同名
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
        request.parentId?.let { parentPublicId ->
            val targetParentFolder = folderMapper.selectByPublicId(parentPublicId)
            if (targetParentFolder == null || targetParentFolder.deleted) {
                throw BusinessException(404, "目标父文件夹不存在")
            }
            if (targetParentFolder.userId != userId) {
                throw BusinessException(403, "无权移动到该文件夹")
            }
            
            val targetParentId = targetParentFolder.id
            if (targetParentId != folder.parentId) {
                // 防止循环引用
                val descendantIds = folderMapper.getDescendantIds(folderId)
                if (descendantIds.contains(targetParentId)) {
                    throw BusinessException(400, "不能将文件夹移动到其子文件夹中")
                }
                
                folder.parentId = targetParentId
                updated = true
            }
        }
        
        if (updated) {
            folder.updatedAt = LocalDateTime.now()
            folderMapper.updateById(folder)
            
            // 清除缓存
            clearFolderCache(userId)
            clearFolderInfoCache(folderId)
            
            logger.info("更新文件夹成功: publicId={}, folderName={}", publicId, folder.name)
        }
        
        // 获取父文件夹的公开ID
        val parentPublicId = folder.parentId?.let { 
            folderMapper.selectById(it)?.publicId 
        }
        
        return FolderDetail.fromEntity(folder, parentPublicId)
    }
    
    /**
     * 删除文件夹（软删除 - 通过数字ID - 内部使用）
     */
    @Transactional(rollbackFor = [Exception::class])
    fun deleteFolder(folderId: Long, userId: Long, recursive: Boolean = false): Boolean {
        val folder = validationService.validateAndGetFolder(folderId, userId)
        
        // 检查是否有子文件夹或文件
        val hasSubFolders = folderMapper.countSubFoldersByFolderId(folderId) > 0
        val hasFiles = folderMapper.countFilesByFolderId(folderId) > 0
        
        if (!recursive && (hasSubFolders || hasFiles)) {
            throw BusinessException(400, "文件夹不为空，无法删除。请先删除文件夹中的内容或使用递归删除")
        }
        
        // 递归删除子文件夹和文件
        if (recursive) {
            val descendantIds = folderMapper.getDescendantIds(folderId)
            val allFolderIds = (descendantIds + folderId).toList()  // 包含当前文件夹
            
            // 批量软删除所有子文件夹
            if (descendantIds.isNotEmpty()) {
                folderMapper.batchSoftDelete(descendantIds)
            }
            
            // 软删除所有相关文件夹中的文件（包括当前文件夹和所有子文件夹）
            val files = fileMapper.selectList(
                QueryWrapper<com.zzy.file.entity.FileMetadata>()
                    .`in`("folder_id", allFolderIds)
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
     * 删除文件夹（软删除 - 通过公开ID - 对外接口）
     */
    @Transactional(rollbackFor = [Exception::class])
    fun deleteFolderByPublicId(publicId: String, userId: Long, recursive: Boolean = false): Boolean {
        val folder = validationService.validateAndGetFolderByPublicId(publicId, userId)
        val folderId = folder.id!!
        
        // 检查是否有子文件夹或文件
        val hasSubFolders = folderMapper.countSubFoldersByFolderId(folderId) > 0
        val hasFiles = folderMapper.countFilesByFolderId(folderId) > 0
        
        if (!recursive && (hasSubFolders || hasFiles)) {
            throw BusinessException(400, "文件夹不为空，无法删除。请先删除文件夹中的内容或使用递归删除")
        }
        
        // 递归删除子文件夹和文件
        if (recursive) {
            val descendantIds = folderMapper.getDescendantIds(folderId)
            val allFolderIds = (descendantIds + folderId).toList()  // 包含当前文件夹
            
            // 批量软删除所有子文件夹
            if (descendantIds.isNotEmpty()) {
                folderMapper.batchSoftDelete(descendantIds)
            }
            
            // 软删除所有相关文件夹中的文件（包括当前文件夹和所有子文件夹）
            val files = fileMapper.selectList(
                QueryWrapper<com.zzy.file.entity.FileMetadata>()
                    .`in`("folder_id", allFolderIds)
                    .eq("deleted", false)
            )
            
            if (files.isNotEmpty()) {
                fileMapper.batchSoftDelete(files.map { it.id!! })
            }
        }
        
        // 软删除文件夹
        folderMapper.softDelete(folderId)
        
        // 清除缓存
        clearFolderCache(userId)
        clearFolderInfoCache(folderId)
        
        logger.info("删除文件夹成功: publicId={}, folderName={}, recursive={}", publicId, folder.name, recursive)
        
        return true
    }
    
    /**
     * 获取文件夹路径（面包屑导航 - 通过数字ID - 内部使用）
     */
    fun getFolderPath(folderId: Long?, userId: Long): FolderPathResponse {
        folderId ?: return FolderPathResponse(path = emptyList())
        
        // 尝试从缓存获取
        val cacheKey = "${FileConstants.Cache.KEY_FOLDER_PATH}$folderId"
        redisUtil.get(cacheKey, FolderPathResponse::class.java)?.let {
            return it
        }
        
        val path = pathBuilderService.buildFolderPath(folderId, userId)
        val response = FolderPathResponse(path = path)
        
        // 缓存结果
        redisUtil.set(cacheKey, response, FileConstants.Cache.EXPIRE_SHORT, TimeUnit.SECONDS)
        return response
    }
    
    /**
     * 获取文件夹路径（面包屑导航 - 通过公开ID - 对外接口）
     */
    fun getFolderPathByPublicId(publicId: String?, userId: Long): FolderPathResponse {
        publicId ?: return FolderPathResponse(path = emptyList())
        
        val folder = validationService.validateAndGetFolderByPublicId(publicId, userId)
        val folderId = folder.id!!
        
        val path = pathBuilderService.buildFolderPath(folderId, userId)
        return FolderPathResponse(path = path)
    }
    
    /**
     * 批量排序文件夹
     */
    @Transactional(rollbackFor = [Exception::class])
    fun batchSortFolders(userId: Long, request: BatchSortFoldersRequest): Boolean {
        // 将 publicId 列表转换为内部 ID 列表
        val publicIds = request.items.map { it.id }
        val folders = folderMapper.selectBatchByPublicIds(publicIds)
        
        // 验证所有文件夹的归属
        validationService.validateFolderOwnership(folders, userId)
        
        // 创建 publicId 到 内部 ID 的映射
        val publicIdToInternalId = folders.associate { it.publicId!! to it.id!! }
        
        // 批量更新排序索引（将 publicId 转换为内部 ID）
        val sortItems = request.items.mapNotNull { item ->
            publicIdToInternalId[item.id]?.let { internalId ->
                com.zzy.file.mapper.FolderSortItem(id = internalId, sortIndex = item.sortIndex)
            }
        }
        
        folderMapper.batchUpdateSortIndex(sortItems)
        
        // 清除缓存
        clearFolderCache(userId)
        
        logger.info("批量排序文件夹成功: userId={}, count={}", userId, request.items.size)
        
        return true
    }
    
    /**
     * 清除文件夹树缓存
     */
    private fun clearFolderCache(userId: Long) {
        redisUtil.deleteByPattern("${FileConstants.Cache.KEY_FOLDER_TREE}$userId:*")
    }
    
    /**
     * 清除文件夹信息缓存
     */
    private fun clearFolderInfoCache(folderId: Long) {
        redisUtil.delete("${FileConstants.Cache.KEY_FOLDER_INFO}$folderId")
        redisUtil.delete("${FileConstants.Cache.KEY_FOLDER_PATH}$folderId")
    }
}

