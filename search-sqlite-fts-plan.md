## 文档搜索（SQLite FTS）整体方案设计

> 版本：v1.0  
> 场景：个人博客/知识库文档标题 + 正文模糊搜索  
> 主库：MySQL（现有 `documents` 表 + file 模块）  
> 辅助索引：SQLite FTS5

---

## 1. 目标与约束

- **目标**
  - 支持对文档 **标题 + 正文内容** 的模糊搜索。
  - 支持 **空格分词的多关键词搜索**：`java redis 缓存` 表示文档中同时包含这几个词即可，顺序不限。
  - 搜索结果按相关度排序，标题命中权重更高。
  - 提供命中片段（snippet）以便前端展示高亮摘要。

- **约束/前提**
  - 主业务数据仍然存储在 **MySQL** 中（`documents` 表 + 文件模块保存实际文档文件）。
  - SQLite 仅作为 **只读为主的全文索引** 存储，坏了可以通过重建恢复。
  - 部署环境为 **2c2g VPS**：并发不高，读多写少，适合 SQLite 作为全文索引。
  - 用户可接受 **按空格分词的词级搜索**，暂不要求复杂中文分词。
  - 可以在 SQLite 中保存一份文档的纯文本内容。

### v1 实现范围

- **包含：**
  - 索引 Markdown / TXT 文档的标题和正文纯文本。
  - 使用 SQLite FTS5 实现 `GET /documents/search` 接口，支持 `q/groupId/page/size`。
  - 支持按空格拆分的多关键词 AND 匹配与前缀匹配（`token*`），返回 `snippet + score`。

- **不包含（后续可选）：**
  - 解析并索引 PDF / Word 等二进制文档内容。
  - 自定义中文分词 tokenizer。
  - 在 SQLite 中维护 `status/tags` 等扩展字段。

---

## 2. 架构概览

### 2.1 总体架构

整体采用「**主库 + 辅助搜索索引库**」模型：

- **MySQL 主库**
  - 表：`documents`（已有）、文件相关表（file 模块）。
  - 职责：存储真实元数据、权限字段、排序字段等。
  - 所有权限判断、分组过滤、发布状态等逻辑仍基于 MySQL。

- **SQLite 搜索索引库**
  - 单独一个 DB 文件，例如：`/data/search/document-index.db`。
  - 内部使用 FTS5 虚拟表 `document_index` 存储：`doc_id/user_id/group_id/title/content/...`。
  - 通过 MySQL 中的 `document.id` 与业务数据关联。

### 2.2 数据流

- **创建文档**
  1. 前端上传 Markdown/TXT 文件并创建 `Document`（现有流程）。
  2. 服务在处理文件时读取内容字符串（现有用于引用解析）。
  3. 将 `Document` 元数据 + 文本内容发送给 `DocumentSearchIndexService`（异步）。
  4. `DocumentSearchIndexService` 将数据写入 SQLite FTS 表。

- **更新文档文件**
  1. 替换文件后，重新读取内容。
  2. 调用 `DocumentSearchIndexService` 执行 `INSERT OR REPLACE` 更新对应 FTS 记录。

- **删除文档**
  1. `documents` 里执行软删除（已有）。
  2. 同时调用 `documentSearchIndexService.deleteByDocumentId(id)`，从 FTS 表中删除对应 `doc_id` 的行。

- **全文搜索**
  1. Controller 接收搜索请求（`q`、`limit`、`groupId` 等）。
  2. Service 将用户输入的 `q` 转换为 FTS `MATCH` 字符串。
  3. 调用 `DocumentSearchIndexService.search(...)` 在 SQLite 中查出候选结果。
  4. 根据 `user_id`、`status` 等做二次过滤（若需要，可回查 MySQL）。
  5. 返回带有 snippet/score 的搜索结果给前端。

---

## 3. SQLite 索引库设计

### 3.1 SQLite DB 文件

- 文件路径建议：
  - 宿主机：`/data/vertex/search/document-index.db`
  - Docker 容器内：挂载为 `/app/data/search/document-index.db`
- 由后端应用启动时负责：
  - 检查文件是否存在；若不存在则创建并初始化（执行 DDL）。

### 3.2 FTS5 表结构

使用 FTS5 虚拟表存储文档索引信息：

