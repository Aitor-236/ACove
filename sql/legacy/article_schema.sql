-- 【已存档 · 不再被任何流程执行】表结构已由 Flyway 接管，见 backend/src/main/resources/db/migration/。
-- ============================================================
-- ACove：文章模块建表脚本
-- 用途：在现有 blog_db 中新增文章模块相关表，可重复执行。
-- 前提：
--   1. MySQL 8+
--   2. blog_db 数据库已存在
--   3. sys_user 表已存在（登录模块创建）
-- 说明：
--   - 正文只保存原始 Markdown，由前端负责渲染。
--   - 一篇文章属于一个作者、一个主分类，可关联多个标签。
--   - 使用 CREATE TABLE IF NOT EXISTS，重复执行不会报错。
--   - 本文件用于已有数据库的增量升级；全新部署请直接用 sql/init_database.sql
--     （它包含建库、sys_user 建表和这里的全部内容）。
--   - 表结构变更时，本文件与 init_database.sql 需要同步修改。
-- ============================================================

-- 显式声明脚本与连接都用 utf8mb4，避免中文种子数据 / 列注释被按 latin1 双重编码。
-- 必须在建表和插入数据之前执行，写在文件末尾就没有意义了。
SET NAMES utf8mb4;

USE blog_db;

-- 文章分类表
CREATE TABLE IF NOT EXISTS article_category (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    name VARCHAR(50) NOT NULL COMMENT '分类名称',
    slug VARCHAR(50) NOT NULL COMMENT '分类英文标识，用于稳定筛选',
    sort_order INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_article_category_slug (slug)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '文章分类表';

-- 文章标签表
CREATE TABLE IF NOT EXISTS tag (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '标签ID',
    name VARCHAR(50) NOT NULL COMMENT '标签名称',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tag_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '文章标签表';

-- 文章主表
CREATE TABLE IF NOT EXISTS article (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '文章ID',
    author_id BIGINT NOT NULL COMMENT '作者ID，关联 sys_user.id（与 sys_user.id 同为有符号 BIGINT）',
    category_id BIGINT UNSIGNED NOT NULL COMMENT '主分类ID，关联 article_category.id',
    title VARCHAR(200) NOT NULL COMMENT '文章标题',
    summary VARCHAR(500) NOT NULL COMMENT '文章摘要，用于列表卡片展示',
    content_markdown LONGTEXT NOT NULL COMMENT 'Markdown 正文原文',
    reading_minutes INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '预计阅读时长（分钟）',
    status VARCHAR(20) NOT NULL DEFAULT 'draft' COMMENT '状态：draft-草稿，published-已发布',
    published_at DATETIME NULL COMMENT '发布时间，发布时写入',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_article_status_published (status, published_at),
    KEY idx_article_author_id (author_id),
    KEY idx_article_category_id (category_id),
    CONSTRAINT fk_article_author FOREIGN KEY (author_id)
        REFERENCES sys_user (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,
    CONSTRAINT fk_article_category FOREIGN KEY (category_id)
        REFERENCES article_category (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '文章主表';

-- 文章-标签关联表
CREATE TABLE IF NOT EXISTS article_tag (
    article_id BIGINT UNSIGNED NOT NULL COMMENT '文章ID',
    tag_id BIGINT UNSIGNED NOT NULL COMMENT '标签ID',
    PRIMARY KEY (article_id, tag_id),
    KEY idx_article_tag_tag_id (tag_id),
    CONSTRAINT fk_article_tag_article FOREIGN KEY (article_id)
        REFERENCES article (id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT fk_article_tag_tag FOREIGN KEY (tag_id)
        REFERENCES tag (id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '文章与标签关联表';

-- 预置与现有文章页面一致的分类数据（基于 slug 幂等）
INSERT INTO article_category (name, slug, sort_order)
VALUES
    ('前端', 'frontend', 10),
    ('后端', 'backend', 20),
    ('绘画', 'painting', 30),
    ('生活', 'life', 40)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    sort_order = VALUES(sort_order);
