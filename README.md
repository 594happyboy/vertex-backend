# Vertex Backend

> 基于 Spring Boot 3 + Kotlin 的现代化后端系统，包含文件管理和博客/知识库两大核心模块

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.6-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.25-blue.svg)](https://kotlinlang.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0+-orange.svg)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-7.0+-red.svg)](https://redis.io/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## 📖 项目简介

Vertex Backend 是一个功能完整的后端系统，提供文件管理和知识库/博客管理两大核心功能模块：

- **文件管理模块**: 支持文件上传、下载、分页查询、MD5秒传、Redis缓存等
- **博客模块**: 提供文档分组、Markdown编辑、发布管理、JWT认证、权限控制等

适合作为学习项目、毕业设计或求职作品集。

---

## ⚡ 快速启动

### 前置要求

- Java 17+
- Docker Desktop（或独立的MySQL 8.0+、Redis 7.0+、MinIO）
- Maven 3.6+ 或 Gradle 7.0+

### 启动步骤

1. **启动基础服务**
```bash
# 启动 MySQL、Redis、MinIO
docker-compose up -d

# 检查服务状态
docker-compose ps
```

2. **初始化数据库**
```bash
# Windows (PowerShell)
Get-Content schema.sql | docker exec -i vertex-mysql mysql -uroot -proot123 vertex_backend

# Linux/Mac
mysql -h localhost -u root -proot123 vertex_backend < schema.sql
```

3. **启动应用**
```bash
# 使用 Gradle (推荐)
./gradlew bootRun

# 或使用 Maven
mvn spring-boot:run

# 或在 IDEA 中直接运行 VertexBackendApplication
```

4. **访问服务**
- API文档: http://localhost:8080/doc.html
- 应用端口: http://localhost:8080

### 默认账号

- **用户名**: `admin`
- **密码**: `admin123`

⚠️ **生产环境请务必修改默认密码！**

---

## 🎯 核心功能

### 文件管理模块 (`module-file`)

- ✅ 文件上传（支持100MB内的任意文件）
- ✅ 文件下载（流式传输）
- ✅ 分页查询和搜索
- ✅ MD5秒传（相同文件自动复用）
- ✅ Redis缓存（列表和详情）
- ✅ MinIO对象存储
- ✅ 批量删除和逻辑删除

### 博客/知识库模块 (`module-blog`)

- ✅ **认证系统**: JWT令牌、用户登录、游客访问
- ✅ **分组管理**: 树形层级结构、拖拽排序
- ✅ **文档管理**: Markdown编辑、草稿/发布状态
- ✅ **权限控制**: 用户/游客双角色、资源归属验证
- ✅ **搜索功能**: 标题搜索、分组筛选、状态过滤
- ✅ **批量操作**: 批量排序、批量移动

---

## 🏗️ 技术栈

### 后端框架
- **Spring Boot 3.5.6** - 核心框架
- **Kotlin 1.9.25** - 开发语言
- **MyBatis-Plus 3.5.5** - ORM框架
- **JWT (jjwt 0.11.5)** - 令牌认证
- **BCrypt** - 密码加密

### 数据存储
- **MySQL 8.0+** - 关系数据库
- **Redis 7.0+** - 缓存中间件
- **MinIO** - 对象存储

### 开发工具
- **Knife4j 4.4.0** - API文档
- **Hutool 5.8.24** - 工具库
- **Docker** - 容器化部署

---

## 📊 项目结构

```
vertex-backend/
├── schema.sql               # 数据库初始化脚本（部署配置）
├── app-bootstrap/           # 应用启动模块
│   ├── src/main/resources/
│   │   └── application.properties  # 应用配置
│   └── VertexBackendApplication.kt # 启动类
│
├── common/                  # 公共模块
│   └── src/main/kotlin/com/zzy/common/
│       ├── config/          # 配置类（CORS、Redis、MinIO等）
│       ├── dto/             # 统一响应格式
│       ├── exception/       # 全局异常处理
│       └── util/            # 工具类
│
├── module-file/             # 文件管理模块
│   └── src/main/kotlin/com/zzy/file/
│       ├── controller/      # 文件API
│       ├── service/         # 业务逻辑
│       ├── entity/          # 实体类
│       └── mapper/          # 数据访问
│
├── module-blog/             # 博客/知识库模块
│   └── src/main/kotlin/com/zzy/blog/
│       ├── controller/      # 认证、分组、文档、排序API
│       ├── service/         # 业务逻辑
│       ├── entity/          # 用户、分组、文档实体
│       ├── mapper/          # 数据访问
│       ├── interceptor/     # 认证拦截器
│       ├── context/         # 鉴权上下文
│       └── util/            # JWT工具
│
├── docker-compose.yml       # Docker服务配置
├── build.gradle.kts         # Gradle配置
└── README.md               # 本文档
```

---

## 📡 API接口概览

### 文件管理接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/files/upload` | POST | 文件上传 |
| `/api/files` | GET | 文件列表（分页、搜索） |
| `/api/files/{id}` | GET | 文件详情 |
| `/api/files/{id}/download` | GET | 文件下载 |
| `/api/files/{id}` | DELETE | 删除文件 |
| `/api/files/batch` | DELETE | 批量删除 |

### 博客管理接口

| 模块 | 接口数 | 主要功能 |
|------|--------|----------|
| 认证模块 | 3个 | 登录、游客令牌、刷新令牌 |
| 分组模块 | 4个 | 分组CRUD、树形结构 |
| 文档模块 | 5个 | 文档CRUD、发布管理 |
| 排序模块 | 2个 | 批量排序和移动 |

**总计**: 20+ API接口

完整API文档: http://localhost:8080/doc.html

---

## 🔧 配置说明

### 数据库配置

编辑 `app-bootstrap/src/main/resources/application.properties`:

```properties
# MySQL配置
spring.datasource.url=jdbc:mysql://localhost:3306/vertex_backend
spring.datasource.username=root
spring.datasource.password=root123

# Redis配置
spring.data.redis.host=localhost
spring.data.redis.port=6379

# MinIO配置
minio.endpoint=http://localhost:9000
minio.accessKey=admin
minio.secretKey=admin123

# JWT配置
jwt.secret=your-256-bit-secret-key-change-in-production
jwt.expiration=7200000
```

### Docker服务配置

`docker-compose.yml` 默认配置：

- **MySQL**: localhost:3306 (root/root123)
- **Redis**: localhost:6379
- **MinIO**: localhost:9000 (API) / localhost:9001 (Console)

---

## 🧪 测试使用

### 1. 测试文件上传

```bash
# 上传文件
curl -X POST "http://localhost:8080/api/files/upload" \
  -F "file=@test.pdf"

# 查看文件列表
curl "http://localhost:8080/api/files"
```

### 2. 测试博客功能

```bash
# 登录获取令牌
curl -X POST "http://localhost:8080/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# 创建分组
curl -X POST "http://localhost:8080/api/groups" \
  -H "Authorization: Bearer <your_token>" \
  -H "Content-Type: application/json" \
  -d '{"name":"技术文章"}'

# 创建文档
curl -X POST "http://localhost:8080/api/documents" \
  -H "Authorization: Bearer <your_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "title":"我的第一篇文章",
    "type":"md",
    "contentMd":"# Hello World"
  }'
```

---

## 🔐 权限说明

### 文件模块
- 所有接口均为公开访问（未实现认证）
- 可独立使用，不依赖博客模块

### 博客模块
- **USER角色**: 已登录用户，可管理自己的分组和文档
- **VISITOR角色**: 游客，只能查看已发布的公开内容
- 所有接口（除登录外）需要携带JWT令牌

### 令牌使用

```http
Authorization: Bearer <jwt_token>
```

---

## 🐳 Docker部署

### 服务管理

```bash
# 启动所有服务
docker-compose up -d

# 停止所有服务
docker-compose down

# 查看服务日志
docker-compose logs -f

# 重启某个服务
docker-compose restart vertex-mysql
```

### 数据持久化

Docker卷挂载：
- MySQL数据: `./docker-data/mysql`
- Redis数据: `./docker-data/redis`
- MinIO数据: `./docker-data/minio`

---

## ❓ 常见问题

### 1. 数据库连接失败

**问题**: `Communications link failure`

**解决**:
```bash
# 检查MySQL是否启动
docker-compose ps

# 重启MySQL
docker-compose restart vertex-mysql

# 检查端口占用
netstat -ano | findstr :3306
```

### 2. Redis连接失败

**问题**: `Cannot get Jedis connection`

**解决**:
```bash
# 检查Redis
docker-compose logs vertex-redis

# 测试Redis连接
docker exec -it vertex-redis redis-cli ping
```

### 3. 端口被占用

**问题**: `Port 8080 is already in use`

**解决**:
- 修改 `application.properties` 中的 `server.port`
- 或关闭占用8080端口的其他程序

### 4. Swagger无法访问

**解决**: 访问 http://localhost:8080/doc.html (不是 swagger-ui.html)

---

## 📝 模块文档

- [博客模块详细说明](./module-blog/README.md)
- [博客模块实现总结](./博客模块实现总结.md)
- [后端设计文档](./backend-spec.md)

---

## 🚀 下一步计划

- [ ] 文件模块添加用户认证
- [ ] 集成阿里云OSS替代MinIO
- [ ] 添加文档标签和全文搜索
- [ ] 实现文档评论功能
- [ ] 开发Vue 3前端界面
- [ ] 添加单元测试

---

## 📄 许可证

本项目采用 [MIT](LICENSE) 许可证

---

## 👨‍💻 作者

**ZZY**

- 开发时间: 2025年10月
- 技术栈: Spring Boot 3 + Kotlin + MyBatis-Plus
- 适用场景: 学习、求职、实际项目

---

## 🌟 Star History

如果这个项目对你有帮助，欢迎给个 Star ⭐

---

*最后更新: 2025-10-18*
