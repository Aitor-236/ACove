-- ============================================================
-- ACove：首页展示文章表（Flyway V4）
--
-- 前台首页「动态」列表不再写死最近三篇：后台「首页展示」页可以挑任意篇
-- 已发布文章并拖拽决定顺序，这里一行 = 首页列表里的一条。
--
-- 字段：
--   article_id  文章ID（主键，一篇最多出现一次），关联 article.id
--   sort_order  首页展示顺序，越小越靠前（保存时从 0 开始依次递增）
--   created_at  加入首页展示的时间
--
-- 一、一篇都没选（或选中的都不是已发布）时，/article/home 回退到
--     「最近三篇已发布文章」，不靠这张表兜底。
-- 二、文章被物理删除时这一行靠外键 ON DELETE CASCADE 自动清理；
--     但「取消发布」不动这里，所以重新发布后会自动回到原来的位置。
--
-- 本文件一旦应用就不要再修改，后续改动请新增 V5__xxx.sql。
-- ============================================================

CREATE TABLE home_article (
    article_id BIGINT UNSIGNED NOT NULL COMMENT '文章ID，关联 article.id',
    sort_order INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '首页展示顺序，越小越靠前',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入首页展示的时间',
    PRIMARY KEY (article_id),
    KEY idx_home_article_sort (sort_order),
    CONSTRAINT fk_home_article_article FOREIGN KEY (article_id)
        REFERENCES article (id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '首页展示文章表';
