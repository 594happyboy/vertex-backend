# Module-Blog - 博客管理模块

## 📝 模块说明

博客管理模块是按照 `backend-spec.md` 设计文档实现的知识库/博客后端系统，提供用户认证、文档分组、文档管理、权限控制等完整功能。

## ✨ 功能特性

### 已实现功能

- ✅ **认证模块**
  - 用户登录（JWT）
  - 游客令牌（只读访问）
  - 令牌刷新（可选）
  - BCrypt密码加密

- ✅ **分组模块**
  - 分组CRUD
  - 树形层级结构
  - 拖拽排序
  - 软删除

- ✅ **文档模块**
  - 文档CRUD
  - Markdown/PDF支持
  - 发布状态管理（草稿/已发布）
  - 分页查询
  - 标题搜索
  - 软删除

- ✅ **排序模块**
  - 批量更新分组排序
  - 批量更新文档排序
  - 跨分组移动

- ✅ **目录树模块**
  - 完整的目录树结构（分组+文档）
  - Redis缓存（30分钟）
  - 自动缓存失效
  - 角色权限控制

- ✅ **权限控制**
  - JWT令牌验证
  - 用户/游客角色区分
  - 资源归属校验
  - 接口级权限控制

## 📊 数据模型

### 用户表 (users)
```sql
- id: 用户ID
- username: 用户名（唯一）
- password_hash: 密码哈希
- nickname: 昵称
- avatar: 头像URL
- status: 状态(1:正常 0:禁用)
```

### 分组表 (blog_groups)
```sql
- id: 分组ID
- user_id: 用户ID
- name: 分组名称
- parent_id: 父分组ID（支持层级）
- sort_index: 排序索引
- deleted: 软删除标记
```

### 文档表 (documents)
```sql
- id: 文档ID
- user_id: 用户ID
- group_id: 分组ID
- title: 标题
- type: 类型(md/pdf)
- status: 状态(draft/published)
- content_md: Markdown内容
- sort_index: 排序索引
- deleted: 软删除标记
```

## 🔌 API接口

### 认证接口

| 接口 | 方法 | 说明 | 权限 |
|------|------|------|------|
| `/api/auth/login` | POST | 用户登录 | 公开 |
| `/api/auth/visitor` | POST | 获取游客令牌 | 公开 |
| `/api/auth/refresh` | POST | 刷新令牌 | 公开 |

### 分组接口

| 接口 | 方法 | 说明 | 权限 |
|------|------|------|------|
| `/api/groups` | GET | 获取分组树 | 需认证 |
| `/api/groups` | POST | 创建分组 | 用户 |
| `/api/groups/{id}` | PATCH | 更新分组 | 用户 |
| `/api/groups/{id}` | DELETE | 删除分组 | 用户 |

### 文档接口

| 接口 | 方法 | 说明 | 权限 |
|------|------|------|------|
| `/api/documents` | GET | 查询文档列表 | 需认证 |
| `/api/documents/{id}` | GET | 获取文档详情 | 需认证 |
| `/api/documents` | POST | 创建文档 | 用户 |
| `/api/documents/{id}` | PATCH | 更新文档 | 用户 |
| `/api/documents/{id}` | DELETE | 删除文档 | 用户 |

### 排序接口

| 接口 | 方法 | 说明 | 权限 |
|------|------|------|------|
| `/api/sort/groups` | POST | 批量更新分组排序 | 用户 |
| `/api/sort/documents` | POST | 批量更新文档排序 | 用户 |

### 目录树接口 ⭐

| 接口 | 方法 | 说明 | 权限 |
|------|------|------|------|
| `/api/directory-tree` | GET | 获取完整的目录树（分组+文档） | 需认证 |

**特性**：
- ✅ 整合分组和文档为一个完整的树形结构
- ✅ Redis缓存（30分钟），避免频繁查询数据库
- ✅ 自动缓存失效（数据变更时自动清除）
- ✅ 用户角色：查看所有文档（包括草稿）
- ✅ 游客角色：只查看已发布文档

## 🔐 权限说明

### 用户角色

- **USER**: 已登录用户
  - 可以对自己的资源进行CRUD操作
  - 可以查看自己的所有文档（包括草稿）
  - 不能访问其他用户的资源

- **VISITOR**: 游客
  - 只能读取已发布的文档
  - 不能进行任何写操作
  - 只能访问指定用户的公开内容

### 令牌格式

```json
{
  "sub": "userId" 或 "visitor:targetUserId",
  "role": "USER" 或 "VISITOR",
  "username": "用户名",
  "iss": "vertex-backend",
  "iat": 1234567890,
  "exp": 1234574890
}
```

### 请求示例

