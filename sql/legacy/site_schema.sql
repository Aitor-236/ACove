-- 【已存档 · 不再被任何流程执行】表结构已由 Flyway 接管，见 backend/src/main/resources/db/migration/。
-- ============================================================
-- ACove：站点设置模块增量升级脚本
-- 用途：给已有的 blog_db 补上「站点设置」表（网站名 / 首页头图 / 首页中间文字），可重复执行。
-- 前提：
--   1. MySQL 8+
--   2. blog_db 已存在（全新部署请直接用 sql/init_database.sql）
-- 用法：
--   mysql -uroot -p < sql/site_schema.sql
-- 说明：
--   - 这是一张单行配置表：主键固定为 1，整张表永远只有一行。
--   - 表结构变更时，本文件与 sql/init_database.sql 需要同步修改。
--   - 重复执行只会补上缺失的那行，不会覆盖你已经在后台保存过的设置。
-- ============================================================

USE blog_db;

-- 显式声明脚本与连接都用 utf8mb4，避免中文列注释 / 默认值被按 latin1 双重编码。
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS site_setting (
    id BIGINT NOT NULL COMMENT '主键，固定为 1（单行配置表）',
    site_name VARCHAR(50) NOT NULL DEFAULT 'ACove' COMMENT '网站名，浏览器标题与后台侧栏用',
    hero_image VARCHAR(255) NOT NULL DEFAULT '' COMMENT '首页头图相对地址（/uploads/hero/xxx），空表示用默认底色',
    hero_text VARCHAR(200) NOT NULL DEFAULT '' COMMENT '首页中间的文字，空表示回退成网站名',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '站点设置表（单行）';

-- 补上默认那一行；已经有配置时保持原样，不覆盖用户的修改。
INSERT INTO site_setting (id, site_name, hero_image, hero_text)
VALUES (1, 'ACove', '', '')
ON DUPLICATE KEY UPDATE id = id;
