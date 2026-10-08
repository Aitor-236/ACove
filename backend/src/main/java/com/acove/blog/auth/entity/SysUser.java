package com.acove.blog.auth.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data 
@TableName("sys_user") 
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    private String email;

    /** 头像地址，默认空字符串表示未设置头像 */
    private String avatar;

    /** 角色：owner-站长（前台首页展示的人）、admin-管理员、user-普通用户 */
    private String role;

    private LocalDateTime createTime;
}
