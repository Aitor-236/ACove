package com.acove.blog.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 站点设置：前台首页和后台「网站设置」页共用。
 */
@Data
@AllArgsConstructor
public class SiteSettingVO {

    /** 网站名，默认 ACove */
    private String siteName;

    /** 首页头图相对地址，形如 /uploads/hero/xxx.png；空字符串表示没设置，前台用默认底色 */
    private String heroImage;

    /** 首页中间的文字；空字符串时前台回退成网站名 */
    private String heroText;
}
