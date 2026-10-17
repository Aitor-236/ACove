package com.acove.blog.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 前台个人简介页的当前展示内容。 */
@Data
@AllArgsConstructor
public class SiteProfilePageVO {

    private String mode;

    private String markdownContent;

    /** HTML文档专用安全展示接口；Markdown模式时为空。 */
    private String htmlUrl;
}
