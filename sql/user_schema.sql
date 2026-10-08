-- ============================================================
-- ACove：用户（登录）模块增量升级脚本
-- 用途：给已有的 blog_db 补齐用户模块后续新增的列，可重复执行。
-- 前提：
--   1. MySQL 8+
--   2. blog_db 与 sys_user 表已存在（全新部署请直接用 sql/init_database.sql）
-- 用法：
--   mysql -uroot -p < sql/user_schema.sql
-- 说明：
--   - MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，这里先查 information_schema 再执行 DDL，重复执行不报错。
--   - 本文件只做增量升级，字段定义需与 sql/init_database.sql 中的 sys_user 保持一致。
--   - 已完成升级的库执行本脚本不会修改任何数据。
-- ============================================================

-- 显式声明脚本与连接都用 utf8mb4，避免中文列注释 / 数据被按 latin1 双重编码。
-- 必须在执行 DDL 之前设置，写在文件末尾就没有意义了。
SET NAMES utf8mb4;

USE blog_db;

-- ------------------------------------------------------------
-- sys_user.avatar：头像地址，默认空字符串表示未设置头像
-- ------------------------------------------------------------
SET @avatar_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_user'
      AND COLUMN_NAME = 'avatar'
);

SET @avatar_ddl := IF(
    @avatar_exists = 0,
    'ALTER TABLE sys_user ADD COLUMN avatar VARCHAR(255) NOT NULL DEFAULT '''' COMMENT ''头像地址，默认为空'' AFTER password',
    'SELECT ''sys_user.avatar 已存在，跳过'' AS message'
);

PREPARE avatar_stmt FROM @avatar_ddl;
EXECUTE avatar_stmt;
DEALLOCATE PREPARE avatar_stmt;

-- ------------------------------------------------------------
-- sys_user.role：角色，决定谁是"站长"（前台首页只展示优先级最高的那个账号）
-- 优先级 owner > admin > user，同优先级时前台取 id 最小的（最早注册的）
-- ------------------------------------------------------------
SET @role_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_user'
      AND COLUMN_NAME = 'role'
);

SET @role_ddl := IF(
    @role_exists = 0,
    'ALTER TABLE sys_user ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT ''user'' COMMENT ''角色：owner-站长（前台首页展示的人），admin-管理员，user-普通用户'' AFTER avatar',
    'SELECT ''sys_user.role 已存在，跳过'' AS message'
);

PREPARE role_stmt FROM @role_ddl;
EXECUTE role_stmt;
DEALLOCATE PREPARE role_stmt;

-- 老库补列后还没有站长：把最早注册的那个账号提升为站长（已经有人是 owner 时不动）
SET @owner_exists := (SELECT COUNT(*) FROM sys_user WHERE role = 'owner');
SET @first_user_id := (SELECT MIN(id) FROM sys_user);

UPDATE sys_user
SET role = 'owner'
WHERE @owner_exists = 0
  AND @first_user_id IS NOT NULL
  AND id = @first_user_id;
