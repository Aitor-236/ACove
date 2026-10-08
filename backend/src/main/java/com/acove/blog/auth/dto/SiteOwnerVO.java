package com.acove.blog.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 前台首页要展示的站长资料。
 * 首页是匿名可访问的，所以这里只给用户名和头像，不带邮箱等账号信息。
 */
@Data
@AllArgsConstructor
public class SiteOwnerVO {

    private String username;

    /** 头像相对地址，形如 /uploads/avatar/xxx.png；空字符串表示还没设置头像 */
    private String avatar;
}
