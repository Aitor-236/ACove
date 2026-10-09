package com.acove.blog.article.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.acove.blog.article.dto.ArticleVO;
import com.acove.blog.article.entity.Article;
import com.acove.blog.article.entity.ArticleCategory;
import com.acove.blog.article.mapper.ArticleCategoryMapper;
import com.acove.blog.article.mapper.ArticleTagMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;

/**
 * 把文章实体统一组装成 ArticleVO：补上分类名称、标签名，只暴露前端需要的字段。
 * 公开列表、后台列表和首页展示共用，保证几边返回结构一致。
 */
@Component
@RequiredArgsConstructor
public class ArticleVOAssembler {

    private final ArticleCategoryMapper articleCategoryMapper;
    private final ArticleTagMapper articleTagMapper;

    Page<ArticleVO> toVoPage(Page<Article> articlePage) {
        Page<ArticleVO> voPage = new Page<>(
                articlePage.getCurrent(),
                articlePage.getSize(),
                articlePage.getTotal());
        voPage.setRecords(toVoList(articlePage.getRecords()));
        return voPage;
    }

    /**
     * 把一组文章实体转换成展示对象：一次补齐分类名和标签名，顺序保持不变。
     * 首页展示（有序列表）和列表分页共用同一套字段拼装。
     */
    List<ArticleVO> toVoList(List<Article> articles) {
        if (articles == null || articles.isEmpty()) {
            return List.of();
        }

        Map<Long, String> categoryNames = loadCategoryNames(articles);
        Map<Long, List<String>> tagNames = loadTagNames(articles);
        return articles.stream()
                .map(article -> {
                    ArticleVO vo = ArticleVO.from(article, categoryNames.get(article.getCategoryId()));
                    vo.setTags(tagNames.getOrDefault(article.getId(), List.of()));
                    return vo;
                })
                .collect(Collectors.toList());
    }

    Page<ArticleVO> emptyPage(long page, long size) {
        Page<ArticleVO> empty = new Page<>(page, size);
        empty.setRecords(List.of());
        return empty;
    }

    private Map<Long, String> loadCategoryNames(List<Article> articles) {
        Set<Long> categoryIds = articles.stream()
                .map(Article::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (categoryIds.isEmpty()) {
            return Map.of();
        }
        return articleCategoryMapper.selectByIds(categoryIds).stream()
                .collect(Collectors.toMap(ArticleCategory::getId, ArticleCategory::getName));
    }

    /** 一次查出一页文章的所有标签，按标签ID顺序追加到各自文章下。 */
    private Map<Long, List<String>> loadTagNames(List<Article> articles) {
        Set<Long> articleIds = articles.stream()
                .map(Article::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (articleIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<String>> tagNames = new HashMap<>();
        for (Map<String, Object> row : articleTagMapper.selectTagNamesByArticleIds(articleIds)) {
            Object articleId = row.get("article_id");
            Object name = row.get("name");
            if (articleId instanceof Number && name != null) {
                tagNames.computeIfAbsent(((Number) articleId).longValue(), key -> new ArrayList<>())
                        .add(String.valueOf(name));
            }
        }
        return tagNames;
    }
}
