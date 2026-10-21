ALTER TABLE site_setting
    ADD COLUMN icp_record_number VARCHAR(100) NOT NULL DEFAULT '' COMMENT 'ICP备案号',
    ADD COLUMN icp_record_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否在首页展示ICP备案号';
