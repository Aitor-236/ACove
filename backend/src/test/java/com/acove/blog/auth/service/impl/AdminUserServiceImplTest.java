package com.acove.blog.auth.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.acove.blog.auth.dto.ChangePasswordDTO;
import com.acove.blog.auth.dto.UserProfileDTO;
import com.acove.blog.auth.entity.SysUser;
import com.acove.blog.auth.mapper.SysUserMapper;
import com.acove.blog.common.exception.BusinessException;

class AdminUserServiceImplTest {
    private SysUserMapper mapper;
    private PasswordEncoder passwordEncoder;
    private AdminUserServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(SysUserMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new AdminUserServiceImpl(mapper, passwordEncoder);
        SysUser user = new SysUser();
        user.setId(7L);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPassword("old-hash");
        user.setTokenVersion(2L);
        when(mapper.selectById(7L)).thenReturn(user);
        when(mapper.selectCount(any())).thenReturn(0L);
    }

    @ParameterizedTest
    @CsvSource({"email,邮箱已被占用，换一个试试", "username,用户名已被占用，换一个试试"})
    void existingValueIsRejectedBeforeWriting(String field, String message) {
        when(mapper.selectCount(any())).thenReturn(1L);

        var error = assertThrows(BusinessException.class,
                () -> service.updateProfile(7L, request(field)));

        assertEquals(400, error.getCode());
        assertEquals(message, error.getMessage());
        verify(mapper, never()).updateById(any(SysUser.class));
    }

    @ParameterizedTest
    @CsvSource({"sys_user.uk_email,邮箱已被占用，换一个试试", "uk_email,邮箱已被占用，换一个试试",
            "sys_user.uk_username,用户名已被占用，换一个试试", "uk_username,用户名已被占用，换一个试试"})
    void concurrentUniqueKeyConflictBecomesBusinessError(String key, String message) {
        when(mapper.updateById(any(SysUser.class))).thenThrow(duplicate(key, 1062));
        UserProfileDTO request = request("email");
        request.setUsername("new-name");

        var error = assertThrows(BusinessException.class, () -> service.updateProfile(7L, request));

        assertEquals(400, error.getCode());
        assertEquals(message, error.getMessage());
    }

    @Test
    void successfulUpdateUsesCurrentUserIdAndLeavesPasswordAndRoleUnset() {
        service.updateProfile(7L, request("email"));

        ArgumentCaptor<SysUser> update = ArgumentCaptor.forClass(SysUser.class);
        verify(mapper).updateById(update.capture());
        assertEquals(7L, update.getValue().getId());
        assertEquals("new@example.com", update.getValue().getEmail());
        assertNull(update.getValue().getPassword());
        assertNull(update.getValue().getTokenVersion());
        assertNull(update.getValue().getRole());
    }

    @Test
    void changePasswordVerifiesCurrentPasswordAndStoresOnlyEncodedValue() {
        when(passwordEncoder.matches("current", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
        when(mapper.changePasswordAndRevokeTokens(7L, "old-hash", "new-hash", 2L)).thenReturn(1);

        service.changePassword(7L, passwordRequest("current", "new-password"));

        verify(mapper).changePasswordAndRevokeTokens(7L, "old-hash", "new-hash", 2L);
        verify(mapper, never()).updateById(any(SysUser.class));
    }

    @Test
    void concurrentPasswordChangeIsRejectedWithoutOverwritingNewCredentials() {
        when(passwordEncoder.matches("current", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

        var error = assertThrows(BusinessException.class,
                () -> service.changePassword(7L, passwordRequest("current", "new-password")));

        assertEquals(409, error.getCode());
        verify(mapper).changePasswordAndRevokeTokens(7L, "old-hash", "new-hash", 2L);
        verify(mapper, never()).updateById(any(SysUser.class));
    }

    @Test
    void changePasswordRejectsWrongCurrentPasswordWithoutWriting() {
        when(passwordEncoder.matches("wrong", "old-hash")).thenReturn(false);

        var error = assertThrows(BusinessException.class,
                () -> service.changePassword(7L, passwordRequest("wrong", "new-password")));

        assertEquals("当前密码不正确", error.getMessage());
        verify(mapper, never()).updateById(any(SysUser.class));
        verify(passwordEncoder, never()).encode(any());
        verify(mapper, never()).changePasswordAndRevokeTokens(any(), any(), any(), any());
    }

    @Test
    void changePasswordRejectsEmptyAndOverlongPasswords() {
        var empty = assertThrows(BusinessException.class,
                () -> service.changePassword(7L, passwordRequest("current", "")));
        var overlong = assertThrows(BusinessException.class,
                () -> service.changePassword(7L, passwordRequest("current", "界".repeat(25))));

        assertEquals("请输入新密码", empty.getMessage());
        assertEquals("新密码不能超过 72 个 UTF-8 字节", overlong.getMessage());
        verify(passwordEncoder, never()).matches(any(), any());
        verify(mapper, never()).updateById(any(SysUser.class));
    }

    @Test
    void unknownUniqueKeyIsNotMislabeledAsEmailConflict() {
        DuplicateKeyException failure = duplicate("another_key", 1062);
        when(mapper.updateById(any(SysUser.class))).thenThrow(failure);

        assertSame(failure, assertThrows(DuplicateKeyException.class,
                () -> service.updateProfile(7L, request("email"))));
    }

    @Test
    void unexpectedSqlErrorCodeIsNotConverted() {
        DuplicateKeyException failure = duplicate("sys_user.uk_email", 9999);
        when(mapper.updateById(any(SysUser.class))).thenThrow(failure);

        assertSame(failure, assertThrows(DuplicateKeyException.class,
                () -> service.updateProfile(7L, request("email"))));
    }

    @Test
    void otherDatabaseFailuresKeepSystemErrorPath() {
        var failure = new DataIntegrityViolationException("unrelated database failure");
        when(mapper.updateById(any(SysUser.class))).thenThrow(failure);

        assertSame(failure, assertThrows(DataIntegrityViolationException.class,
                () -> service.updateProfile(7L, request("email"))));
    }

    private DuplicateKeyException duplicate(String key, int code) {
        return new DuplicateKeyException("update failed",
                new SQLException("Duplicate entry 'new@example.com' for key '" + key + "'", "23000", code));
    }

    private UserProfileDTO request(String field) {
        UserProfileDTO dto = new UserProfileDTO();
        if ("email".equals(field)) {
            dto.setEmail("new@example.com");
        } else {
            dto.setUsername("new-name");
        }
        return dto;
    }

    private ChangePasswordDTO passwordRequest(String currentPassword, String newPassword) {
        ChangePasswordDTO dto = new ChangePasswordDTO();
        dto.setCurrentPassword(currentPassword);
        dto.setNewPassword(newPassword);
        return dto;
    }
}
