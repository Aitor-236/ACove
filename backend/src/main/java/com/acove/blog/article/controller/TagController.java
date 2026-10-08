package com.acove.blog.article.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acove.blog.article.dto.TagVO;
import com.acove.blog.article.service.TagService;
import com.acove.blog.common.result.Result;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/tag")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    /**
     * 公开的标签列表，附已发布文章数，供文章页标签面板使用。
     */
    @GetMapping("/list")
    public Result<List<TagVO>> list() {
        return Result.success(tagService.listPublishedTags());
    }
}
