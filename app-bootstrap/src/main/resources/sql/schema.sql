-- ============================================
-- Vertex Backend - 统一数据库初始化脚本
-- ============================================
-- 说明：
-- 1. 使用单数据库架构，通过表名前缀区分不同业务模块
-- 2. 文件模块表：file_*
-- 3. 博客模块表：blog_*
-- 4. 共享表：users
-- ============================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS vertex_backend 
DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE vertex_backend;

-- ============================================
-- 文件管理模块（支持文件夹树形结构）
-- ============================================

-- 文件夹表（树形结构）
CREATE TABLE IF NOT EXISTS file_folders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '文件夹ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    name VARCHAR(128) NOT NULL COMMENT '文件夹名称',
    parent_id BIGINT NULL COMMENT '父文件夹ID（NULL表示根目录）',
    sort_index INT NOT NULL DEFAULT 0 COMMENT '排序索引',
    color VARCHAR(20) NULL COMMENT '文件夹颜色标记（前端展示）',
    description TEXT NULL COMMENT '文件夹描述',
    deleted BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否删除（软删除）',
    deleted_at DATETIME NULL COMMENT '删除时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (parent_id) REFERENCES file_folders(id) ON DELETE SET NULL,
    INDEX idx_user_id (user_id),
    INDEX idx_parent_id (parent_id),
    INDEX idx_sort_index (sort_index),
    INDEX idx_deleted (deleted),
    INDEX idx_user_parent (user_id, parent_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件夹表';

-- 文件元数据表（重构版，支持文件夹）
CREATE TABLE IF NOT EXISTS file_metadata (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '文件ID',
    user_id BIGINT NOT NULL COMMENT '上传用户ID',
    folder_id BIGINT NULL COMMENT '所属文件夹ID（NULL表示根目录）',
    file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    stored_name VARCHAR(255) NOT NULL COMMENT '存储文件名(UUID)',
    file_size BIGINT NOT NULL COMMENT '文件大小(字节)',
    file_type VARCHAR(100) COMMENT '文件MIME类型',
    file_extension VARCHAR(20) COMMENT '文件扩展名',
    file_path VARCHAR(500) COMMENT '文件存储路径',
    file_md5 VARCHAR(64) COMMENT '文件MD5值(用于秒传)',
    download_count INT DEFAULT 0 COMMENT '下载次数',
    description TEXT NULL COMMENT '文件描述',
    deleted BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否删除（软删除）',
    deleted_at DATETIME NULL COMMENT '删除时间',
    upload_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (folder_id) REFERENCES file_folders(id) ON DELETE SET NULL,
    INDEX idx_user_id (user_id),
    INDEX idx_folder_id (folder_id),
    INDEX idx_file_name (file_name),
    INDEX idx_upload_time (upload_time),
    INDEX idx_file_md5 (file_md5),
    INDEX idx_deleted (deleted),
    INDEX idx_deleted_at (deleted_at),
    INDEX idx_user_folder (user_id, folder_id, deleted),
    FULLTEXT INDEX idx_file_name_ft (file_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件元数据表';

-- ============================================
-- 博客管理模块（按照 backend-spec.md 设计）
-- ============================================

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    username VARCHAR(64) NOT NULL UNIQUE COMMENT '用户名',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
    nickname VARCHAR(64) COMMENT '昵称',
    avatar TEXT COMMENT '头像URL',
    status SMALLINT DEFAULT 1 COMMENT '状态(1:正常 0:禁用)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_username (username),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 分组表（支持层级结构）
CREATE TABLE IF NOT EXISTS blog_groups (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '分组ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    name VARCHAR(128) NOT NULL COMMENT '分组名称',
    parent_id BIGINT NULL COMMENT '父分组ID',
    sort_index INT NOT NULL DEFAULT 0 COMMENT '排序索引',
    deleted BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否删除',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (parent_id) REFERENCES blog_groups(id) ON DELETE SET NULL,
    INDEX idx_user_id (user_id),
    INDEX idx_parent_id (parent_id),
    INDEX idx_sort_index (sort_index),
    INDEX idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分组表';

-- 文档表
CREATE TABLE IF NOT EXISTS documents (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '文档ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    group_id BIGINT NULL COMMENT '分组ID',
    title VARCHAR(255) NOT NULL COMMENT '标题',
    type VARCHAR(10) NOT NULL COMMENT '类型: md/pdf/txt',
    file_id BIGINT NULL COMMENT '文件ID（关联file_metadata）',
    file_path VARCHAR(500) NULL COMMENT '文件访问路径',
    sort_index INT NOT NULL DEFAULT 0 COMMENT '排序索引',
    deleted BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否删除',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (group_id) REFERENCES blog_groups(id) ON DELETE SET NULL,
    FOREIGN KEY (file_id) REFERENCES file_metadata(id) ON DELETE SET NULL,
    INDEX idx_user_id (user_id),
    INDEX idx_group_sort (user_id, group_id, sort_index),
    INDEX idx_deleted (deleted),
    INDEX idx_file_id (file_id),
    FULLTEXT INDEX idx_title (title)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档表';

-- ============================================
-- 初始数据
-- ============================================

-- 插入默认管理员账号
-- 用户名：admin
-- 密码：admin123 (BCrypt加密后的值)
-- ⚠️ 生产环境部署后请立即修改默认密码！
INSERT INTO users (username, password_hash, nickname, status) VALUES 
('admin', '$2a$10$imeDUw8sOsq94Ho3BpLhMOTzCtJUmypW71gbtoynQ0FLvMZbtnvmm', '管理员', 1)
ON DUPLICATE KEY UPDATE username=username;

-- ============================================
-- 使用说明
-- ============================================
-- 1. 首次部署执行：mysql -u root -p < schema.sql
-- 2. 默认管理员账号：admin / admin123
-- 3. 生产环境务必修改默认密码
-- 4. 分组支持层级结构（通过 parent_id）
-- 5. 文档支持 Markdown 和 PDF 两种类型
-- 6. 所有删除操作为软删除（通过 deleted 字段）
-- ============================================

