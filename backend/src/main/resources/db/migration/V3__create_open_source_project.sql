-- ============================================================
-- ACove：开源项目表（Flyway V3）
--
-- 前台「开源项目」页一张卡片 = 这里的一行，后台可维护。
-- 字段：
--   name          项目名字
--   url           项目地址（对外链接）
--   description   项目介绍
--   update_log    日志：最新更新内容
--   next_step     下一步（todo）
--   preview_image 预览图相对地址 —— 暂未启用：先把列建好、默认空串，
--                 等后期做好预览图上传再补 UI，前台现在也不给它留位置
--
-- 排序口径（接口实现）：ORDER BY created_at DESC, id DESC，
-- 即新建的排前面，同秒再用 id 兜底。
--
-- 本文件一旦应用就不要再修改，后续改动请新增 V4__xxx.sql。
-- ============================================================

CREATE TABLE open_source_project (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    name VARCHAR(200) NOT NULL COMMENT '项目名字',
    url VARCHAR(500) NOT NULL DEFAULT '' COMMENT '项目地址（对外链接）',
    description TEXT NULL COMMENT '项目介绍',
    update_log TEXT NULL COMMENT '日志：最新更新内容',
    next_step TEXT NULL COMMENT '下一步（todo）',
    preview_image VARCHAR(255) NOT NULL DEFAULT '' COMMENT '预览图相对地址（暂未启用，保持空串，前端不展示）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '开源项目表';
