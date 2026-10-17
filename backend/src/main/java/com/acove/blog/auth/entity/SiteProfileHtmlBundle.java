package com.acove.blog.auth.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/** 导入的个人简介静态HTML文档和配套资源目录。 */
@Data
@TableName("site_profile_html_bundle")
public class SiteProfileHtmlBundle {

    @TableId(type = IdType.INPUT)
    private String id;

    private String htmlContent;

    private String assetPath;

    private String status;

    private LocalDateTime createdAt;
}
