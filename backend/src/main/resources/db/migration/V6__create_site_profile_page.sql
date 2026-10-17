-- ============================================================
-- ACove：站长个人简介页（Flyway V6）
--
-- 页面配置固定一行；HTML 文档与资源包元数据分开保存，
-- 资源文件只包含 CSS、图片和字体，不在 /uploads 下托管 HTML。
-- ============================================================

CREATE TABLE site_profile_html_bundle (
    id CHAR(36) NOT NULL COMMENT 'HTML资源包ID（UUID）',
    html_content LONGTEXT NOT NULL COMMENT '经过校验的静态HTML文档',
    asset_path VARCHAR(120) NOT NULL COMMENT '资源相对目录（相对于blog.upload.dir）',
    status VARCHAR(16) NOT NULL DEFAULT 'staged' COMMENT '资源包状态：staged / active',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '导入时间',
    PRIMARY KEY (id),
    KEY idx_site_profile_bundle_status (status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '站长简介HTML资源包';

CREATE TABLE site_profile_page (
    id TINYINT UNSIGNED NOT NULL COMMENT '主键，固定为1（单行配置表）',
    active_mode VARCHAR(16) NOT NULL DEFAULT 'markdown' COMMENT '当前展示模式：markdown / html',
    markdown_content LONGTEXT NOT NULL COMMENT 'Markdown简介内容，切换模式时保留',
    html_bundle_id CHAR(36) NULL COMMENT '已保存的HTML资源包ID，切换模式时保留',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    CONSTRAINT fk_site_profile_page_bundle FOREIGN KEY (html_bundle_id)
        REFERENCES site_profile_html_bundle (id)
        ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '站长个人简介页配置';

INSERT INTO site_profile_page (id, active_mode, markdown_content, html_bundle_id)
VALUES (1, 'markdown', '', NULL);
