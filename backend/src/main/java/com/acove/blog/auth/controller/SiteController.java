package com.acove.blog.auth.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acove.blog.auth.dto.SiteOwnerVO;
import com.acove.blog.auth.dto.SiteSettingVO;
import com.acove.blog.auth.service.SiteProfileService;
import com.acove.blog.auth.service.SiteSettingService;
import com.acove.blog.common.result.Result;

import lombok.RequiredArgsConstructor;

/**
 * 前台站点资料接口，匿名可访问（路径已加进 JwtInterceptor 白名单）。
 */
@RestController
@RequestMapping("/site")
@RequiredArgsConstructor
public class SiteController {

    private final SiteProfileService siteProfileService;
    private final SiteSettingService siteSettingService;

    /** 首页要展示的站长资料：角色最高的那个账号的用户名和头像。 */
    @GetMapping("/owner")
    public Result<SiteOwnerVO> owner() {
        return Result.success(siteProfileService.getSiteOwner());
    }

    /** 前台要展示的站点设置：网站名、首页头图、首页中间的文字。 */
    @GetMapping("/settings")
    public Result<SiteSettingVO> settings() {
        return Result.success(siteSettingService.getSettings());
    }
}
