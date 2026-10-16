-- ============================================================
-- ACove：图库表（Flyway V5）
--
-- gallery_album 一行代表一个图库；gallery_image 保存图库中的图片。
-- 封面由应用按 gallery_image.sort_order、id 升序取第一张图片，
-- 不单独冗余保存封面地址。空图库允许存在。
--
-- 删除图库时，外键级联删除图片记录；图片文件由应用服务在删除图库前
-- 收集地址并在删除记录后清理磁盘文件。
--
-- 本文件一旦应用就不要再修改，后续改表请新增 V6__xxx.sql。
-- ============================================================

CREATE TABLE gallery_album (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '图库ID',
    name VARCHAR(200) NOT NULL COMMENT '图库名称',
    description TEXT NULL COMMENT '图库简介',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_gallery_album_created_at (created_at, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '图库表';

CREATE TABLE gallery_image (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '图片ID',
    gallery_id BIGINT UNSIGNED NOT NULL COMMENT '所属图库ID',
    image_url VARCHAR(255) NOT NULL COMMENT '图片相对地址',
    original_name VARCHAR(255) NOT NULL DEFAULT '' COMMENT '上传时的原始文件名',
    sort_order INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '图库内排序，越小越靠前',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '添加时间',
    PRIMARY KEY (id),
    KEY idx_gallery_image_gallery_sort (gallery_id, sort_order, id),
    CONSTRAINT fk_gallery_image_album FOREIGN KEY (gallery_id)
        REFERENCES gallery_album (id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '图库图片表';
