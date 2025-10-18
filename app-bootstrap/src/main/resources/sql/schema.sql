-- ============================================
-- 多功能后端系统 - 统一数据库初始化脚本
-- ============================================
-- 说明：
-- 1. 使用单数据库架构，通过表名前缀区分不同业务模块
-- 2. 文件模块表：file_*
-- 3. 博客模块表：blog_*
-- 4. 共享表：users
-- ============================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS multifunctional_backend 
DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE multifunctional_backend;

-- ============================================
-- 文件管理模块
-- ============================================

-- 文件元数据表
CREATE TABLE IF NOT EXISTS file_metadata (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '文件ID',
    file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    stored_name VARCHAR(255) NOT NULL COMMENT '存储文件名(UUID)',
    file_size BIGINT NOT NULL COMMENT '文件大小(字节)',
    file_type VARCHAR(100) COMMENT '文件MIME类型',
    file_extension VARCHAR(20) COMMENT '文件扩展名',
    file_path VARCHAR(500) COMMENT '文件存储路径',
    file_md5 VARCHAR(64) COMMENT '文件MD5值(用于秒传)',
    download_count INT DEFAULT 0 COMMENT '下载次数',
    upload_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    status TINYINT DEFAULT 1 COMMENT '状态(1:正常 0:已删除)',
    user_id BIGINT COMMENT '上传用户ID',
    INDEX idx_file_name (file_name),
    INDEX idx_upload_time (upload_time),
    INDEX idx_file_md5 (file_md5),
    INDEX idx_status (status),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件元数据表';

-- ============================================
-- 博客管理模块
-- ============================================

-- 用户表（博客管理员）
CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
    email VARCHAR(100) COMMENT '邮箱',
    avatar_url VARCHAR(500) COMMENT '头像URL',
    storage_quota BIGINT DEFAULT 1073741824 COMMENT '存储配额(默认1GB)',
    used_storage BIGINT DEFAULT 0 COMMENT '已用存储',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    last_login_at TIMESTAMP NULL COMMENT '最后登录时间',
    status TINYINT DEFAULT 1 COMMENT '状态(1:正常 0:禁用)',
    INDEX idx_username (username),
    INDEX idx_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 文章分组表
CREATE TABLE IF NOT EXISTS blog_groups (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '分组ID',
    name VARCHAR(100) NOT NULL UNIQUE COMMENT '分组名称',
    slug VARCHAR(100) NOT NULL UNIQUE COMMENT 'URL slug',
    description TEXT COMMENT '分组描述',
    order_index INT DEFAULT 0 COMMENT '排序序号',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_order (order_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章分组表';

-- 文章表
CREATE TABLE IF NOT EXISTS blog_articles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '文章ID',
    title VARCHAR(200) NOT NULL COMMENT '文章标题',
    slug VARCHAR(200) NOT NULL UNIQUE COMMENT 'URL slug',
    summary TEXT COMMENT '摘要',
    content_type VARCHAR(20) NOT NULL COMMENT '内容类型: markdown/pdf',
    content_text LONGTEXT COMMENT 'Markdown原始内容',
    content_url VARCHAR(500) COMMENT 'PDF文件URL',
    cover_url VARCHAR(500) COMMENT '封面图URL',
    group_id BIGINT COMMENT '所属分组',
    tags VARCHAR(500) COMMENT '标签（JSON数组字符串）',
    status VARCHAR(20) NOT NULL DEFAULT 'draft' COMMENT '状态: draft/published',
    publish_time TIMESTAMP NULL COMMENT '发布时间',
    views BIGINT DEFAULT 0 COMMENT '浏览量',
    comment_count INT DEFAULT 0 COMMENT '评论数',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (group_id) REFERENCES blog_groups(id) ON DELETE SET NULL,
    INDEX idx_slug (slug),
    INDEX idx_status (status),
    INDEX idx_publish_time (publish_time),
    INDEX idx_group (group_id),
    INDEX idx_views (views)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章表';

-- 评论表
CREATE TABLE IF NOT EXISTS blog_comments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '评论ID',
    article_id BIGINT NOT NULL COMMENT '文章ID',
    nickname VARCHAR(50) NOT NULL DEFAULT '访客' COMMENT '昵称',
    content TEXT NOT NULL COMMENT '评论内容',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    FOREIGN KEY (article_id) REFERENCES blog_articles(id) ON DELETE CASCADE,
    INDEX idx_article (article_id),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论表';

-- ============================================
-- 初始数据
-- ============================================

-- 插入默认管理员账号
-- 用户名：admin
-- 密码：admin123 (BCrypt加密)
-- ⚠️ 生产环境部署后请立即修改默认密码！
INSERT INTO users (username, password_hash, email, avatar_url) VALUES 
('admin', '$2a$10$EhVi6UxHQSz.a4MEh/LH/uQ/Z0rZ2jSqxX8F/3H9Z5Kt0EYlXv4xW', 'admin@example.com', NULL)
ON DUPLICATE KEY UPDATE username=username;

-- 插入默认博客分组
INSERT INTO blog_groups (name, slug, description, order_index) VALUES 
('技术分享', 'tech', '技术相关的文章', 1),
('生活随笔', 'life', '生活感悟与随笔', 2),
('学习笔记', 'notes', '学习过程中的笔记', 3)
ON DUPLICATE KEY UPDATE name=name;

-- ============================================
-- 使用说明
-- ============================================
-- 1. 首次部署执行：mysql -u root -p < schema.sql
-- 2. 默认管理员账号：admin / admin123
-- 3. 生产环境务必修改默认密码
-- 4. 文件模块通过 user_id 关联用户表
-- 5. 博客文章可通过 content_url 关联文件表（存储 PDF）
-- ============================================

