# Module Search - 文档全文搜索模块

基于 SQLite FTS5 的轻量级文档全文搜索实现。

## 功能特性

- ✅ 标题 + 正文全文搜索
- ✅ 多关键词 AND 匹配（空格分词）
- ✅ 前缀匹配支持（`jav*` 可匹配 `java`）
- ✅ BM25 相关度评分
- ✅ 高亮片段（snippet）返回
- ✅ 异步索引更新
- ✅ 索引重建功能

## 快速开始

### 1. 配置

在 `application.yml` 中添加配置（可选，已有默认值）：

```yaml
search:
  db-path: data/search/document-index.db  # SQLite 数据库文件路径
  auto-init-schema: true                   # 是否自动初始化表结构
  query-timeout: 5000                      # 查询超时（毫秒）
  max-query-length: 128                    # 最大查询关键词长度
```

### 2. 使用搜索 API

#### 搜索文档

```http
GET /documents/search?q=java+redis&groupId=10&page=1&size=20
```

**参数说明：**
- `q` (必填): 搜索关键词，支持空格分词
- `groupId` (可选): 按分组过滤
- `page` (可选): 页码，默认 1
- `size` (可选): 每页大小，默认 20

**响应示例：**
```json
{
  "items": [
    {
      "id": 1,
      "title": "Java Redis 缓存实践",
      "groupId": 10,
      "snippet": "...使用 <em>Java</em> 和 <em>Redis</em> 实现分布式缓存...",
      "score": 0.123,
      "createdAt": "2025-01-01T00:00:00",
      "updatedAt": "2025-01-02T00:00:00"
    }
  ],
  "page": 1,
  "size": 20,
  "total": null
}
```

### 3. 重建索引（管理员）

当索引出现问题或需要全量重建时：

```http
POST /admin/search-index/rebuild
```

此接口会：
1. 从 MySQL 中读取所有未删除的文档
2. 读取 md/txt 文件内容
3. 清空并重建 FTS 索引

## 技术实现

### 架构

```
MySQL (主库)          SQLite FTS5 (索引库)
    │                        │
    ├─ documents 表          ├─ document_index (FTS5 虚拟表)
    ├─ file_metadata         └─ meta (元数据表)
    └─ ...
         │
         ▼
   DocumentService
         │
         ├─ create/update/delete 文档
         │
         ▼
   AsyncDocumentSearchIndexService (异步)
         │
         ▼
   DocumentSearchIndexService
         │
         ▼
   SQLite FTS5 索引
```

### 索引同步机制

- **创建文档**: 文档创建成功后，异步提取内容并索引
- **更新文件**: 文件更新后，异步重新索引
- **删除文档**: 软删除后，异步删除索引记录

### FTS5 表结构

```sql
CREATE VIRTUAL TABLE document_index USING fts5(
  doc_id UNINDEXED,         -- 文档 ID
  user_id UNINDEXED,        -- 用户 ID（权限隔离）
  group_id UNINDEXED,       -- 分组 ID
  title,                    -- 标题（参与全文索引）
  content,                  -- 正文（参与全文索引）
  created_at UNINDEXED,
  updated_at UNINDEXED,
  tokenize = 'unicode61 tokenchars ''.#_''',
  prefix = '2 3 4'
);
```

### 搜索查询处理

用户输入 → 预处理 → FTS 查询 → 结果返回

**示例：**
- 输入: `java redis 缓存`
- 预处理: `java* AND redis* AND 缓存*`
- FTS 查询: `MATCH 'java* AND redis* AND 缓存*'`
- BM25 排序: 标题权重 10.0，正文权重 1.0

## 性能优化

### SQLite 配置

```sql
PRAGMA journal_mode = WAL;      -- 写前日志，提升并发读
PRAGMA synchronous = NORMAL;    -- 平衡可靠性与性能
PRAGMA cache_size = -20000;     -- 20MB 缓存
PRAGMA temp_store = MEMORY;     -- 临时表使用内存
```

### 性能指标（2c2g VPS）

- 索引体积: ~30%-70% 原文大小
- 搜索延迟: 几十毫秒（数千文档规模）
- 并发支持: 读多写少场景表现良好

## 依赖模块

- `common`: 公共工具与异常定义
- `module-blog`: 文档 CRUD 集成

## 限制与注意事项

1. **v1 范围**:
   - 仅支持 md/txt 文件内容索引
   - PDF/Word 等只索引标题
   - 使用 unicode61 tokenizer（空格分词）

2. **权限**:
   - 搜索结果自动按 `user_id` 隔离
   - 访客/发布状态过滤由上层处理

3. **异步索引**:
   - 索引更新有几秒延迟
   - 失败会记录日志，可通过重建恢复

## 扩展方向

- [ ] 支持 PDF/Word 内容提取
- [ ] 自定义中文分词 tokenizer
- [ ] 在索引中增加 `status/tags` 字段
- [ ] 搜索结果缓存
- [ ] 搜索日志与分析

## 维护

### 查看索引状态

```sql
-- 连接 SQLite 数据库
sqlite3 data/search/document-index.db

-- 查看索引记录数
SELECT COUNT(*) FROM document_index;

-- 查看元数据
SELECT * FROM meta;
```

### 故障排查

1. **索引未更新**: 检查异步任务日志，必要时调用重建接口
2. **搜索无结果**: 确认关键词格式、用户权限、文档状态
3. **性能下降**: 检查 SQLite 文件大小、WAL 日志、索引碎片

---

> 详细设计文档请参考：[search-sqlite-fts-plan.md](../search-sqlite-fts-plan.md)
