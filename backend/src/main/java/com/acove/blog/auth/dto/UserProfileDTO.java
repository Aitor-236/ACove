package com.acove.blog.auth.dto;

import lombok.Data;

/**
 * 个人管理页修改用户名 / 邮箱的入参。
 * 两个字段都可选，只更新传了值的那个，留空表示该项不变。
 */
@Data
public class UserProfileDTO {

    /** 新的用户名，最长 50 字符（对应 sys_user.username） */
    private String username;

    /** 新的邮箱，最长 100 字符（对应 sys_user.email） */
    private String email;
}
