package com.acove.blog.article.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.acove.blog.article.dto.UploadImageVO;
import com.acove.blog.article.service.AdminUploadService;
import com.acove.blog.common.result.Result;

import lombok.RequiredArgsConstructor;

/**
 * 后台上传接口：目前只有正文配图。
 * 路径在 /admin/** 下，不要加进 JwtInterceptor 的白名单。
 */
@RestController
@RequestMapping("/admin/upload")
@RequiredArgsConstructor
public class AdminUploadController {

    private final AdminUploadService adminUploadService;

    /** 上传正文配图，表单字段名固定为 file，成功后返回可直接写进 Markdown 的地址。 */
    @PostMapping("/image")
    public Result<UploadImageVO> uploadImage(@RequestPart("file") MultipartFile file) {
        return Result.success(adminUploadService.uploadImage(file));
    }
}
