## 后端详细设计文档（个人博客/知识库）

版本: v1.0  
作者: 项目组  
最后更新: 2025-10-18

---

### 1. 概要

- **目标**: 提供安全、稳定、清晰的 API，为前端博客/知识库提供用户鉴权、分组与文档管理、只读游客访问、发布控制与排序拖拽等能力。
- **技术栈建议**: Spring Boot 3.x、Spring Security、JWT、JPA（Hibernate）、MySQL/PostgreSQL、Redis（可选）、MinIO/S3（对象存储，可选）。
- **部署建议**: Docker 化部署，Nginx 反向代理，HTTPS，日志聚合（ELK/EFK）。

---

### 2. 服务与模块划分

- `auth` 鉴权模块：登录、游客令牌、令牌校验、登出黑名单（可选）
- `group` 分组模块：目录树 CRUD、层级与排序
- `document` 文档模块：文档 CRUD、发布状态切换、内容与元信息
- `sort` 排序模块：分组与文档的批量排序/迁移
- `search` 搜索模块：标题/内容/标签模糊查询（初期走 DB LIKE）
- `common` 公共模块：统一响应、异常处理、审计、拦截器

---

### 3. 安全与鉴权

- **认证方式**: 用户登录返回 `accessToken` (JWT)；游客入口返回 `visitorToken` (JWT, scope=read-only)。
- **授权规则**:
  - 已登录用户：可读写自己资源；禁止访问他人资源
  - 游客：仅可 GET；仅能访问 `status=published` 且 `ownerId` 为目标空间用户的资源
  - 接口级别 + 数据级别双重校验（方法权限 + 资源归属）
- **令牌字段**:
  - `sub`: userId 或 `visitor:<targetUserId>`
  - `role`: `USER` | `VISITOR`
  - `exp`: 过期时间（短期，如 2h）
  - `iat`, `iss`
- **黑名单与续签**（可选）:
  - Redis 维护登出黑名单（`jti`）
  - 刷新令牌接口（可选，以减轻频繁登录）

---

### 4. 数据模型（DDL 草案）

以 PostgreSQL 为例（MySQL 同理）：

```sql
CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  username VARCHAR(64) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  nickname VARCHAR(64),
  avatar TEXT,
  status SMALLINT DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE blog_groups (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  name VARCHAR(128) NOT NULL,
  parent_id BIGINT NULL REFERENCES blog_groups(id) ON DELETE SET NULL,
  sort_index INT NOT NULL DEFAULT 0,
  deleted BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TYPE doc_type AS ENUM ('md', 'pdf');
CREATE TYPE doc_status AS ENUM ('draft', 'published');

CREATE TABLE documents (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  group_id BIGINT NULL REFERENCES blog_groups(id) ON DELETE SET NULL,
  title VARCHAR(255) NOT NULL,
  type doc_type NOT NULL,
  status doc_status NOT NULL DEFAULT 'draft',
  content_md TEXT NULL,
  sort_index INT NOT NULL DEFAULT 0,
  deleted BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_groups_user ON blog_groups(user_id);
CREATE INDEX idx_docs_user_status ON documents(user_id, status);
CREATE INDEX idx_docs_group_sort ON documents(user_id, group_id, sort_index);
CREATE INDEX idx_docs_title_trgm ON documents USING gin (title gin_trgm_ops);
```

备注：如使用 MySQL，可用 `ENUM` 或 `VARCHAR` + 约束代替；全文检索可引入 ES 后替换。

---

### 5. 领域模型（JPA 实体概要）

- `User`: id, username, passwordHash, nickname, avatar, status, createdAt
- `Group`: id, userId, name, parentId, sortIndex, deleted, createdAt, updatedAt
- `Document`: id, userId, groupId, title, type(MD|PDF), status(DRAFT|PUBLISHED), contentMd, sortIndex, deleted, createdAt, updatedAt

---

### 6. API 设计

统一前缀：`/api`

#### 6.1 认证
- `POST /auth/login`
  - req: `{ username, password }`
  - res: `{ accessToken, user: { id, username, nickname, avatar } }`
- `POST /auth/visitor`
  - req: `{ targetUser? }`
  - res: `{ visitorToken, targetUser: { id, username, nickname } }`
- `POST /auth/refresh`（可选）
  - req: `{ refreshToken }`
  - res: `{ accessToken }`

#### 6.2 分组
- `GET /groups` → 当前空间的树
  - query: `rootOnly?`（可选）
  - res: `Group[]`（含 children）
- `POST /groups`
  - req: `{ name, parentId }`
  - res: `Group`
- `PATCH /groups/{id}`
  - req: `{ name?, parentId?, sortIndex? }`
  - res: `Group`
- `DELETE /groups/{id}`
  - 软删除，若含子节点可拒绝或级联（采用受控选项）

