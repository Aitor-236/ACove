package com.acove.blog.auth.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.acove.blog.auth.dto.AdminSiteProfilePageVO;
import com.acove.blog.auth.dto.SiteProfileHtmlPackageVO;
import com.acove.blog.auth.dto.SiteProfilePageDTO;
import com.acove.blog.auth.service.SiteProfilePageService;
import com.acove.blog.common.interceptor.JwtInterceptor;
import com.acove.blog.common.result.Result;

import lombok.RequiredArgsConstructor;

/** Owner-only management endpoints for the public personal introduction page. */
@RestController
@RequestMapping("/admin/profile-page")
@RequiredArgsConstructor
public class AdminSiteProfilePageController {

    private final SiteProfilePageService siteProfilePageService;

    @GetMapping
    public Result<AdminSiteProfilePageVO> get(
            @RequestAttribute(name = JwtInterceptor.REQUEST_ATTRIBUTE_USER_ID, required = false) Long userId) {
        return Result.success(siteProfilePageService.getAdminPage(userId));
    }

    @PostMapping("/html-package")
    public Result<SiteProfileHtmlPackageVO> uploadHtmlPackage(
            @RequestAttribute(name = JwtInterceptor.REQUEST_ATTRIBUTE_USER_ID, required = false) Long userId,
            @RequestPart("file") MultipartFile file) {
        return Result.success(siteProfilePageService.uploadHtmlPackage(userId, file));
    }

    /** Returns a validated candidate document for the owner's sandboxed srcdoc preview. */
    @GetMapping("/html-preview/{bundleId}")
    public Result<String> previewHtmlPackage(
            @RequestAttribute(name = JwtInterceptor.REQUEST_ATTRIBUTE_USER_ID, required = false) Long userId,
            @PathVariable String bundleId) {
        return Result.success(siteProfilePageService.getAdminPreviewHtmlDocument(userId, bundleId));
    }

    @PostMapping("/save")
    public Result<AdminSiteProfilePageVO> save(
            @RequestAttribute(name = JwtInterceptor.REQUEST_ATTRIBUTE_USER_ID, required = false) Long userId,
            @RequestBody SiteProfilePageDTO pageDTO) {
        return Result.success(siteProfilePageService.save(userId, pageDTO));
    }
}
