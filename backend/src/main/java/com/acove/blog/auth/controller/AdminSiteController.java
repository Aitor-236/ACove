package com.acove.blog.auth.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.acove.blog.auth.dto.SiteSettingDTO;
import com.acove.blog.auth.dto.SiteSettingVO;
import com.acove.blog.auth.service.SiteSettingService;
import com.acove.blog.common.result.Result;

import lombok.RequiredArgsConstructor;

/**
 * 后台「网站设置」接口：网站名、首页头图、首页中间的文字。
 * 路径在 /admin/** 下，不要加进 JwtInterceptor 的白名单。
 */
@RestController
@RequestMapping("/admin/site")
@RequiredArgsConstructor
public class AdminSiteController {

    private final SiteSettingService siteSettingService;

    /** 读取当前设置，网站设置页进入时调用。 */
    @GetMapping("/settings")
    public Result<SiteSettingVO> settings() {
        return Result.success(siteSettingService.getSettings());
    }

    /** 保存网站名 / 首页文字，或按空串清除头图。 */
    @PostMapping("/settings/update")
    public Result<SiteSettingVO> update(@RequestBody SiteSettingDTO settingDTO) {
        return Result.success(siteSettingService.updateSettings(settingDTO));
    }

    /** 上传首页头图，表单字段名固定为 file。 */
    @PostMapping("/hero-image")
    public Result<SiteSettingVO> uploadHeroImage(@RequestPart("file") MultipartFile file) {
        return Result.success(siteSettingService.updateHeroImage(file));
    }
}
