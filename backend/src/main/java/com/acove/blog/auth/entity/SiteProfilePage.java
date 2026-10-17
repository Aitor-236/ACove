package com.acove.blog.auth.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/** 站长个人简介页的单行配置。 */
@Data
@TableName("site_profile_page")
public class SiteProfilePage {

    @TableId(type = IdType.INPUT)
    private Integer id;

    private String activeMode;

    private String markdownContent;

    private String htmlBundleId;

    private LocalDateTime updatedAt;
}
