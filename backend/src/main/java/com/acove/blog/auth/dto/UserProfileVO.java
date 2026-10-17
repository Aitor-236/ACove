package com.acove.blog.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 个人管理页展示用的当前用户资料。 */
@Data
@AllArgsConstructor
public class UserProfileVO {

    private Long id;

    private String username;

    private String email;

    /** 当前账号角色，用于前端按 owner 身份显示个人简介编辑入口。 */
    private String role;

    /** 头像相对地址，形如 /uploads/avatar/xxx.png；空字符串表示还没设置头像 */
    private String avatar;
}
