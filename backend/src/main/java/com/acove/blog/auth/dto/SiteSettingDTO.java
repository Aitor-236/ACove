package com.acove.blog.auth.dto;

import lombok.Data;

/**
 * 后台「网站设置」的入参。
 * 三个字段都可选，只更新传了值的那个：
 *   - null  表示这一项不改；
 *   - siteName 传空串会被忽略（网站名不允许清空）；
 *   - heroText 传空串表示清空（前台回退成网站名）；
 *   - heroImage 传空串表示清除头图（回退成默认底色），传其它值必须是 /uploads/ 开头的相对地址。
 */
@Data
public class SiteSettingDTO {

    /** 网站名，最长 50 字符 */
    private String siteName;

    /** 首页中间的文字，最长 200 字符，可清空 */
    private String heroText;

    /** 首页头图相对地址，最长 255 字符，可传空串清除 */
    private String heroImage;
}
