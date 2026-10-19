package com.acove.blog.auth.dto;

import lombok.Data;

/** 当前登录用户修改密码的入参。 */
@Data
public class ChangePasswordDTO {

    private String currentPassword;

    private String newPassword;
}
