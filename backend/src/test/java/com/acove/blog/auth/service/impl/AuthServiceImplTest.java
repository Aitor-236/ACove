package com.acove.blog.auth.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.acove.blog.auth.dto.LoginDTO;
import com.acove.blog.auth.entity.SysUser;
import com.acove.blog.auth.mapper.SysUserMapper;
import com.acove.blog.common.exception.BusinessException;
import com.acove.blog.common.utils.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;

class AuthServiceImplTest {
    private SysUserMapper mapper;
    private PasswordEncoder encoder;
    private JwtUtil jwt;
    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysUser.class);
        mapper = mock(SysUserMapper.class);
        encoder = mock(PasswordEncoder.class);
        jwt = mock(JwtUtil.class);
        service = new AuthServiceImpl(mapper, encoder, jwt);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void emailLoginReadsAtMostTwoUsersAndReturnsTokenForUniqueMatch() {
        SysUser user = user();
        when(mapper.selectList(any())).thenReturn(List.of(user));
        when(encoder.matches("password", "hash")).thenReturn(true);
        when(jwt.generateToken(7L, "alice", 3L)).thenReturn("token");

        var result = service.login(request(null, "alice@example.com"));

        assertEquals("token", result.getToken());
        assertEquals("alice", result.getUsername());
        ArgumentCaptor<LambdaQueryWrapper> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectList(query.capture());
        assertTrue(query.getValue().getSqlSegment().endsWith("LIMIT 2"));
        verify(mapper, never()).selectOne(any());
    }

    @Test
    void duplicateEmailRejectsBeforeCheckingPasswordsOrIssuingToken() {
        when(mapper.selectList(any())).thenReturn(List.of(user(), user()));

        var error = assertThrows(BusinessException.class,
                () -> service.login(request(null, "alice@example.com")));

        assertEquals(400, error.getCode());
        assertEquals("邮箱关联多个账号，请使用用户名登录并联系管理员", error.getMessage());
        verifyNoInteractions(encoder, jwt);
    }

    @Test
    void usernameStillTakesPrecedenceOverEmail() {
        when(mapper.selectOne(any())).thenReturn(user());
        when(encoder.matches("password", "hash")).thenReturn(true);

        service.login(request("alice", "duplicate@example.com"));

        verify(mapper, never()).selectList(any());
        verify(jwt).generateToken(7L, "alice", 3L);
    }

    @Test
    void missingEmailMatchReturnsInvalidCredentialsWithoutToken() {
        when(mapper.selectList(any())).thenReturn(List.of());

        var error = assertThrows(BusinessException.class,
                () -> service.login(request(null, "missing@example.com")));

        assertEquals(401, error.getCode());
        verifyNoInteractions(encoder, jwt);
    }

    @Test
    void wrongPasswordDoesNotIssueToken() {
        when(mapper.selectList(any())).thenReturn(List.of(user()));

        var error = assertThrows(BusinessException.class,
                () -> service.login(request(null, "alice@example.com")));

        assertEquals(401, error.getCode());
        verifyNoInteractions(jwt);
    }

    private LoginDTO request(String username, String email) {
        LoginDTO dto = new LoginDTO();
        dto.setUsername(username);
        dto.setEmail(email);
        dto.setPassword("password");
        return dto;
    }

    private SysUser user() {
        SysUser user = new SysUser();
        user.setId(7L);
        user.setTokenVersion(3L);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPassword("hash");
        return user;
    }
}
