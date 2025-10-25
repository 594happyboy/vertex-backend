# 文件管理模块 (module-file)

## 📋 功能概述

全新的文件管理系统，支持树形文件夹结构、文件秒传、回收站、权限控制等企业级功能。

## ✨ 核心特性

### 1. 文件夹管理
- ✅ **树形结构**：支持无限层级嵌套的文件夹
- ✅ **拖拽排序**：自定义文件夹和文件的排序
- ✅ **颜色标记**：为文件夹设置不同的颜色标识
- ✅ **统计信息**：实时显示文件夹中的文件数量、大小等
- ✅ **路径导航**：面包屑导航，快速定位文件位置
- ✅ **批量操作**：批量移动、删除、排序

### 2. 文件管理
- ✅ **智能秒传**：基于MD5的文件去重和秒传功能
- ✅ **多格式支持**：支持md、jpg、png、pdf、doc等多种文件类型
- ✅ **文件预览**：支持图片、PDF、文本文件在线预览
- ✅ **描述信息**：为文件添加详细描述

### 3. 搜索和筛选
- ✅ **全文搜索**：支持文件名、描述的模糊搜索
- ✅ **多维筛选**：按文件类型、上传时间、大小等筛选
- ✅ **智能排序**：支持按名称、大小、时间、下载次数排序

### 4. 回收站
- ✅ **软删除**：删除的文件和文件夹暂存到回收站
- ✅ **30天保留期**：自动清理超过保留期的文件
- ✅ **一键恢复**：可随时从回收站恢复文件
- ✅ **永久删除**：彻底删除文件，释放存储空间

### 5. 权限和安全
- ✅ **用户隔离**：每个用户拥有独立的文件空间
- ✅ **下载统计**：记录文件下载次数

### 6. 性能优化
- ✅ **Redis缓存**：文件夹树和文件列表缓存
- ✅ **批量操作**：支持批量移动、删除等操作
- ✅ **分页查询**：大数据量下的高效分页
- ✅ **MinIO存储**：高性能对象存储

## 🗂️ 数据库结构

### file_folders 表（文件夹）
```sql
- id: 文件夹ID
- user_id: 用户ID
- name: 文件夹名称
- parent_id: 父文件夹ID（NULL表示根目录）
- sort_index: 排序索引
- color: 颜色标记
- description: 描述
- deleted: 是否删除
- deleted_at: 删除时间
- created_at/updated_at: 时间戳
```

### file_metadata 表（文件）
```sql
- id: 文件ID
- user_id: 用户ID
- folder_id: 所属文件夹ID（NULL表示根目录）
- file_name: 原始文件名
- stored_name: 存储文件名(UUID)
- file_size: 文件大小
- file_type: MIME类型
- file_extension: 文件扩展名
- file_md5: MD5值（用于秒传）
- download_count: 下载次数
- description: 描述
- deleted: 是否删除
- deleted_at: 删除时间
- upload_time/update_time: 时间戳
```

## 🔌 API 接口

### 文件夹管理 (/api/folders)

| 接口 | 方法 | 说明 |
|------|------|------|
| /tree | GET | 获取文件夹树 |
| / | POST | 创建文件夹 |
| /{id} | GET | 获取文件夹详情 |
| /{id} | PUT | 更新文件夹 |
| /{id} | DELETE | 删除文件夹 |
| /{id}/path | GET | 获取文件夹路径 |
| /batch-sort | POST | 批量排序 |

### 文件管理 (/api/files)

| 接口 | 方法 | 说明 |
|------|------|------|
| /upload | POST | 上传文件 |
| / | GET | 获取文件列表 |
| /{id} | GET | 获取文件详情 |
| /{id} | PUT | 更新文件信息 |
| /{id} | DELETE | 删除文件 |
| /{id}/download | GET | 下载文件（公开） |
| /{id}/move | PUT | 移动文件 |
| /batch-move | POST | 批量移动 |
| /batch-delete | POST | 批量删除 |
| /{id}/permanent | DELETE | 永久删除 |
| /{id}/restore | POST | 恢复文件 |
| /recycle-bin | GET | 回收站列表 |
| /statistics | GET | 统计信息 |
| /cache/clear | POST | 清除缓存 |

