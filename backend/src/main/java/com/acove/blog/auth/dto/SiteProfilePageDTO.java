package com.acove.blog.auth.dto;

import lombok.Data;

/** 站长个人简介页保存请求。 */
@Data
public class SiteProfilePageDTO {

    /** 当前公开展示模式：markdown 或 html。 */
    private String mode;

    /** Markdown模式的源码；保存HTML模式时仍会保留并更新此字段。 */
    private String markdownContent;

    /** HTML模式选择的已上传资源包ID。 */
    private String htmlBundleId;
}
