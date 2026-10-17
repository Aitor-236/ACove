package com.acove.blog.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 已校验的候选HTML资源包；保存前只作为预览使用。 */
@Data
@AllArgsConstructor
public class SiteProfileHtmlPackageVO {

    private String bundleId;

    /** 站长后台用认证接口读取候选 HTML 并放入 sandbox iframe 的 srcdoc。 */
    private String previewApiUrl;
}
