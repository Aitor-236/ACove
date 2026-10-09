package com.acove.blog.article.controller;

import com.acove.blog.article.dto.ArticleVO;
import com.acove.blog.article.dto.ArticlePublicDetailVO;
import com.acove.blog.article.dto.HomeArticleVO;
import com.acove.blog.article.service.ArticleService;
import com.acove.blog.article.service.HomeArticleService;
import com.acove.blog.common.result.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/article")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;
    private final HomeArticleService homeArticleService;

    /**
     * 公开的文章卡片分页列表，只返回已发布文章。
     */
    @GetMapping("/list")
    public Result<Page<ArticleVO>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tag) {
        return Result.success(articleService.listPublished(page, size, category, keyword, tag));
    }

    /**
     * 公开的文章详情，只暴露已发布文章，草稿一律按不存在处理。
     */
    @GetMapping("/detail/{id}")
    public Result<ArticlePublicDetailVO> detail(@PathVariable Long id) {
        return Result.success(articleService.getPublishedDetail(id));
    }

    /**
     * 首页动态列表：后台「首页展示」挑好的已发布文章按顺序返回，
     * 一篇都没挑时回退到最近三篇已发布文章；同时带上已发布文章总数。
     */
    @GetMapping("/home")
    public Result<HomeArticleVO> home() {
        return Result.success(homeArticleService.listHomeArticles());
    }
}
