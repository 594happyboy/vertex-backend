package com.zzy.file.service.common

import com.zzy.common.exception.BusinessException
import com.zzy.file.entity.FileFolder
import com.zzy.file.entity.FileMetadata
import com.zzy.file.mapper.FileMapper
import com.zzy.file.mapper.FolderMapper
import org.springframework.stereotype.Service

/**
 * 验证服务 - 统一处理权限验证和实体查询
 * 
 * 消除了多个服务中重复的验证逻辑
 * 
 * @author ZZY
 * @date 2025-11-03
 */
@Service
class ValidationService(
    private val fileMapper: FileMapper,
    private val folderMapper: FolderMapper
) {
    
    /**
     * 验证并获取文件（包含权限检查）- 通过数字ID
     * 
     * @param fileId 文件ID
     * @param userId 用户ID
     * @param includeDeleted 是否包含已删除的文件
     * @return 文件实体
     * @throws BusinessException 文件不存在、无权限或已删除时抛出异常
     */
    fun validateAndGetFile(
        fileId: Long, 
        userId: Long, 
        includeDeleted: Boolean = false
    ): FileMetadata {
        val file = if (includeDeleted) {
            fileMapper.selectByIdIncludeDeleted(fileId)
        } else {
            fileMapper.selectById(fileId)
        }
        
        if (file == null) {
            throw BusinessException(404, "文件不存在")
        }
        
        if (file.userId != userId) {
            throw BusinessException(403, "无权访问该文件")
        }
        
        if (!includeDeleted && file.deleted) {
            throw BusinessException(404, "文件已被删除")
        }
        
        return file
    }
    
    /**
     * 验证并获取文件（包含权限检查）- 通过公开ID
     * 
     * @param publicId 文件公开ID
     * @param userId 用户ID
     * @param includeDeleted 是否包含已删除的文件
     * @return 文件实体
     * @throws BusinessException 文件不存在、无权限或已删除时抛出异常
     */
    fun validateAndGetFileByPublicId(
        publicId: String, 
        userId: Long, 
        includeDeleted: Boolean = false
    ): FileMetadata {
        val file = fileMapper.selectByPublicId(publicId)
            ?: throw BusinessException(404, "文件不存在")
        
        if (file.userId != userId) {
            throw BusinessException(403, "无权访问该文件")
        }
        
        if (!includeDeleted && file.deleted) {
            throw BusinessException(404, "文件已被删除")
        }
        
        return file
    }
    
    /**
     * 验证并获取文件夹（包含权限检查）- 通过数字ID
     * 
     * @param folderId 文件夹ID
     * @param userId 用户ID
     * @return 文件夹实体
     * @throws BusinessException 文件夹不存在、无权限或已删除时抛出异常
     */
    fun validateAndGetFolder(folderId: Long, userId: Long): FileFolder {
        val folder = folderMapper.selectById(folderId)
            ?: throw BusinessException(404, "文件夹不存在")
        
        if (folder.userId != userId) {
            throw BusinessException(403, "无权访问该文件夹")
        }
        
        if (folder.deleted) {
            throw BusinessException(404, "文件夹已被删除")
        }
        
        return folder
    }
    
    /**
     * 验证并获取文件夹（包含权限检查）- 通过公开ID
     * 
     * @param publicId 文件夹公开ID
     * @param userId 用户ID
     * @return 文件夹实体
     * @throws BusinessException 文件夹不存在、无权限或已删除时抛出异常
     */
    fun validateAndGetFolderByPublicId(publicId: String, userId: Long): FileFolder {
        val folder = folderMapper.selectByPublicId(publicId)
            ?: throw BusinessException(404, "文件夹不存在")
        
        if (folder.userId != userId) {
            throw BusinessException(403, "无权访问该文件夹")
        }
        
        if (folder.deleted) {
            throw BusinessException(404, "文件夹已被删除")
        }
        
        return folder
    }
    
    /**
     * 验证目标文件夹是否有效（可选）- 通过数字ID
     */
    fun validateTargetFolder(folderId: Long?, userId: Long) {
        folderId ?: return
        
        val folder = folderMapper.selectById(folderId)
            ?: throw BusinessException(404, "目标文件夹不存在")
        
        if (folder.deleted) {
            throw BusinessException(404, "目标文件夹已被删除")
        }
        
        if (folder.userId != userId) {
            throw BusinessException(403, "无权访问该文件夹")
        }
    }
    
    /**
     * 验证目标文件夹是否有效（可选）- 通过公开ID
     */
    fun validateTargetFolderByPublicId(folderPublicId: String?, userId: Long) {
        folderPublicId ?: return
        
        val folder = folderMapper.selectByPublicId(folderPublicId)
            ?: throw BusinessException(404, "目标文件夹不存在")
        
        if (folder.deleted) {
            throw BusinessException(404, "目标文件夹已被删除")
        }
        
        if (folder.userId != userId) {
            throw BusinessException(403, "无权访问该文件夹")
        }
    }
    
    /**
     * 验证文件所有权（批量）
     */
    fun validateFileOwnership(files: List<FileMetadata>, userId: Long) {
        files.forEach { file ->
            if (file.userId != userId) {
                throw BusinessException(403, "无权操作文件: ${file.fileName}")
            }
            if (file.deleted) {
                throw BusinessException(404, "文件已被删除: ${file.fileName}")
            }
        }
    }
    
    /**
     * 验证文件夹所有权（批量）
     */
    fun validateFolderOwnership(folders: List<FileFolder>, userId: Long) {
        folders.forEach { folder ->
            if (folder.userId != userId) {
                throw BusinessException(403, "无权操作文件夹: ${folder.name}")
            }
        }
    }
}

