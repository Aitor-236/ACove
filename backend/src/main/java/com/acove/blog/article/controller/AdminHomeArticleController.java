package com.acove.blog.article.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acove.blog.article.dto.ArticleVO;
import com.acove.blog.article.dto.HomeArticleDTO;
import com.acove.blog.article.service.HomeArticleService;
import com.acove.blog.common.result.Result;

import lombok.RequiredArgsConstructor;

/**
 * 后台「首页展示」接口，需要登录（路径不在白名单里）。
 * 读写都围绕 home_article 这张排序表，文章本身仍走 /admin/article/* 维护。
 */
@RestController
@RequestMapping("/admin/home-article")
@RequiredArgsConstructor
public class AdminHomeArticleController {

    private final HomeArticleService homeArticleService;

    /**
     * 已选文章，数组顺序就是首页展示顺序；包含当前是草稿的文章，
     * 前端用 status 标出「未发布」，方便知道它暂时不会出现在首页。
     */
    @GetMapping("/list")
    public Result<List<ArticleVO>> list() {
        return Result.success(homeArticleService.listSelectedArticles());
    }

    /**
     * 整体覆盖首页展示选择，请求体里的 articleIds 顺序就是首页顺序。
     * 空数组表示清空（首页回退到最近三篇）。
     */
    @PostMapping("/save")
    public Result<Void> save(@RequestBody HomeArticleDTO homeArticleDTO) {
        homeArticleService.saveHomeArticles(homeArticleDTO == null ? null : homeArticleDTO.getArticleIds());
        return Result.success();
    }
}