```sql
CREATE VIRTUAL TABLE document_index USING fts5(
  doc_id UNINDEXED,         -- Document.id（业务主键，用于返回与删改）
  user_id UNINDEXED,        -- 所属用户，用于权限隔离
  group_id UNINDEXED,       -- 所属分组，用于筛选
  title,                    -- 标题（参与全文索引）
  content,                  -- 正文纯文本（参与全文索引）
  created_at UNINDEXED,     -- 创建时间（字符串存储）
  updated_at UNINDEXED,     -- 更新时间（字符串存储）
  tokenize = 'unicode61 tokenchars ''.#_''',
  prefix = '2 3 4'
);
```

设计要点：

- `UNINDEXED` 列：只存储不参与倒排索引，主要用于过滤与排序；可以减少索引体积。
- `title`、`content` 参与 FTS 索引：
  - 能够支持 `MATCH` 查询、`bm25` 评分、`snippet` 高亮等。
- `tokenize = 'unicode61'`：
  - 对英文/数字支持良好，对中文则以“整块文本”处理，已满足目前“空格分词 + 词级匹配”的需求。
  - 后续如果需要更好的中文体验，可以考虑自定义 tokenizer。
- `prefix = '2 3 4'`：
  - 支持 2～4 长度的前缀匹配，例如 `jav*`、`doc*`，提升模糊搜索体验。

### 3.3 元数据表（可选）

为管理索引库状态，建议增设一个简单的元数据表：

```sql
CREATE TABLE IF NOT EXISTS meta (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL
);

INSERT OR IGNORE INTO meta(key, value) VALUES
  ('schema_version', '1'),
  ('last_rebuild_time', '');
```

用途：

- 标记当前 SQLite 索引的 schema 版本。
- 记录上次全量重建时间，便于运维排查问题。

---

## 4. 索引内容提取与同步策略

### 4.1 支持的文档类型与提取规则

结合现有 `DocumentConstants.SUPPORTED_EXTENSIONS`，先重点支持：

- **Markdown (`.md`)**：
  - 读取原始 Markdown 文本。
  - 可选：做极简的 Markdown → 纯文本转换（去掉部分标记，如 `#`、`*` 等），减少噪声。

- **纯文本 (`.txt`)**：
  - 直接将文件内容作为 `content`。

- **PDF 等其他类型**（视情况）：
  - v1 可以只依赖 **标题搜索**（`content` 为空）。
  - 后续如需，可以接入专门的解析库提取文本再索引。

### 4.2 与现有 Service 的集成点

关键方法（简要说明，不改现有行为）：

- `DocumentService.createDocument(request, file)`
  - 当前逻辑中已在处理 Markdown 文件时读取 `content`，并调用 `asyncFileReferenceService.syncDocumentContentReferencesAsync`。
  - 新增：在同一处读取到 `content` 后，调用 **异步索引服务**：
    - `documentSearchIndexService.indexDocumentAsync(document, content)`
  - 好处：避免重复打开/读取文件。

- `DocumentService.updateDocumentFile(id, file)`
  - 更新文件后，同样读取新内容，调用 `indexDocumentAsync` 执行覆盖索引。

- `DocumentService.deleteDocument(id)`
  - 文档软删除时，调用 `documentSearchIndexService.deleteByDocumentId(id)`，从 FTS 删除记录。

### 4.3 索引服务接口设计（示意）

定义一个独立的索引服务（在单独 `module-search` 中）：

- `DocumentSearchIndexService`
  - `fun indexDocument(doc: Document, content: String)`
  - `fun deleteByDocumentId(docId: Long)`
  - `fun search(userId: Long, q: String, groupId: Long?, limit: Int, offset: Int): List<SearchResult>`
  - `fun rebuildAll()`（全量重建）

同时提供异步包装：

- `AsyncDocumentSearchIndexService`
  - 在内部线程池中异步执行 `indexDocument`/`deleteByDocumentId`，避免阻塞主请求线程。

`module-search` 模块主要职责：

- 管理 SQLite 连接与基础配置（PRAGMA 初始化、DB 文件路径等）。
- 提供 `DocumentSearchIndexService` / `AsyncDocumentSearchIndexService` 的具体实现。
- 暴露索引重建任务入口（HTTP / 命令行 / 定时任务），供运维或管理员触发。

该模块只处理与搜索相关的基础设施逻辑，不参与业务权限判断。

### 4.4 同步策略与一致性