## 📖 使用示例

### 1. 创建文件夹
```http
POST /api/folders?userId=1
Content-Type: application/json

{
  "name": "工作文档",
  "parentId": null,
  "color": "#FF5722",
  "description": "存放工作相关文档"
}
```

### 2. 上传文件到文件夹
```http
POST /api/files/upload?userId=1&folderId=10
Content-Type: multipart/form-data

file: [二进制文件]
description: "项目需求文档"
```

### 3. 获取文件夹树
```http
GET /api/folders/tree?userId=1&includeStats=true
```

响应：
```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "rootFolders": [
      {
        "id": 1,
        "name": "工作文档",
        "fileCount": 15,
        "subFolderCount": 3,
        "totalSize": 52428800,
        "totalSizeFormatted": "50.00 MB",
        "children": [...]
      }
    ],
    "totalFolders": 10,
    "totalFiles": 45,
    "totalSize": 157286400,
    "totalSizeFormatted": "150.00 MB"
  }
}
```

### 4. 搜索文件
```http
GET /api/files?userId=1&keyword=需求&folderId=10&page=1&size=20&sortBy=uploadTime&order=desc
```

### 5. 批量移动文件
```http
POST /api/files/batch-move?userId=1
Content-Type: application/json

{
  "fileIds": [1, 2, 3, 4],
  "targetFolderId": 20
}
```

## 🎯 技术特性

### 架构设计
- **分层架构**：Controller → Service → Mapper → Entity
- **DTO模式**：请求和响应使用独立的DTO对象
- **事务管理**：关键操作使用Spring事务保证数据一致性

### 缓存策略
- **文件夹树缓存**：5分钟过期，按用户缓存
- **文件列表缓存**：5分钟过期，支持多维度查询缓存
- **文件详情缓存**：10分钟过期
- **自动失效**：修改操作自动清除相关缓存

### 存储优化
- **秒传机制**：相同MD5的文件只存储一份
- **MinIO存储**：分布式对象存储，支持横向扩展
- **定时清理**：自动清理回收站中的过期文件

## 🚀 部署说明

### 1. 数据库初始化
```bash
mysql -u root -p < app-bootstrap/src/main/resources/sql/schema.sql
```

### 2. 配置MinIO
```properties
minio.endpoint=http://localhost:9000
minio.accessKey=admin
minio.secretKey=admin123
minio.bucketName=vertex-files
```

### 3. 配置Redis
```properties
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.database=0
```

### 4. 文件大小限制
```properties
spring.servlet.multipart.max-file-size=100MB
spring.servlet.multipart.max-request-size=100MB
file.max-size=104857600
file.allowed-types=md,jpg,jpeg,png,gif,pdf,doc,docx,xls,xlsx,ppt,pptx,txt,zip,rar
```

## 🔧 常见问题

### Q: 如何修改文件大小限制？
A: 修改 `application.properties` 中的 `file.max-size` 配置项。

### Q: 如何添加新的文件类型支持？
A: 修改 `file.allowed-types` 配置项，添加新的文件扩展名。

### Q: 回收站保留期如何配置？
A: 在定时任务中调用 `cleanupExpiredFiles(days)` 方法，传入保留天数。

### Q: 如何清空缓存？
A: 调用 `/api/files/cache/clear` 接口。

## 📝 更新日志

### v2.0.0 (2025-10-23)
- 🎉 全新重构：支持文件夹树形结构
- ✨ 新增秒传功能
- ✨ 新增文件描述
- ✨ 新增批量操作
- ✨ 新增统计信息
- 🚀 性能优化：Redis缓存
- 🐛 修复已知问题

### v1.0.0 (2025-10-09)
- 基础文件上传下载功能
- 平铺式文件管理
- 回收站功能

## 📄 许可证

MIT License

## 👥 贡献者

ZZY - 主要开发者

---

**注意**：本系统已完全重构，不兼容v1.0版本的数据结构。升级前请备份数据！

