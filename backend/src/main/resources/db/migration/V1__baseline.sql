-- ============================================================
-- ACove：数据库基线（Flyway V1）
--
-- 这是 2026-10 之前用 sql/init_database.sql 手工建的 schema 的等价值：全新部署时
-- 由它建出全部表；已有的老库（没有 flyway_schema_history）会以 version 0 为基线，
-- 再把本文件完整执行一遍——所以这里**所有语句都必须幂等**，
-- 建表用 CREATE TABLE IF NOT EXISTS，种子数据用 ON DUPLICATE KEY UPDATE id = id
-- （绝不能写成 name = VALUES(name)，那会把后台改过的分类名覆盖掉）。
--
-- 约定：
--   - Flyway 用 JDBC 连到已存在的 blog_db，所以这里不写 CREATE DATABASE / USE。
--   - 也不写 SET NAMES：字符集由 JDBC 的 characterEncoding=utf-8 加上
--     spring.flyway.encoding=UTF-8 保证（脚本本身必须是 UTF-8 编码）。
--   - 不预置任何登录账号，部署后仍用 sql/init_account.sh（或 ./deploy.sh account）创建。
--   - 本文件一旦被应用过就不要再修改，改表结构请新增 V2__xxx.sql。
-- ============================================================

-- ------------------------------------------------------------
-- 系统用户表（登录模块使用）
-- 密码列存 BCrypt 哈希（60 字符，$2b$ 前缀），绝不存明文。
-- 本表刻意不预置任何账号，部署时由 sql/init_account.sh 创建。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    email VARCHAR(100) NOT NULL COMMENT '邮箱',
    password VARCHAR(100) NOT NULL COMMENT '密码（BCrypt 哈希）',
    avatar VARCHAR(255) NOT NULL DEFAULT '' COMMENT '头像地址，默认为空',
    role VARCHAR(20) NOT NULL DEFAULT 'user' COMMENT '角色：owner-站长（前台首页展示的人），admin-管理员，user-普通用户',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '系统用户表';

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

-- 预置与现有文章页面一致的分类数据（基于 slug 幂等）。
-- 注意 ON DUPLICATE KEY UPDATE 只写 id = id：老库重新执行本文件时保持分类名 / 排序不动，
-- 后台在「分类管理」里改过的名字不能被种子数据覆盖。
INSERT INTO article_category (name, slug, sort_order)
VALUES
    ('前端', 'frontend', 10),
    ('后端', 'backend', 20),
    ('绘画', 'painting', 30),
    ('生活', 'life', 40)
ON DUPLICATE KEY UPDATE id = id;

-- ------------------------------------------------------------
-- 访问统计模块（每天一行汇总 + 当天访客去重，UV 去重不依赖 Redis）
-- ------------------------------------------------------------

-- 每日访问汇总表（后台访问统计页的数据来源）
CREATE TABLE IF NOT EXISTS visit_daily_stat (
    stat_date DATE NOT NULL COMMENT '统计日期',
    pv BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当日浏览量 PV，每上报一次加一',
    uv BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当日独立访客数 UV，按 visitor_key 去重',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (stat_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '每日访问汇总表';

-- 每日访客去重表（UV 的判定依据，只保留「谁在哪天来过」）
CREATE TABLE IF NOT EXISTS visit_visitor (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    stat_date DATE NOT NULL COMMENT '统计日期',
    visitor_key CHAR(32) NOT NULL COMMENT '访客标识（Cookie 里 IP+UA 的哈希，不存原始 IP）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '当天首次来访时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_visit_visitor_date_key (stat_date, visitor_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '每日访客去重表';

-- ------------------------------------------------------------
-- 站点设置模块（单行配置：网站名 / 首页头图 / 首页中间文字）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS site_setting (
    id BIGINT NOT NULL COMMENT '主键，固定为 1（单行配置表）',
    site_name VARCHAR(50) NOT NULL DEFAULT 'ACove' COMMENT '网站名，浏览器标题与后台侧栏用',
    hero_image VARCHAR(255) NOT NULL DEFAULT '' COMMENT '首页头图相对地址（/uploads/hero/xxx），空表示用默认底色',
    hero_text VARCHAR(200) NOT NULL DEFAULT '' COMMENT '首页中间的文字，空表示回退成网站名',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '站点设置表（单行）';

-- 默认那一行：只在缺失时插入，已有配置保持原样（同样不能覆盖用户在后台保存过的值）。
INSERT INTO site_setting (id, site_name, hero_image, hero_text)
VALUES (1, 'ACove', '', '')
ON DUPLICATE KEY UPDATE id = id;
