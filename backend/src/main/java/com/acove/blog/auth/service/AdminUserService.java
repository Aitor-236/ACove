package com.acove.blog.auth.service;

import org.springframework.web.multipart.MultipartFile;

import com.acove.blog.auth.dto.UserProfileDTO;
import com.acove.blog.auth.dto.UserProfileVO;

/**
 * 后台「个人管理」：维护当前登录用户自己的用户名、邮箱和头像。
 * 只能改自己，用户ID 一律从 token 里取，不由前端传入。
 */
public interface AdminUserService {

    /** 读取当前登录用户的资料，用户不存在时抛 404。 */
    UserProfileVO getProfile(Long userId);

    /** 修改用户名 / 邮箱，只更新传了值的字段；重名或格式不合法抛业务异常。 */
    UserProfileVO updateProfile(Long userId, UserProfileDTO profileDTO);

    /** 保存上传的头像图片并写回数据库，返回更新后的资料。 */
    UserProfileVO updateAvatar(Long userId, MultipartFile file);
}
