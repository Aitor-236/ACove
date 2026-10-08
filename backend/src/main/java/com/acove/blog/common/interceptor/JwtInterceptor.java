package com.acove.blog.common.interceptor;

import com.acove.blog.common.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor 
public class JwtInterceptor implements HandlerInterceptor {

    /** 校验通过后写入 request 的当前登录用户ID属性名，controller 用 @RequestAttribute 取。 */
    public static final String REQUEST_ATTRIBUTE_USER_ID = "userId";

    private final JwtUtil jwtUtil;

    @Override 
    public boolean preHandle(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler
    ) throws Exception {
        // Allow preflight requests
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true; 
        }
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7); // Remove "Bearer " prefix
            if (jwtUtil.verifyToken(token)) {
                request.setAttribute(REQUEST_ATTRIBUTE_USER_ID, JwtUtil.getUserId(token));
                return true; // Token is valid, proceed with the request
            }
        }
        // Token is missing or invalid, request 401
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401, \"message\": \"Unauthorized: Invalid or missing token.\", \"data\": null }");
        return false;
    }
}
