# 文档树重构方案（文件夹优先 + 拖拽移动）

本方案用于重构文档目录树，使「默认文件夹在前、文件在后」并支持文档/文件夹的拖拽移动与排序。允许破坏性改动，不兼容旧接口。

## 目标与范围
- 目录树默认排序：同一父节点下先文件夹、后文件，再按自定义顺序。
- 交互：文档、文件夹都可拖拽到任意文件夹或同级位置，移动和排序一次请求完成。
- 范围：`DocumentController` 及目录树/排序相关服务、DTO、缓存、批量上传；前端目录树与拖拽交互。

## 现状痛点
- `DirectoryTreeService.sortChildren` 以 `nodeType.value` 排序，实际会导致文档排在分组前。
- 排序接口拆成 `/api/sort/groups` 与 `/api/sort/documents`，无法在一次操作内完成「移动 + 排序」且不支持跨类型原子更新。
- `sortIndex` 语义分散，上传/创建默认写 0，缺少「插入指定位置」能力。

## 重构总览
1) 统一节点视图  
   - DTO：`TreeNode { id, nodeType(group|document), parentId, orderIndex, name, type?, children[] }`。  
   - 排序规则：`typeWeight(nodeType) asc`（group=0, document=1）→ `orderIndex asc`。后端和前端一致。

2) 统一操作入口  
   - 新增 `TreeCommandService`（或重写 `SortService`）处理「移动 + 排序」事务，内部同时更新分组/文档并清缓存。  
   - 删除旧的 `/api/sort/groups/reorder`、`/api/sort/documents/reorder`；改为单一「树节点重排」接口。

3) 数据模型与默认值  
   - 继续复用 `groups.parent_id`、`documents.group_id`，但重新定义 `sort_index` 语义：**同一父节点下的顺序序号**。  
   - 默认插入策略：同父节点下按类型取当前最大 `orderIndex` + 100，避免频繁重排；必要时有压缩逻辑。  
   - 目录树查询时按 `typeWeight + orderIndex` 排序，保证文件夹在前。

4) 缓存  
   - 仍用 Redis 缓存目录树，任何移动/创建/删除/批量上传完成后统一调用 `clearCache(userId)`。

## API 设计（破坏性）
- `GET /api/tree`：替换现有目录树接口，返回 `TreeNode[]`，已按“文件夹优先”排序；`cached` 字段保留。
- `POST /api/tree/reorder`：单接口支持拖拽移动与排序。请求体示例：
  ```json
  {
    "parentId": 123,            // 目标父节点；null 表示根
    "items": [
      { "nodeId": 12, "nodeType": "group", "orderIndex": 100 },
      { "nodeId": 88, "nodeType": "document", "orderIndex": 200 }
    ]
  }
  ```
  规则：一次请求只描述同一个父节点下的完整新顺序。后端需要做循环检测（禁止把父拖进子）。
- `POST /api/groups`：创建分组时由服务层分配 `orderIndex`（目标父节点下末尾）。  
- `POST /api/documents/upload` / `create`：上传成功后根据所属分组分配 `orderIndex`。  
- 批量上传：解包时先生成所有分组，再生成文档，分组与文档的 `orderIndex` 连续写入，确保“文件夹在前”。

## 服务层流程
- **排序比较器**：`typeWeight(group=0, document=1)` → `orderIndex`。`DirectoryTreeService` 构树时使用；`buildDirectoryTree` 去掉原 `nodeType.value` 比较。
- **TreeCommandService.reorder(parentId, items)**  
  - 校验用户、节点归属、父子循环。  
  - 将 `items` 按 `typeWeight + orderIndex` 重新计算规范化序号（如 100,200,...）写回对应表。  
  - 更新完成后清理目录树缓存。
- **移动/拖拽落点计算**：前端给出新父节点与相对序号；后端不关心「before/after 谁」，只接受最终列表。

## 迁移步骤
1. 数据回填：按旧排序规则为每个父节点重新生成 `order_index`（分组先、文档后，步长 100）。  
2. 删除旧排序接口与对应前端调用，替换为 `/api/tree/reorder`。  
3. 调整 `DirectoryTreeService.sortChildren` 与所有创建/上传/批量上传入口的默认排序写入。  
4. 完成后清空目录树缓存。

## 前端适配指南
- 列表/树展示：以 `typeWeight`（folder=0, file=1）+ `orderIndex` 排序渲染，默认不允许文件夹后置；UI 图标按 `nodeType` 渲染。  
- 拖拽交互：  
  1) 允许把文档/文件夹拖到任意文件夹或根；禁止拖进文件。  
  2) 落点计算：拿到目标父节点下的最新节点列表，前端重新排出 `orderIndex`（100 递增）并调用 `POST /api/tree/reorder`。  
  3) 成功后更新本地树并触发刷新；失败回滚本地状态。  
- 批量上传/创建：上传成功后直接刷新 `GET /api/tree`，无需手动排序。  
- 兼容性：删除旧的分组/文档独立排序调用，统一改用新接口；DTO 字段名称使用 `orderIndex`、`parentId`、`nodeType`。

## 非目标（本轮不做）
- 权限模型扩展（仍按用户私有）。  
- 文档内容层面的移动或引用更新。  
- 回收站/版本管理。
