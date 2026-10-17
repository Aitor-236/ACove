package com.acove.blog.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 站长后台个人简介编辑页的已保存内容。 */
@Data
@AllArgsConstructor
public class AdminSiteProfilePageVO {

    private String mode;

    private String markdownContent;

    private String htmlBundleId;

    /** 后台需携带认证请求此接口，再将响应中的 HTML 放入 sandbox iframe 的 srcdoc。 */
    private String previewApiUrl;
}
