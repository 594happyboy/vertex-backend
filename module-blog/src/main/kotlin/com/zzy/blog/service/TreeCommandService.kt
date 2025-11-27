package com.zzy.blog.service

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper
import com.zzy.blog.dto.NodeType
import com.zzy.blog.dto.TreeReorderItem
import com.zzy.blog.dto.TreeReorderRequest
import com.zzy.blog.entity.Document
import com.zzy.blog.entity.Group
import com.zzy.blog.mapper.DocumentMapper
import com.zzy.blog.mapper.GroupMapper
import com.zzy.common.context.AuthContextHolder
import com.zzy.common.exception.ForbiddenException
import com.zzy.common.exception.ResourceNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 树形结构写操作：拖拽移动 + 排序
 */
@Service
class TreeCommandService(
    private val groupMapper: GroupMapper,
    private val documentMapper: DocumentMapper,
    private val directoryTreeService: DirectoryTreeService
) {

    private val logger = LoggerFactory.getLogger(TreeCommandService::class.java)

    @Transactional
    fun reorder(request: TreeReorderRequest) {
        val userId = AuthContextHolder.getCurrentUserId()
        validateParent(request.parentId, userId)

        val groupItems = request.items.filter { it.nodeType == NodeType.GROUP }
        val documentItems = request.items.filter { it.nodeType == NodeType.DOCUMENT }

        val groups = loadGroups(groupItems.map { it.nodeId }, userId)
        val documents = loadDocuments(documentItems.map { it.nodeId }, userId)

        preventCycle(groupItems.map { it.nodeId }.toSet(), request.parentId, userId)

        val normalizedItems = normalizeOrder(request.items)
        logger.info("开始重排: parentId={}, 总节点数={}", request.parentId, normalizedItems.size)
        
        normalizedItems.forEachIndexed { index, item ->
            val orderIndex = (index + 1) * ORDER_STEP
            when (item.nodeType) {
                NodeType.GROUP -> {
                    val group = groups[item.nodeId]!!
                    val oldParentId = group.parentId
                    logger.info("处理分组: id={}, name={}, oldParentId={}, newParentId={}, orderIndex={}", 
                        group.id, group.name, oldParentId, request.parentId, orderIndex)
                    updateGroup(group, request.parentId, orderIndex)
                }
                NodeType.DOCUMENT -> {
                    val document = documents[item.nodeId]!!
                    val oldGroupId = document.groupId
                    logger.info("处理文档: id={}, title={}, oldGroupId={}, newGroupId={}, orderIndex={}", 
                        document.id, document.title, oldGroupId, request.parentId, orderIndex)
                    updateDocument(document, request.parentId, orderIndex)
                }
            }
        }

        directoryTreeService.clearCache(userId)
        logger.info(
            "完成树重排: userId={}, parentId={}, groups={}, documents={}",
            userId,
            request.parentId,
            groupItems.size,
            documentItems.size
        )
    }

    private fun validateParent(parentId: Long?, userId: Long) {
        if (parentId == null) {
            return
        }
        val parent = groupMapper.selectById(parentId)
        if (parent == null || parent.userId != userId || parent.deleted) {
            throw ResourceNotFoundException("父分组不存在或无权访问")
        }
    }

    private fun loadGroups(ids: List<Long>, userId: Long): Map<Long, Group> {
        if (ids.isEmpty()) return emptyMap()
        val groups = groupMapper.selectBatchIds(ids)
        if (groups.size != ids.size) {
            throw ResourceNotFoundException("部分分组不存在")
        }
        if (groups.any { it.userId != userId }) {
            throw ForbiddenException("无权操作部分分组")
        }
        return groups.associateBy { it.id!! }
    }

    private fun loadDocuments(ids: List<Long>, userId: Long): Map<Long, Document> {
        if (ids.isEmpty()) return emptyMap()
        val documents = documentMapper.selectBatchIds(ids)
        if (documents.size != ids.size) {
            throw ResourceNotFoundException("部分文档不存在")
        }
        if (documents.any { it.userId != userId }) {
            throw ForbiddenException("无权操作部分文档")
        }
        return documents.associateBy { it.id!! }
    }

    private fun preventCycle(movingGroupIds: Set<Long>, parentId: Long?, userId: Long) {
        if (movingGroupIds.isEmpty() || parentId == null) {
            return
        }
        var currentParent = parentId
        while (currentParent != null) {
            if (movingGroupIds.contains(currentParent)) {
                throw IllegalArgumentException("不能将分组移动到自身或子分组下")
            }
            val parent = groupMapper.selectById(currentParent) ?: break
            if (parent.userId != userId) break
            currentParent = parent.parentId
        }
    }

    private fun normalizeOrder(items: List<TreeReorderItem>): List<TreeReorderItem> {
        return items.sortedWith(compareBy<TreeReorderItem>({ typeWeight(it.nodeType) }, { it.orderIndex }))
    }

    private fun updateGroup(group: Group, parentId: Long?, orderIndex: Int) {
        val wrapper = UpdateWrapper<Group>()
            .eq("id", group.id)
            .set("parent_id", parentId)
            .set("sort_index", orderIndex)
        groupMapper.update(null, wrapper)
        logger.debug("已更新分组: id={}, parentId={}, sortIndex={}", group.id, parentId, orderIndex)
    }

    private fun updateDocument(document: Document, parentId: Long?, orderIndex: Int) {
        val wrapper = UpdateWrapper<Document>()
            .eq("id", document.id)
            .set("group_id", parentId)
            .set("sort_index", orderIndex)
        documentMapper.update(null, wrapper)
        logger.debug("已更新文档: id={}, groupId={}, sortIndex={}", document.id, parentId, orderIndex)
    }

    private fun typeWeight(nodeType: NodeType): Int {
        return when (nodeType) {
            NodeType.GROUP -> 0
            NodeType.DOCUMENT -> 1
        }
    }

    companion object {
        private const val ORDER_STEP = 100
    }
}
