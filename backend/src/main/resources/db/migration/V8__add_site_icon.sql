-- 网站图标使用上传文件的相对地址；空串表示沿用前端内置 favicon。
ALTER TABLE site_setting
    ADD COLUMN site_icon VARCHAR(255) NOT NULL DEFAULT '' COMMENT '站点图标相对地址（/uploads/favicon/xxx），空表示使用默认图标'
    AFTER hero_image;
