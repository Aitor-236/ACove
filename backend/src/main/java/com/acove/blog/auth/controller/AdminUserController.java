package com.acove.blog.auth.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.acove.blog.auth.dto.UserProfileDTO;
import com.acove.blog.auth.dto.UserProfileVO;
import com.acove.blog.auth.service.AdminUserService;
import com.acove.blog.common.interceptor.JwtInterceptor;
import com.acove.blog.common.result.Result;

import lombok.RequiredArgsConstructor;

/**
 * 后台「个人管理」接口：只能操作 token 里的当前用户，
 * 路径在 /admin/** 下，不要加进 JwtInterceptor 的白名单。
 */
@RestController
@RequestMapping("/admin/user")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    /** 当前登录用户的资料，个人管理页进入时读取。 */
    @GetMapping("/profile")
    public Result<UserProfileVO> profile(
            @RequestAttribute(name = JwtInterceptor.REQUEST_ATTRIBUTE_USER_ID, required = false) Long userId) {
        return Result.success(adminUserService.getProfile(userId));
    }

    /** 修改用户名 / 邮箱，只更新请求体里带了值的字段，留空表示该项不变。 */
    @PostMapping("/profile/update")
    public Result<UserProfileVO> update(
            @RequestAttribute(name = JwtInterceptor.REQUEST_ATTRIBUTE_USER_ID, required = false) Long userId,
            @RequestBody UserProfileDTO profileDTO) {
        return Result.success(adminUserService.updateProfile(userId, profileDTO));
    }

    /** 上传头像图片，成功后返回新的头像地址；表单字段名固定为 file。 */
    @PostMapping("/avatar")
    public Result<UserProfileVO> uploadAvatar(
            @RequestAttribute(name = JwtInterceptor.REQUEST_ATTRIBUTE_USER_ID, required = false) Long userId,
            @RequestPart("file") MultipartFile file) {
        return Result.success(adminUserService.updateAvatar(userId, file));
    }
}
