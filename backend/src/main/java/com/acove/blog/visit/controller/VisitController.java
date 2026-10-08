package com.acove.blog.visit.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acove.blog.common.result.Result;
import com.acove.blog.visit.service.VisitStatService;
import com.acove.blog.visit.support.VisitorKeyResolver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 访问上报接口：前台每切换一次页面就调一次，匿名可访问（路径已加进 JwtInterceptor 白名单）。
 */
@RestController
@RequestMapping("/visit")
@RequiredArgsConstructor
public class VisitController {

    private final VisitStatService visitStatService;
    private final VisitorKeyResolver visitorKeyResolver;

    /**
     * 记录一次页面浏览。前端不需要传任何参数，访客标识由服务端 Cookie 维护；
     * 用 POST 是因为它会产生写入（也方便用 sendBeacon / keepalive 上报）。
     */
    @PostMapping("/report")
    public Result<Void> report(HttpServletRequest request, HttpServletResponse response) {
        visitStatService.recordVisit(visitorKeyResolver.resolve(request, response));
        return Result.success();
    }
}
