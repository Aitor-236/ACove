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
    // This class is intended to provide utility methods for handling JWT.
    // The implementation details for generating, validating, and parsing JWTs would be added here.

    private final JwtProperties jwtProperties;

    /**
     * Generates a JWT token for the given user ID and username.
     * @param userId The ID of the user for whom to generate the token.
     * @param username The username of the user for whom to generate the token.
     * @return The generated JWT token.
     */
    public String generateToken(Long userId, String username) {
        Date date = new Date(System.currentTimeMillis() + jwtProperties.getExpireTime());
        return JWT.create()
                .withClaim("userId", userId)
                .withClaim("username", username)
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
            JWT.require(Algorithm.HMAC256(jwtProperties.getSecretKey())).build().verify(token);
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

    /**
     * Extracts the user ID from the given JWT token.
     * @param token The JWT token from which to extract the user ID.
     * @return The user ID extracted from the token, or null if the token is invalid or the userId claim is not present.
     */
    public static Long getUserId(String token) {
        try {
            DecodedJWT jwt = JWT.decode(token);
            return jwt.getClaim("userId").asLong();
        } catch (Exception e) {
            return null;
        }
    }
}
