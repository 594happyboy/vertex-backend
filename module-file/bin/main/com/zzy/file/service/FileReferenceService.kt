package com.zzy.file.service

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.zzy.common.exception.BusinessException
import com.zzy.file.entity.FileMetadata
import com.zzy.file.entity.FileReference
import com.zzy.file.entity.ReferenceType
import com.zzy.file.mapper.FileMapper
import com.zzy.file.mapper.FileReferenceMapper
import com.zzy.file.mapper.FolderMapper
import com.zzy.file.util.MarkdownFileExtractor
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 系统文件夹根目录名称常量
 * 与 SystemFolderManager.SYSTEM_ROOT 保持一致
 */
private const val SYSTEM_FOLDER_ROOT = "系统"

/**
 * 文件引用管理服务
 * 负责跟踪文件的引用情况，防止孤儿文件产生
 * @author ZZY
 * @date 2025-11-03
 */
@Service
class FileReferenceService(
    private val fileMapper: FileMapper,
    private val fileReferenceMapper: FileReferenceMapper,
    private val folderMapper: FolderMapper
) {
    
    private val logger = LoggerFactory.getLogger(FileReferenceService::class.java)
    
    /**
     * 通过公开ID获取文件
     */
    fun getFileByPublicId(publicId: String): FileMetadata {
        return fileMapper.selectByPublicId(publicId)
            ?: throw BusinessException(404, "文件不存在")
    }
    
    /**
     * 同步文档内容的文件引用
     * 每次保存文档时调用此方法，自动对比并更新引用关系
     * 
     * @param documentId 文档ID
     * @param markdownContent 文档内容（Markdown格式）
     * @return 引用变化统计（added, removed）
     */
    @Transactional(rollbackFor = [Exception::class])
    fun syncDocumentContentReferences(
        documentId: Long, 
        markdownContent: String?
    ): ReferenceChanges {
        logger.debug("同步文档文件引用: documentId={}", documentId)
        
        // 1. 从MD内容中提取当前所有文件引用
        val currentFileIds = MarkdownFileExtractor.extractFileIds(markdownContent)
        
        // 2. 查询数据库中已有的引用关系
        val existingRefs = fileReferenceMapper.selectList(
            QueryWrapper<FileReference>()
                .eq("reference_type", ReferenceType.DOCUMENT_CONTENT.value)
                .eq("reference_id", documentId)
        )
        val existingFileIds = existingRefs.map { it.fileId }.toSet()
        
        // 3. 计算差异
        val toAdd = currentFileIds - existingFileIds      // 新增的引用
        val toRemove = existingFileIds - currentFileIds   // 删除的引用
        
        logger.info("文档引用变化: documentId={}, 新增={}, 删除={}", 
            documentId, toAdd.size, toRemove.size)
        
        // 4. 删除不再引用的文件
        toRemove.forEach { fileId ->
            removeReference(fileId, ReferenceType.DOCUMENT_CONTENT.value, documentId)
        }
        
        // 5. 添加新引用的文件
        toAdd.forEach { fileId ->
            addReference(fileId, ReferenceType.DOCUMENT_CONTENT.value, documentId)
        }
        
        return ReferenceChanges(added = toAdd.size, removed = toRemove.size)
    }
    
    /**
     * 添加文件引用
     * 使用原子性操作确保线程安全
     */
    @Transactional(rollbackFor = [Exception::class])
    fun addReference(
        fileId: Long, 
        referenceType: String, 
        referenceId: Long,
        referenceField: String? = null
    ) {
        // 使用 INSERT IGNORE 避免重复，确保原子性操作
        val inserted = fileReferenceMapper.insertReferenceIgnoreDuplicate(
            fileId, referenceType, referenceId, referenceField
        )
        
        // 只有真正插入时才增加计数
        if (inserted > 0) {
            // 更新引用计数
            fileMapper.incrementReferenceCount(fileId)
            
            logger.info("添加文件引用: fileId={}, type={}, refId={}", 
                fileId, referenceType, referenceId)
        } else {
            logger.debug("引用已存在，跳过: fileId={}, type={}", fileId, referenceType)
        }
    }
    
    /**
     * 移除文件引用
     */
    @Transactional(rollbackFor = [Exception::class])
    fun removeReference(
        fileId: Long, 
        referenceType: String, 
        referenceId: Long,
        referenceField: String? = null
    ) {
        // 删除引用记录
        val deleted = fileReferenceMapper.delete(
            QueryWrapper<FileReference>()
                .eq("file_id", fileId)
                .eq("reference_type", referenceType)
                .eq("reference_id", referenceId)
                .apply { referenceField?.let { eq("reference_field", it) } }
        )
        
        if (deleted > 0) {
            // 更新引用计数
            fileMapper.decrementReferenceCount(fileId)
            
            logger.info("移除文件引用: fileId={}, type={}, refId={}, deleted={}", 
                fileId, referenceType, referenceId, deleted)
        }
    }
    
    /**
     * 移除对象的所有文件引用
     * 用于删除文档时，一次性清理所有关联引用
     */
    @Transactional(rollbackFor = [Exception::class])
    fun removeAllReferences(referenceType: String, referenceId: Long) {
        val references = fileReferenceMapper.selectList(
            QueryWrapper<FileReference>()
                .eq("reference_type", referenceType)
                .eq("reference_id", referenceId)
        )
        
        references.forEach { ref ->
            removeReference(ref.fileId, ref.referenceType, ref.referenceId, ref.referenceField)
        }
        
        logger.info("清理对象所有引用: type={}, refId={}, count={}", 
            referenceType, referenceId, references.size)
    }
    
    /**
     * 清理无引用文件
     * 只清理"系统"文件夹及其子文件夹下的无引用文件
     * 用户主动上传到普通文件夹的文件不会被清理
     */
    @Transactional(rollbackFor = [Exception::class])
    fun cleanupUnreferencedFiles(gracePeriodDays: Int = 7): Int {
        val threshold = LocalDateTime.now().minusDays(gracePeriodDays.toLong())
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        
        val unreferencedFiles = fileMapper.selectUnreferencedFiles(threshold.format(formatter))
        
        var deletedCount = 0
        var skippedCount = 0
        
        unreferencedFiles.forEach { file ->
            // 检查文件是否在"系统"文件夹下（递归检查）
            val isInSystemFolder = isFileInSystemFolder(file.folderId)
            
            if (isInSystemFolder) {
                try {
                    fileMapper.softDelete(file.id!!)
                    deletedCount++
                    logger.info("清理系统文件夹下的无引用文件: fileId={}, fileName={}, uploadTime={}", 
                        file.id, file.fileName, file.uploadTime)
                } catch (e: Exception) {
                    logger.error("清理文件失败: fileId={}", file.id, e)
                }
            } else {
                skippedCount++
                logger.debug("跳过用户文件夹中的文件: fileId={}, fileName={}", 
                    file.id, file.fileName)
            }
        }
        
        if (deletedCount > 0 || skippedCount > 0) {
            logger.info("无引用文件清理完成: 清理 {} 个，跳过 {} 个（宽限期{}天）", 
                deletedCount, skippedCount, gracePeriodDays)
        }
        
        return deletedCount
    }
    
    /**
     * 递归检查文件是否在"系统"文件夹下
     * 
     * 此方法用于判断文件是否应该被自动清理。
     * 只有系统文件夹下的无引用文件才会被清理，用户文件夹的文件不会被清理。
     * 
     * @param folderId 文件所在的文件夹ID，null表示根目录
     * @return true表示在系统文件夹下，false表示不在
     */
    private fun isFileInSystemFolder(folderId: Long?): Boolean {
        // 文件在根目录，不是系统文件夹
        if (folderId == null) return false
        
        var currentFolderId: Long? = folderId
        val visited = mutableSetOf<Long>() // 防止循环引用
        
        while (currentFolderId != null) {
            // 防止无限循环
            if (currentFolderId in visited) {
                logger.warn("检测到文件夹循环引用: folderId={}", currentFolderId)
                return false
            }
            visited.add(currentFolderId)
            
            val folder = folderMapper.selectById(currentFolderId)
            // 文件夹不存在或已删除
            if (folder == null || folder.deleted) return false
            
            // 检查是否是"系统"文件夹（且父文件夹为null，即根目录）
            if (folder.name == SYSTEM_FOLDER_ROOT && folder.parentId == null) {
                return true
            }
            
            // 继续向上查找
            currentFolderId = folder.parentId
        }
        
        // 没有找到"系统"文件夹
        return false
    }
    
    /**
     * 获取文件的所有引用信息
     */
    fun getFileReferences(fileId: Long): List<FileReference> {
        return fileReferenceMapper.selectList(
            QueryWrapper<FileReference>().eq("file_id", fileId)
        )
    }
    
    /**
     * 获取孤儿文件列表（无引用且超过指定天数）
     * 只返回系统文件夹下的孤儿文件
     */
    fun getOrphanFiles(gracePeriodDays: Int = 7, limit: Int = 100): List<Long> {
        val threshold = LocalDateTime.now().minusDays(gracePeriodDays.toLong())
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        
        return fileMapper.selectUnreferencedFiles(threshold.format(formatter))
            .filter { isFileInSystemFolder(it.folderId) }
            .take(limit)
            .mapNotNull { it.id }
    }
}

/**
 * 引用变化统计
 */
data class ReferenceChanges(
    val added: Int,
    val removed: Int
)