- **实时 + 异步**
  - 写入主库事务成功后，即提交一个异步任务去更新 SQLite 索引。
  - 保证主功能（文档 CRUD）不受索引失败影响。

- **容错与补偿**
  - 异步任务失败时记录业务日志（含 `doc_id`）。
  - 提供 `rebuildAll()` 管理功能，可从 MySQL + 文件系统全量重建索引。

---

## 5. 搜索查询与多关键词匹配

### 5.1 查询输入预处理

输入示例：

- `"java redis 缓存"`

处理流程：

1. `q = q.trim()`，并限制最大长度（如 128 字符）。
2. 按空格拆分：`tokens = q.split(/\\s+/)`。
3. 过滤掉空 token，对每个 token 做简单转义，避免 FTS 语法错误：
   - 删除 FTS 特殊字符（例如 `"`, `*`, `:`, `'`, `(`, `)` 等）。
   - 连续标点统一替换为空格，避免生成异常 token。
4. 组合为 FTS `MATCH` 表达式：
   - 默认策略：**所有词必须出现（AND）**。
   - 构造形如：`token1* AND token2* AND token3*`。

示例：

- 输入：`java redis 缓存`
  - FTS 查询：`"java* AND redis* AND 缓存*"`

### 5.2 SQLite 查询语句示例

基础查询 SQL：

```sql
SELECT
  doc_id,
  user_id,
  group_id,
  title,
  snippet(document_index, 1, '<em>', '</em>', '...', 20) AS snippet,
  bm25(document_index, 10.0, 1.0) AS score,
  created_at,
  updated_at
FROM document_index
WHERE document_index MATCH :ftsQuery
  AND user_id = :userId
  AND (:groupId IS NULL OR group_id = :groupId)
ORDER BY score ASC
LIMIT :limit OFFSET :offset;
```

说明：

- `MATCH :ftsQuery` 使用上一步生成的多关键词表达式。
- `user_id`：保证不同用户空间间完全隔离。
- `group_id`：可选分组过滤；若为 `NULL` 则不过滤。
- `bm25(document_index, 10.0, 1.0)`：
  - 标题列权重更高（10），正文列权重较低（1），标题命中的结果排序更靠前。
- `snippet(...)`：截取正文部分文本（`content` 列），并使用 `<em>...</em>` 包裹命中词，供前端高亮展示。

---

## 6. API 与系统集成方案

### 6.1 新增/复用的接口设计

有两种方案：

- **方案 A：新增独立搜索接口**（推荐）
  - `GET /documents/search`
  - Query 参数：`q`, `groupId?`, `page?`, `size?`
  - 由 `DocumentSearchIndexService` 驱动，返回：
    - `id`, `title`, `groupId`, `snippet`, `score`, `createdAt`, `updatedAt`。
  - 与现有 `/documents/query`（游标分页列表）职责清晰分离。
  - 响应数据结构建议使用 page/size 分页，示例：
    ```json
    {
      "items": [
        {
          "id": 1,
          "title": "示例标题",
          "groupId": 10,
          "snippet": "...",
          "score": 0.123,
          "createdAt": "2025-01-01T00:00:00",
          "updatedAt": "2025-01-02T00:00:00"
        }
      ],
      "page": 1,
      "size": 20,
      "total": 100
    }
    ```
  - `total` 可根据需要选择是否返回；若不需要总数，可只返回 `items/page/size`。

- **方案 B：在现有 `/documents/query` 中内嵌全文搜索**
  - 若 `q` 为空：走 MySQL + 游标分页（现有逻辑）。
  - 若 `q` 非空：走 SQLite FTS 搜索，忽略游标逻辑，使用 page/size 或 limit/offset。
  - 前端根据是否传 `q`，在 UI 上展示列表或搜索结果。

> 推荐：**先实现方案 A**，便于渐进迁移与调试，不破坏现有列表接口行为。

### 6.2 Controller / Service 映射

- 新增 `DocumentSearchController` 或在 `DocumentController` 中增加搜索方法：
  - 负责接收 `q/groupId/page/size` 等参数。
  - 从 `AuthContextHolder` 读取当前用户/访客上下文。
  - 调用 `DocumentSearchIndexService.search(...)` 获取候选。
  - 按需回查 MySQL 进行状态/权限过滤。

- Service 侧新增 `DocumentSearchService`：
  - 封装输入预处理、SQLite FTS 调用、MySQL 回查与结果组装。

