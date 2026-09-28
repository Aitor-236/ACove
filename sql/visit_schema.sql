-- ============================================================
-- Aitor Blog：访问统计模块建表脚本
-- 用途：在现有 blog_db 中新增「每日访问人数（PV / UV）」相关表，可重复执行。
-- 前提：
--   1. MySQL 8+
--   2. blog_db 数据库已存在
-- 说明：
--   - 不做实时明细流水，只保留「每天一行汇总」+「当天来访过的访客」两张表，
--     访问量小的时候不需要 Redis，全部落 MySQL。
--   - visit_daily_stat 是汇总表：前台每上报一次页面浏览，pv + 1；
--     访客当天第一次出现时，uv + 1。
--   - visit_visitor 是去重依据：唯一键 (stat_date, visitor_key) 保证同一访客
--     一天只算一次 UV。visitor_key 是浏览器 Cookie 里的哈希值，不存原始 IP。
--   - 使用 CREATE TABLE IF NOT EXISTS，重复执行不会报错，也不会覆盖已有数据。
--   - 本文件用于已有数据库的增量升级；全新部署请直接用 sql/init_database.sql
--     （它包含建库、sys_user、文章模块和这里的全部内容）。
--   - 表结构变更时，本文件与 init_database.sql 需要同步修改。
-- ============================================================

SET NAMES utf8mb4;

USE blog_db;

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
