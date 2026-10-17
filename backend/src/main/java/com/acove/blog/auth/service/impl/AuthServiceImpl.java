package com.acove.blog.auth.service.impl;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.acove.blog.auth.dto.LoginDTO;
import com.acove.blog.auth.dto.LoginVO;
import com.acove.blog.auth.entity.SysUser;
import com.acove.blog.auth.service.AuthService;
import com.acove.blog.auth.mapper.SysUserMapper;
import com.acove.blog.common.exception.BusinessException;
import com.acove.blog.common.utils.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor 
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;

    private final PasswordEncoder passwordEncoder;

    private final JwtUtil jwtUtil;

    @Override
    public LoginVO login(LoginDTO loginDTO) {
        // get username, email, and password  from loginDTO
        String username = loginDTO.getUsername();
        String email = loginDTO.getEmail();
        String password = loginDTO.getPassword();

        // Query user by username or email
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        SysUser user;
        if (username != null && !username.trim().isEmpty()) {
            queryWrapper.eq(SysUser::getUsername, username);
            user = sysUserMapper.selectOne(queryWrapper);
        } else if (email != null && !email.trim().isEmpty()) {
            queryWrapper.eq(SysUser::getEmail, email).last("LIMIT 2");
            List<SysUser> users = sysUserMapper.selectList(queryWrapper);
            if (users.size() > 1) {
                throw new BusinessException("邮箱关联多个账号，请使用用户名登录并联系管理员");
            }
            user = users.isEmpty() ? null : users.getFirst();
        } else {
            throw new BusinessException("请输入用户名或邮箱");
        }  
        
        // check password
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        // Generate JWT token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());

        // Return VO with token and user info
        return new LoginVO(token, user.getUsername(), user.getEmail());
    }
    
}
