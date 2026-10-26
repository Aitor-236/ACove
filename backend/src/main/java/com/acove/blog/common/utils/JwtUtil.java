package com.acove.blog.common.utils;

import java.util.Date;
import org.springframework.stereotype.Component;

import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

import lombok.RequiredArgsConstructor;

import com.acove.blog.config.JwtProperties;
import com.auth0.jwt.JWT;

@Component 
@RequiredArgsConstructor 
public class JwtUtil {
    private final JwtProperties jwtProperties;

    /**
     * Generates a JWT token for the given user ID and username.
     * @param userId The ID of the user for whom to generate the token.
     * @param username The username of the user for whom to generate the token.
     * @param tokenVersion The user's current session version in the database.
     * @return The generated JWT token.
     */
    public String generateToken(Long userId, String username, Long tokenVersion) {
        if (userId == null || tokenVersion == null || tokenVersion < 0) {
            throw new IllegalArgumentException("User ID and a non-negative token version are required");
        }
        Date date = new Date(System.currentTimeMillis() + jwtProperties.getExpireTime());
        return JWT.create()
                .withClaim("userId", userId)
                .withClaim("username", username)
                .withClaim("tokenVersion", tokenVersion)
                .withExpiresAt(date)
                .sign(Algorithm.HMAC256(jwtProperties.getSecretKey()));
    }
    
    /**
     * Verifies the given JWT token.
     * @param token The JWT token to verify.
     * @return true if the token is valid, false otherwise.
     */
    public boolean verifyToken(String token) {
        try {
            JWT.require(Algorithm.HMAC256(jwtProperties.getSecretKey()))
                    .withClaimPresence("exp").build().verify(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extracts the username from the given JWT token.
     * @param token The JWT token from which to extract the username.
     * @return The username extracted from the token, or null if the token is invalid or the username claim is not present.
     */
    public static String getUsername(String token) {
        try {
            DecodedJWT jwt = JWT.decode(token);
            return jwt.getClaim("username").asString();
        } catch (Exception e) {
            return null;
        }
    }

    /** 仅在签名校验通过后使用；缺少版本的历史令牌返回 null。 */
    public static Long getTokenVersion(String token) {
        try {
            return JWT.decode(token).getClaim("tokenVersion").asLong();
        } catch (Exception e) {
            return null;
        }
    }

    /** 仅在签名校验通过后使用；缺少用户 ID 返回 null。 */
    public static Long getUserId(String token) {
        try {
            DecodedJWT jwt = JWT.decode(token);
            return jwt.getClaim("userId").asLong();
        } catch (Exception e) {
            return null;
        }
    }
}
