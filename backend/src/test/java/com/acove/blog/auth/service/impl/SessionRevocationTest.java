package com.acove.blog.auth.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.acove.blog.auth.dto.ChangePasswordDTO;
import com.acove.blog.auth.dto.LoginDTO;
import com.acove.blog.auth.entity.SysUser;
import com.acove.blog.auth.mapper.SysUserMapper;
import com.acove.blog.common.exception.BusinessException;
import com.acove.blog.common.interceptor.JwtInterceptor;
import com.acove.blog.common.utils.JwtUtil;
import com.acove.blog.config.JwtProperties;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

/** Real JWT / BCrypt through login, password service and interceptor; isolated in-memory mapper. */
class SessionRevocationTest {
    private static final String SECRET = "isolated-session-test-key-only";
    private final Map<Long, SysUser> users = new HashMap<>();
    private SysUserMapper mapper;
    private JwtUtil jwt;
    private JwtInterceptor interceptor;
    private AuthServiceImpl auth;
    private AdminUserServiceImpl admin;

    @BeforeEach
    void setUp() {
        PasswordEncoder encoder = new BCryptPasswordEncoder(4);
        mapper = mock(SysUserMapper.class);
        JwtProperties config = new JwtProperties();
        config.setSecretKey(SECRET);
        config.setExpireTime(60_000);
        jwt = new JwtUtil(config);
        interceptor = new JwtInterceptor(jwt, mapper);
        auth = new AuthServiceImpl(mapper, encoder, jwt);
        admin = new AdminUserServiceImpl(mapper, encoder);
        for (long id : new long[] {7L, 8L}) {
            SysUser user = new SysUser();
            user.setId(id);
            user.setUsername("user" + id);
            user.setPassword(encoder.encode("original-password"));
            user.setTokenVersion(0L);
            users.put(id, user);
        }
        when(mapper.selectById(any())).thenAnswer(call -> users.get(call.getArgument(0)));
        when(mapper.selectOne(any())).thenAnswer(call -> users.get(7L));
        when(mapper.changePasswordAndRevokeTokens(any(), any(), any(), any())).thenAnswer(call -> {
            SysUser user = users.get(call.<Long>getArgument(0));
            if (user == null || !user.getPassword().equals(call.<String>getArgument(1))
                    || !user.getTokenVersion().equals(call.<Long>getArgument(3))) return 0;
            user.setPassword(call.getArgument(2));
            user.setTokenVersion(user.getTokenVersion() + 1);
            return 1;
        });
    }

    @Test
    void passwordChangeRevokesAllOwnTokensNewLoginWorksAndOtherUsersStaySignedIn() throws Exception {
        String first = login("original-password");
        String second = jwt.generateToken(7L, "user7", 0L);
        String other = jwt.generateToken(8L, "user8", 0L);
        assertAuthorized(first, 7L);
        assertAuthorized(other, 8L);

        admin.changePassword(7L, password("original-password", "new-password"));

        assertEquals(1L, users.get(7L).getTokenVersion());
        assertUnauthorized(first);
        assertUnauthorized(second);
        assertAuthorized(other, 8L);
        assertThrows(BusinessException.class, () -> login("original-password"));
        String next = login("new-password");
        assertEquals(1L, JwtUtil.getTokenVersion(next));
        assertAuthorized(next, 7L);
        // A second change must also revoke tokens obtained after the first change.
        admin.changePassword(7L, password("new-password", "third-password"));
        assertUnauthorized(next);
        assertAuthorized(login("third-password"), 7L);
        assertAuthorized(other, 8L);
    }

    @Test
    void wrongPasswordAndValidationFailuresPreserveCurrentSessions() throws Exception {
        String token = login("original-password");
        for (ChangePasswordDTO request : new ChangePasswordDTO[] {
                password("wrong", "new-password"), password("original-password", ""),
                password("original-password", "界".repeat(25))}) {
            assertThrows(BusinessException.class, () -> admin.changePassword(7L, request));
            assertEquals(0L, users.get(7L).getTokenVersion());
            assertAuthorized(token, 7L);
        }
        verify(mapper, never()).changePasswordAndRevokeTokens(any(), any(), any(), any());
    }

    @Test
    void accountResetVersionChangeAndUserDeletionInvalidateExistingTokens() throws Exception {
        String token = login("original-password");
        users.get(7L).setTokenVersion(1L); // Same version increment used by account reset SQL.
        assertUnauthorized(token);
        String current = login("original-password");
        users.remove(7L);
        assertUnauthorized(current);
    }

    @Test
    void legacyMissingMalformedAndNegativeClaimsAreRejectedWithoutReadingUsers() throws Exception {
        assertUnauthorized(JWT.create().withClaim("userId", 7L).withExpiresAt(future()).sign(algorithm()));
        assertUnauthorized(JWT.create().withClaim("tokenVersion", 0L).withExpiresAt(future()).sign(algorithm()));
        assertUnauthorized(JWT.create().withClaim("userId", 7L).withClaim("tokenVersion", "wrong")
                .withExpiresAt(future()).sign(algorithm()));
        assertUnauthorized(JWT.create().withClaim("userId", 7L).withClaim("tokenVersion", -1L)
                .withExpiresAt(future()).sign(algorithm()));
        verify(mapper, never()).selectById(any());
    }

    @Test
    void missingExpirationExpiredAndInvalidSignatureNeverReachDatabase() throws Exception {
        assertUnauthorized(JWT.create().withClaim("userId", 7L).withClaim("tokenVersion", 0L).sign(algorithm()));
        assertUnauthorized(JWT.create().withClaim("userId", 7L).withClaim("tokenVersion", 0L)
                .withExpiresAt(new Date(0)).sign(algorithm()));
        assertUnauthorized(JWT.create().withClaim("userId", 7L).withClaim("tokenVersion", 0L)
                .withExpiresAt(future()).sign(Algorithm.HMAC256("wrong-secret")));
        verify(mapper, never()).selectById(any());
    }

    @Test
    void missingBearerAndPreflightKeepExistingHttpContract() throws Exception {
        assertUnauthorized(null);
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/admin/user/profile");
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        verify(mapper, never()).selectById(any());
    }

    private String login(String password) {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("user7");
        dto.setPassword(password);
        return auth.login(dto).getToken();
    }

    private ChangePasswordDTO password(String current, String next) {
        ChangePasswordDTO dto = new ChangePasswordDTO();
        dto.setCurrentPassword(current);
        dto.setNewPassword(next);
        return dto;
    }

    private void assertAuthorized(String token, Long id) throws Exception {
        var request = request(token);
        var response = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertEquals(id, request.getAttribute(JwtInterceptor.REQUEST_ATTRIBUTE_USER_ID));
    }

    private void assertUnauthorized(String token) throws Exception {
        var request = request(token);
        var response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":401"));
        assertNull(request.getAttribute(JwtInterceptor.REQUEST_ATTRIBUTE_USER_ID));
    }

    private MockHttpServletRequest request(String token) {
        var request = new MockHttpServletRequest("GET", "/admin/user/profile");
        if (token != null) request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private Algorithm algorithm() { return Algorithm.HMAC256(SECRET); }
    private Date future() { return new Date(System.currentTimeMillis() + 60_000); }
}