#### 6.3 文档
- `GET /documents`
  - query: `{ q?, status?, groupId?, page?, size? }`
  - res: `{ items: DocumentItem[], total }`
- `GET /documents/{id}` → 文档详情
- `POST /documents`
  - req: `{ title, groupId?, type: 'md'|'pdf', contentMd? }`
  - res: `DocumentDetail`
- `PATCH /documents/{id}`
  - req: `{ title?, groupId?, contentMd?, status?, sortIndex? }`
  - res: `DocumentDetail`
- `DELETE /documents/{id}` → 软删除

#### 6.4 排序/移动
- `POST /sort/groups`
  - req: `{ items: Array<{ id, parentId, sortIndex }> }`
- `POST /sort/documents`
  - req: `{ items: Array<{ id, groupId, sortIndex }> }`

---

### 7. 控制器到服务的职责划分

- Controller：参数校验、鉴权上下文读取（userId/role）、调用服务层、返回统一响应
- Service：事务边界、资源归属校验、领域逻辑（排序、发布、导入）、审计
- Repository：JPA 查询与批量更新

---

### 8. 资源归属与权限校验

- 读取鉴权上下文：
  - `AuthUser { role, currentUserId, targetUserId? }`
- 规则：
  - USER：所有读写操作均要求 `resource.userId == currentUserId`
  - VISITOR：仅允许 Controller 上的只读接口；查询自动添加 `status = 'published'` 且 `user_id = targetUserId`
- 统一切面/拦截器：方法上可加注解 `@OwnerRequired` 进行二次检查

---

### 9. 统一返回与异常

- 返回结构：`{ code: 0, message: 'ok', data: ... }`；错误：`{ code, message }`
- 全局异常处理：
  - 业务异常 `BizException(code, message)`
  - 参数校验异常 400
  - 权限异常 401/403
  - 未找到 404
  - 服务器错误 500（隐藏内部细节）

---

### 10. 排序与拖拽一致性

- 分组与文档的排序统一用 `sort_index` 升序；
- 拖拽后前端上传批量列表，后端开启事务：
  1) 校验所有项归属同一 `user_id`
  2) 更新 `parentId/groupId` 与 `sortIndex`
  3) 返回新的序列（用于前端乐观确认）

---

### 11. 搜索策略

- 初期：`title LIKE :q` +（`content_md LIKE :q` 可选）+ `status` 过滤；
- 数据量增长：引入 ES，索引 `title`, `content_md`, `tags`；保持 DB 为权威源。

---

### 12. 配置与环境

- `application.yml`
  - 数据库、Redis、对象存储、JWT 密钥、CORS 白名单
- Profile：`dev`, `staging`, `prod`
- 迁移：`Flyway`/`Liquibase` 管理 DDL

---

### 13. 日志与监控

- 访问日志：请求路径、方法、耗时、用户 id、状态码
- 业务日志：关键操作（发布、删除、导入）审计
- 指标：接口 QPS、错误率；Prometheus + Grafana（可选）

---

### 14. 性能与扩展性

- 数据库索引优化、N+1 查询规避、批量写入
- CDN 分发静态/PDF 资源（公开内容）
- 缓存：热门文档详情（短期缓存），鉴权黑名单
- 限流：登录接口

---

### 15. 安全清单

- 密码哈希：BCrypt；禁止明文
- JWT 密钥妥善保管，设置过期；签发者与受众校验
- CSRF：主要为 API + JWT Bearer，风险较小；同源策略 + CORS 严格控制
- XSS：Markdown 渲染在前端做清洗，后端再次做最小信任

---

### 16. 示例请求/响应

登录：
```http
POST /api/auth/login
Content-Type: application/json

{ "username": "alice", "password": "***" }
```

响应：
```json
{ "accessToken": "<jwt>", "user": { "id": 1, "username": "alice", "nickname": "Alice" } }
```

创建 Markdown 文档：
```http
POST /api/documents
Content-Type: application/json
Authorization: Bearer <token>

{ "title": "我的第一篇文章", "groupId": 12, "type": "md", "contentMd": "# 标题\n正文..." }
```

---

### 17. 开发计划与任务拆分

- S1 鉴权模块：登录、游客令牌、拦截器、上下文注入
- S2 分组模块：树查询、CRUD、迁移/排序
- S3 文档模块：CRUD、发布、权限校验
- S4 排序批量接口与事务一致性
- S5 搜索与分页
- S6 非功能：日志、监控、限流、安全加固

---

### 18. 部署与运维

- Dockerfile + docker-compose（db、minio、app）
- Nginx 反代，开启 Gzip/HTTP2/缓存策略
- 备份策略：DB 定期备份，对象存储版本化（可选）
- 迁移：Flyway 在启动时自动执行

---

### 19. 变更记录

- v1.0：初版设计，覆盖鉴权、资源模型、API、排序与安全基线


