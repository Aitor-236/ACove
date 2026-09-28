package com.aitor.blog.visit.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 生成访客标识，作为 UV 去重的依据。
 *
 * <p>思路是「Cookie 优先、请求信息兜底」：首次来访用 IP + User-Agent 的 SHA-256 哈希当标识，
 * 同时把这个哈希写进 Cookie；之后同一个浏览器直接复用 Cookie 里的值。这样：
 * <ul>
 *   <li>同一访客当天多次访问只算一个 UV（Cookie 命中）；</li>
 *   <li>访客换网络（IP 变了）也不会被当成新访客；</li>
 *   <li>浏览器禁用 Cookie 时不会每次都生成新标识，退化回 IP + UA 去重，UV 不会虚高。</li>
 * </ul>
 * 库里只存哈希，不存原始 IP / User-Agent。
 */
@Component
public class VisitorKeyResolver {

    /** 访客标识 Cookie 名，前端不用关心，浏览器自动带上。 */
    public static final String COOKIE_NAME = "blog_vid";

    /** Cookie 有效期一年。 */
    private static final int COOKIE_MAX_AGE_SECONDS = 365 * 24 * 60 * 60;

    /** 合法标识：32 位小写十六进制；不合法（被篡改/粘贴了别的值）就当没有 Cookie，重新生成。 */
    private static final Pattern VALID_KEY = Pattern.compile("^[0-9a-f]{32}$");

    /** 标识长度，与 visit_visitor.visitor_key CHAR(32) 对齐。 */
    private static final int KEY_LENGTH = 32;

    /**
     * 解析（必要时生成）访客标识。没有有效 Cookie 时会在响应里种一个新的 Cookie。
     */
    public String resolve(HttpServletRequest request, HttpServletResponse response) {
        String fromCookie = readCookie(request);
        if (fromCookie != null) {
            return fromCookie;
        }
        String key = hash(clientIp(request) + "|" + userAgent(request));
        writeCookie(request, response, key);
        return key;
    }

    private String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                String value = cookie.getValue();
                if (value != null && VALID_KEY.matcher(value).matches()) {
                    return value;
                }
                return null;
            }
        }
        return null;
    }

    /**
     * 种访客 Cookie：HttpOnly + SameSite=Lax；只有走 HTTPS 时才加 Secure
     * （本地 http 开发不加，否则浏览器可能不收这个 Cookie，UV 会虚高）。
     */
    private void writeCookie(HttpServletRequest request, HttpServletResponse response, String key) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, key)
                .path("/")
                .maxAge(COOKIE_MAX_AGE_SECONDS)
                .httpOnly(true)
                .sameSite("Lax")
                .secure(isHttps(request))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /** 直连时看 request.isSecure()，部署在 Nginx 后面时看 X-Forwarded-Proto。 */
    private boolean isHttps(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (forwardedProto == null || forwardedProto.isBlank()) {
            return false;
        }
        int comma = forwardedProto.indexOf(',');
        String first = (comma >= 0 ? forwardedProto.substring(0, comma) : forwardedProto).trim();
        return "https".equalsIgnoreCase(first);
    }

    /**
     * 取真实客户端 IP：部署时后端在 Nginx 后面，remoteAddr 是容器地址，得看代理头。
     * 取不到就退回 remoteAddr（本地开发直连的情况）。
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            String first = (comma >= 0 ? forwarded.substring(0, comma) : forwarded).trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr == null ? "" : remoteAddr;
    }

    private String userAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return userAgent == null ? "" : userAgent;
    }

    private String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, KEY_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            // JDK 一定带 SHA-256，真的没有就没法做 UV 去重了
            throw new IllegalStateException("当前运行环境缺少 SHA-256 实现", e);
        }
    }
}