---

## 7. 性能与调优（针对 2c2g VPS）

### 7.1 SQLite 配置建议

应用启动或首次打开 SQLite 连接时执行若干 PRAGMA：

```sql
PRAGMA journal_mode = WAL;      -- 写前日志模式，提升并发读性能
PRAGMA synchronous = NORMAL;    -- 在可靠性与性能之间折中
PRAGMA cache_size = -20000;     -- 约 20MB 的 page cache（负值表示 KB）
PRAGMA temp_store = MEMORY;     -- 临时表优先使用内存
```

### 7.2 索引体积与资源评估

- FTS 索引大小大致为纯文本内容的 30%～70%。
- 在 2c2g 的 VPS 场景下，只要文档总体文本量在几百 MB 以内，SQLite 完全可以胜任。
- 文档数量“较少”时，搜索延迟预计在几十毫秒级别。

### 7.3 并发写入与锁

- 写入场景主要是创建/更新/删除文档，频率不会特别高。
- 采用单线程异步写入队列，可以：
  - Avoid 并发写锁竞争。
  - 保证写入顺序（方便调试和重放）。

---

## 8. 容错与重建策略

### 8.1 常见故障场景

- 索引库文件丢失或损坏。
- 异步索引任务因异常中断，导致部分文档未入索引或索引未更新。

### 8.2 重建流程

提供一个管理接口或命令行任务：

1. 清空现有 FTS 表：`DELETE FROM document_index;` 或重新建表。
2. 从 MySQL 中扫描所有 `documents`（`deleted = 0`）。
3. 通过 file 模块读取每个文档文件，提取文本内容。
4. 调用 `DocumentSearchIndexService.indexDocument(...)` 逐个写入索引。
5. 更新 `meta.last_rebuild_time`。

> 由于文档数量不大，全量重建可以在后台一次性完成，不会给 2c2g 带来太大压力。

---

## 9. 实施步骤建议

按阶段逐步落地，降低风险：

1. **搭建 SQLite 索引库基础设施**
   - 实现 SQLite 连接管理与 PRAGMA 初始化。
   - 创建 `document_index` FTS 表和 `meta` 表。

2. **实现 `DocumentSearchIndexService`**
   - 封装插入/更新/删除/查询 API。
   - 实现 FTS 查询语句与结果映射。

3. **接入文档生命周期事件**
   - 在 `createDocument`/`updateDocumentFile`/`deleteDocument` 等关键路径调用索引服务（异步）。
   - 先只在开发环境验证，确保对原有功能无副作用。

4. **新增搜索 API**
   - 实现 `GET /documents/search`（方案 A）。
   - 支持 `q/groupId/page/size`，返回 snippet + score。

5. **全量重建 & 线上验证**
   - 提供重建索引入口（仅管理员可调用）。
   - 在测试/预发布环境跑一次全量重建，校验搜索效果与性能。

6. **后续优化（可选）**
   - 将 `status` 等字段一并写入 SQLite，减少回表。
   - 尝试自定义中文分词器，进一步提升搜索体验。
   - 根据实际使用情况调整 `bm25` 的列权重与 `prefix` 配置。

---

## 10. 后续扩展方向

- 支持更多文档类型（如 PDF、Word），通过专门的解析器抽取纯文本索引。
- 在搜索结果中加入更多维度：标签（tags）、文档类型过滤（md/pdf/txt）等。
- 为访客模式提供独立的索引视图，只索引 `status = 'published'` 的文档，进一步简化权限判断。

## 11. 验证与测试要点

- **输入边界**
  - `q` 为空或全是空白时的处理策略（直接返回空结果或回退到列表接口）。
  - 超长 `q`（超过 128 字符）是否截断或报参数错误。

- **匹配规则**
  - 单关键词、多关键词、前缀查询（例如 `jav`）的预期行为是否符合设计。
  - 含有大量标点、emoji 等特殊字符的输入不会导致 FTS MATCH 报错。

- **索引同步**
  - 创建、更新、删除文档后，索引中对应 `doc_id` 是否在预期时间内更新（例如几秒内）。
  - 软删除的文档不会再出现在搜索结果中。

- **重建与容错**
  - 手动触发 `rebuildAll()` 后，索引结果与 MySQL 中的文档记录是否一致。
  - SQLite 索引文件被删除或损坏时，重建流程是否可顺利恢复到可用状态。

---
