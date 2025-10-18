# 📁 文件管理系统

> 基于 Spring Boot 3 + Kotlin + Vue 3 的全栈文件管理系统

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.6-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.25-blue.svg)](https://kotlinlang.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0+-orange.svg)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-7.0+-red.svg)](https://redis.io/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## ✨ 项目特点

- 🚀 **现代化技术栈**：Spring Boot 3 + Kotlin，符合国内主流技术要求
- 📦 **完整功能**：文件上传、下载、列表查询、删除、MD5秒传
- 🎯 **性能优化**：Redis缓存、数据库索引优化、流式传输
- 🔒 **安全可靠**：文件类型验证、大小限制、全局异常处理
- 📚 **文档齐全**：详细的技术方案、启动指南、API文档
- 🐳 **容器化部署**：Docker一键启动所有服务
- 💼 **求职利器**：适合作为全栈项目作品集

---

## 🚀 5分钟快速启动

### 第一次使用？

👉 **请先阅读：[新手快速启动指南.md](./新手快速启动指南.md)** 👈

只需5个步骤即可启动项目！

### 已安装环境？

#### Windows用户
```
双击 "启动.bat" 即可
```

#### Linux/Mac用户
```bash
# 1. 启动Docker服务
docker-compose up -d

# 2. 初始化数据库（统一数据库）
mysql -h localhost -u root -p < app-bootstrap/src/main/resources/sql/schema.sql

# 3. 启动应用
mvn spring-boot:run
```

访问：http://localhost:8080/doc.html 查看API文档

---

## 📚 文档导航

### 🎯 入门必读（按顺序阅读）

| 文档 | 说明 | 适合人群 |
|------|------|----------|
| 📄 [新手快速启动指南.md](./新手快速启动指南.md) | 5分钟快速上手 | ⭐ 新手必读 |
| 📄 [项目概览.md](./项目概览.md) | 项目介绍、技术栈、架构 | ⭐ 了解项目 |
| 📄 [启动指南.md](./启动指南.md) | 详细启动说明（含问题解决） | ⭐ 详细参考 |

### 📖 技术文档

| 文档 | 说明 | 适合人群 |
|------|------|----------|
| 📄 [文件管理系统-技术方案.md](./文件管理系统-技术方案.md) | 完整技术方案（726行） | 技术深入学习 |
| 📄 [README-Backend.md](./README-Backend.md) | 后端开发文档 | 后端开发者 |
| 📄 [后端开发完成总结.md](./后端开发完成总结.md) | 开发总结和亮点 | 了解项目成果 |
| 📄 [项目文件清单.md](./项目文件清单.md) | 所有文件清单 | 项目概览 |

### 🛠️ 辅助文件

| 文件 | 说明 |
|------|------|
| `启动.bat` | Windows一键启动脚本 |
| `docker-compose.yml` | Docker服务配置 |
| `环境配置示例.txt` | 环境变量配置 |

---

## 🎯 核心功能

### 已实现功能 ✅

- ✅ **文件上传**
  - 支持多文件上传
  - 文件大小限制（100MB）
  - 文件类型白名单验证
  - MD5秒传（相同文件自动复用）
  - UUID文件名生成

- ✅ **文件下载**
  - 流式下载
  - 中文文件名支持
  - 下载次数统计

- ✅ **文件管理**
  - 分页查询
  - 关键词搜索
  - 多字段排序
  - 逻辑删除
  - 批量删除

- ✅ **性能优化**
  - Redis缓存（文件列表、详情）
  - 数据库索引优化
  - 文件MD5去重
  - 流式传输大文件

- ✅ **系统功能**
  - 统一响应格式
  - 全局异常处理
  - 跨域配置
  - Swagger API文档
  - 健康检查接口

### 计划功能 📋

- [ ] Vue 3前端界面
- [ ] 用户认证（JWT）
- [ ] 权限控制（RBAC）
- [ ] 文件分享功能
- [ ] 实时通知（WebSocket）
- [ ] 云存储集成（OSS）
- [ ] 文件预览功能
- [ ] 数据统计分析

---

## 🛠️ 技术栈

### 后端技术

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.5.6 | 核心框架 |
| Kotlin | 1.9.25 | 开发语言 |
| MyBatis-Plus | 3.5.5 | ORM框架 |
| MySQL | 8.0+ | 关系数据库 |
| Redis | 7.0+ | 缓存 |
| MinIO | 8.5.7 | 对象存储 |
| Knife4j | 4.4.0 | API文档 |
| Hutool | 5.8.24 | 工具库 |

### 前端技术（规划）

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue | 3.4+ | 前端框架 |
| TypeScript | 5.0+ | 类型支持 |
| Vite | 5.0+ | 构建工具 |
| Element Plus | 2.5+ | UI组件库 |
| Axios | 1.6+ | HTTP客户端 |
| Pinia | 2.1+ | 状态管理 |

---

## 📊 项目结构

```
multifunctional-backend/
├── app-bootstrap/                    # 主启动模块
│   └── src/main/resources/
│       ├── application.properties    # 应用配置（统一数据库）
│       └── sql/schema.sql            # 数据库初始化脚本
├── common/                           # 公共模块
│   └── src/main/kotlin/com/zzy/common/
│       ├── config/                   # 配置类（CORS、Redis、MinIO等）
│       ├── dto/                      # 数据传输对象
│       ├── exception/                # 异常处理
│       └── util/                     # 工具类
├── module-file/                      # 文件管理模块
│   └── src/main/kotlin/com/zzy/file/
│       ├── controller/               # 文件管理API
│       ├── service/                  # 文件业务逻辑
│       ├── entity/                   # 文件实体
│       └── mapper/                   # 数据访问层
├── module-blog/                      # 博客模块
│   └── src/main/kotlin/com/zzy/blog/
│       ├── controller/               # 博客API
│       ├── service/                  # 博客业务逻辑
│       ├── entity/                   # 博客实体
│       └── mapper/                   # 数据访问层
├── pom.xml                           # Maven配置
├── docker-compose.yml                # Docker配置
├── 启动.bat                          # Windows启动脚本
├── 初始化数据库.bat                  # 数据库初始化脚本
├── 数据库迁移指南.md                 # 数据库迁移文档
└── README.md                         # 本文档
```

---

## 📡 API接口

### 核心接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/files/upload` | POST | 文件上传 |
| `/api/files` | GET | 获取文件列表 |
| `/api/files/{id}` | GET | 获取文件详情 |
| `/api/files/{id}/download` | GET | 文件下载 |
| `/api/files/{id}` | DELETE | 删除文件 |
| `/api/files/batch` | DELETE | 批量删除 |
| `/api/files/health` | GET | 健康检查 |

### API文档

启动项目后访问：http://localhost:8080/doc.html

在线查看和测试所有API接口。

---

## 🎨 系统架构

```
┌─────────────────────────────────────────┐
│          前端层 (Vue 3)                  │
│    文件上传 | 文件列表 | 文件预览        │
└─────────────────────────────────────────┘
                    ↕ REST API
┌─────────────────────────────────────────┐
│       后端层 (Spring Boot + Kotlin)      │
│  Controller → Service → Mapper → Entity │
└─────────────────────────────────────────┘
                    ↕
┌──────────┐  ┌──────────┐  ┌──────────┐
│  MySQL   │  │  Redis   │  │  MinIO   │
│ (元数据) │  │  (缓存)  │  │  (文件)  │
└──────────┘  └──────────┘  └──────────┘
```

---

## 🔥 项目亮点

### 1. 技术栈现代化
- Spring Boot 3最新版 + Kotlin
- 符合国内90%公司技术要求
- 适合求职作品集

### 2. 架构设计规范
- 清晰的分层架构
- 统一异常处理
- 统一响应格式

### 3. 性能优化
- Redis多级缓存
- 数据库索引优化
- 文件MD5秒传
- 流式传输大文件

### 4. 代码质量
- Kotlin类型安全
- 详细注释文档
- 0个代码错误
- 遵循最佳实践

---

## 🐳 Docker部署

### 一键启动所有服务

```bash
# 启动MySQL、Redis、MinIO
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f
```

### 服务访问

- **MySQL**: localhost:3306 (root/root123)
  - 数据库名: `multifunctional_backend` (统一数据库架构)
- **Redis**: localhost:6379
- **MinIO控制台**: http://localhost:9001 (admin/admin123)
- **MinIO API**: http://localhost:9000

### 数据库快速初始化

Windows用户可以双击 `初始化数据库.bat` 一键完成数据库初始化。

---

## 🧪 测试

### 使用Swagger测试（推荐）

1. 启动项目
2. 访问：http://localhost:8080/doc.html
3. 选择接口 → 点击"调试" → 输入参数 → 发送

### 使用PowerShell/CMD测试

```powershell
# 上传文件
curl.exe -X POST "http://localhost:8080/api/files/upload" -F "file=@C:\test.pdf"

# 获取列表
Invoke-WebRequest -Uri "http://localhost:8080/api/files" | Select-Object -ExpandProperty Content

# 下载文件
Invoke-WebRequest -Uri "http://localhost:8080/api/files/1/download" -OutFile "downloaded_file"
```

---

## 🔧 开发环境

### 必需环境
- Java 17+
- Maven 3.6+
- Docker Desktop
- Cursor/IDEA

### 推荐环境
- Windows 10/11 或 Linux
- 8GB+ 内存
- SSD硬盘

---

## 📝 开发进度

- [x] 项目初始化
- [x] 技术方案设计
- [x] 后端架构搭建
- [x] 文件上传功能
- [x] 文件下载功能
- [x] 文件列表查询
- [x] 文件删除功能
- [x] Redis缓存集成
- [x] MinIO存储集成
- [x] API文档生成
- [x] 异常处理机制
- [x] Docker部署配置
- [ ] 前端Vue 3开发
- [ ] 用户认证系统
- [ ] 权限控制
- [ ] 文件分享功能
- [ ] 单元测试

---

## 🤝 贡献指南

欢迎提交Issue和Pull Request！

### 开发规范
- 遵循Kotlin编码规范
- 提交前进行代码格式化
- 编写必要的注释
- 提交信息使用中文

---

## 📄 许可证

本项目采用 [MIT](LICENSE) 许可证

---

## 👨‍💻 作者

**ZZY**
- 项目时间：2025年10月
- 技术栈：Spring Boot 3 + Kotlin
- 适合：学习、求职、实际使用

---

## 🆘 获取帮助

### 遇到问题？

1. 查看 **[新手快速启动指南.md](./新手快速启动指南.md)** 的"常见问题"部分
2. 查看 **[启动指南.md](./启动指南.md)** 的详细说明
3. 检查Cursor终端日志
4. 运行 `docker-compose logs -f` 查看Docker日志

### 学习资源

- [Spring Boot官方文档](https://spring.io/projects/spring-boot)
- [Kotlin官方文档](https://kotlinlang.org/docs/home.html)
- [MyBatis-Plus文档](https://baomidou.com/)
- [Vue 3官方文档](https://cn.vuejs.org/)

---

## 🎯 下一步

### 立即开始
1. ✅ 阅读 [新手快速启动指南.md](./新手快速启动指南.md)
2. ✅ 启动项目并测试API
3. ✅ 理解项目结构和代码

### 功能扩展
1. 📱 开发Vue 3前端界面
2. 🔐 添加用户认证（JWT）
3. 📊 实现数据统计分析
4. ☁️ 集成云存储（阿里云OSS）

### 求职准备
1. 完善项目功能
2. 写技术博客
3. 录制演示视频
4. 上传到GitHub

---

## ⭐ Star History

如果这个项目对你有帮助，请给个Star支持一下！

---

**祝你学习顺利，早日成为优秀的全栈开发工程师！** 💪🚀

---

## 📦 数据库架构说明

**v2.0 更新 (2025-10-16)**

项目已从多数据库架构升级为单数据库架构：
- ✅ **简化配置**：从 2 个数据库合并为 1 个
- ✅ **降低复杂度**：无需多数据源配置
- ✅ **更易维护**：统一的数据库管理
- ✅ **表命名规范**：使用模块前缀区分业务（`file_*`、`blog_*`）

详细信息请查看：[数据库迁移指南.md](./数据库迁移指南.md)

---

*最后更新：2025-10-16*

