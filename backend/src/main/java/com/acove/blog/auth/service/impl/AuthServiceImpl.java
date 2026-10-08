package com.acove.blog.auth.service.impl;

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
        if (username != null && !username.trim().isEmpty()) {
            queryWrapper.eq(SysUser::getUsername, username);
        } else if (email != null && !email.trim().isEmpty()) {
            queryWrapper.eq(SysUser::getEmail, email);
        } else {
            throw new BusinessException("请输入用户名或邮箱");
        }  
        
        // Fetch user from database
        SysUser user = sysUserMapper.selectOne(queryWrapper);

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
