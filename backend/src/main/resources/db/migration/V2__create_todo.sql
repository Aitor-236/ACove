-- ============================================================
-- ACove：Todo 清单表（Flyway V2）
--
-- 站长个人的一份待办清单，三个状态用中性英文码存库：
--   todo  - 酝酿中（新建的 todo 默认落在这里）
--   doing - 打磨中
--   done  - 已完成
-- 前台只读、后台可改，展示文案（酝酿中 / 打磨中 / 已完成）由前端映射，
-- 所以改显示名不需要动这张表。
--
-- 「全表最多一条置顶」由数据库强约束：
--   is_pinned 只有 0 / 1，pinned_flag 是生成列——is_pinned = 1 时取 1，
--   否则取 NULL。MySQL 的唯一索引允许多个 NULL，因此 uk_todo_pinned 只可能
--   命中一条 is_pinned = 1 的行。
--   业务含义：置顶一条新的 todo 时，service 必须在同一事务里先取消旧的置顶、
--   再置顶新的，否则会撞唯一键。
--
-- 排序口径（后续接口实现）：ORDER BY is_pinned DESC, created_at DESC，
-- 即置顶那条永远最先，其余按创建时间新 → 旧，不引入 sort_order。
--
-- 本文件一旦应用就不要再修改，后续改动请新增 V3__xxx.sql。
-- ============================================================

CREATE TABLE todo (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    title VARCHAR(200) NOT NULL COMMENT '标题',
    description TEXT NULL COMMENT '描述 / 备注',
    status VARCHAR(20) NOT NULL DEFAULT 'todo' COMMENT '状态：todo-酝酿中，doing-打磨中，done-已完成',
    is_pinned TINYINT NOT NULL DEFAULT 0 COMMENT '是否置顶：1-置顶，0-普通',
    pinned_flag TINYINT GENERATED ALWAYS AS (IF(is_pinned = 1, 1, NULL)) STORED COMMENT '置顶唯一性判定列（生成列，非业务字段，仅供 uk_todo_pinned 使用）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_todo_pinned (pinned_flag),
    KEY idx_todo_status_created_at (status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Todo 清单表';