```bash
# 设置令牌
Authorization: Bearer <jwt_token>

# 用户登录
POST /api/auth/login
{
  "username": "admin",
  "password": "admin123"
}

# 获取游客令牌
POST /api/auth/visitor
{
  "targetUser": "admin"
}

# 创建文档
POST /api/documents
{
  "title": "我的第一篇文章",
  "groupId": 1,
  "type": "md",
  "contentMd": "# 标题\n\n正文内容..."
}

# 获取完整的目录树（带缓存）
GET /api/directory-tree

# 响应示例
{
  "code": 200,
  "message": "success",
  "data": {
    "tree": [
      {
        "id": 1,
        "nodeType": "group",
        "name": "前端开发",
        "sortIndex": 0,
        "children": [
          {
            "id": 2,
            "nodeType": "group",
            "name": "React",
            "parentId": 1,
            "sortIndex": 0,
            "children": [
              {
                "id": 10,
                "nodeType": "document",
                "name": "React 入门教程",
                "groupId": 2,
                "type": "md",
                "status": "published",
                "sortIndex": 0
              }
            ]
          }
        ]
      },
      {
        "id": 20,
        "nodeType": "document",
        "name": "未分组的文档",
        "type": "md",
        "status": "draft",
        "sortIndex": 0
      }
    ],
    "cached": false
  }
}
```

## 🚀 快速开始

### 1. 初始化数据库

```bash
mysql -u root -p < app-bootstrap/src/main/resources/sql/schema.sql
```

### 2. 配置文件

编辑 `app-bootstrap/src/main/resources/application.properties`：

```properties
# 数据库配置
spring.datasource.url=jdbc:mysql://localhost:3306/vertex_backend
spring.datasource.username=root
spring.datasource.password=your_password

# JWT配置
jwt.secret=your-secret-key
jwt.expiration=7200000

# Redis配置
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

### 3. 启动应用

```bash
# 使用Gradle启动
./gradlew bootRun

# 或者在IDEA中直接运行 VertexBackendApplication
```

### 4. 访问API文档

http://localhost:8080/doc.html

### 5. 默认账号

- 用户名：`admin`
- 密码：`admin123`

⚠️ **生产环境请立即修改默认密码！**

## 🏗️ 项目结构

```
module-blog/
├── src/main/kotlin/com/zzy/blog/
│   ├── config/          # 配置类
│   │   └── WebConfig.kt
│   ├── context/         # 上下文
│   │   └── AuthContext.kt
│   ├── controller/      # 控制器
│   │   ├── AuthController.kt
│   │   ├── GroupController.kt
│   │   ├── DocumentController.kt
│   │   ├── SortController.kt
│   │   └── DirectoryTreeController.kt
│   ├── dto/            # 数据传输对象
│   │   ├── AuthDto.kt
│   │   ├── GroupDto.kt
│   │   ├── DocumentDto.kt
│   │   ├── SortDto.kt
│   │   └── DirectoryTreeDto.kt
│   ├── entity/         # 实体类
│   │   ├── User.kt
│   │   ├── Group.kt
│   │   └── Document.kt
│   ├── exception/      # 异常类
│   │   └── AuthException.kt
│   ├── interceptor/    # 拦截器
│   │   └── AuthInterceptor.kt
│   ├── mapper/         # 数据访问层
│   │   ├── UserMapper.kt
│   │   ├── GroupMapper.kt
│   │   └── DocumentMapper.kt
│   ├── service/        # 业务逻辑层
│   │   ├── AuthService.kt
│   │   ├── GroupService.kt
│   │   ├── DocumentService.kt
│   │   ├── SortService.kt
│   │   └── DirectoryTreeService.kt
│   └── util/           # 工具类
│       └── JwtUtil.kt
└── build.gradle.kts    # Gradle配置
```

## 📖 开发说明

### 添加新功能

1. 在对应的 `entity` 包中添加实体类
2. 在 `mapper` 包中添加Mapper接口
3. 在 `service` 包中实现业务逻辑
4. 在 `controller` 包中添加API接口
5. 更新数据库表结构（如需要）

### 权限控制

所有需要认证的接口都会经过 `AuthInterceptor` 拦截器：
- 自动解析JWT令牌
- 设置 `AuthContextHolder` 上下文
- 请求结束后自动清理上下文

在Service层可以通过以下方式获取当前用户信息：

```kotlin
// 获取当前用户ID
val userId = AuthContextHolder.getCurrentUserId()

// 检查是否为游客
if (AuthContextHolder.isVisitor()) {
    throw ForbiddenException("游客无权操作")
}

// 获取完整的认证信息
val authUser = AuthContextHolder.getAuthUser()
```

### 异常处理

所有异常都会被 `GlobalExceptionHandler` 统一处理：
- `AuthException` → 401/403
- `ResourceNotFoundException` → 404
- `IllegalArgumentException` → 400
- `Exception` → 500

## 🔧 技术栈

- **Spring Boot 3.5.6**: 核心框架
- **Kotlin 1.9.25**: 开发语言
- **MyBatis-Plus**: ORM框架
- **JWT (jjwt)**: 令牌认证
- **BCrypt**: 密码加密
- **MySQL 8.0+**: 数据库
- **Redis**: 缓存（可选）
- **Swagger/Knife4j**: API文档

## 📝 TODO

- [x] 添加Redis缓存（目录树）
- [ ] 实现文档标签功能
- [ ] 添加文档版本历史
- [ ] 实现全文搜索（Elasticsearch）
- [ ] 添加文档评论功能
- [ ] 实现文档分享链接
- [ ] 添加单元测试

## 📄 许可证

MIT License

---

**开发时间**: 2025-10-18  
**开发者**: ZZY  
**参考文档**: backend-spec.md

