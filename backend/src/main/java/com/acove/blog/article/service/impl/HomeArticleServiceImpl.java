package com.acove.blog.article.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acove.blog.article.dto.ArticleVO;
import com.acove.blog.article.dto.HomeArticleVO;
import com.acove.blog.article.entity.Article;
import com.acove.blog.article.entity.HomeArticle;
import com.acove.blog.article.mapper.ArticleMapper;
import com.acove.blog.article.mapper.HomeArticleMapper;
import com.acove.blog.article.service.HomeArticleService;
import com.acove.blog.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HomeArticleServiceImpl implements HomeArticleService {

    private static final String STATUS_PUBLISHED = "published";

    /** 后台一篇都没选时，首页兜底展示的已发布文章篇数 */
    private static final int FALLBACK_SIZE = 3;

    private final HomeArticleMapper homeArticleMapper;
    private final ArticleMapper articleMapper;
    private final ArticleVOAssembler articleVOAssembler;

    @Override
    public HomeArticleVO listHomeArticles() {
        long total = articleMapper.selectCount(
                new LambdaQueryWrapper<Article>().eq(Article::getStatus, STATUS_PUBLISHED));

        List<Article> selected = loadSelectedArticles();
        // 取消发布的文章不显示在首页，但选择保留，重新发布后自动回到原位置。
        List<Article> visible = selected.stream()
                .filter(article -> STATUS_PUBLISHED.equals(article.getStatus()))
                .collect(Collectors.toList());
        if (visible.isEmpty()) {
            visible = loadLatestPublished();
        }

        return new HomeArticleVO(articleVOAssembler.toVoList(visible), total);
    }

    @Override
    public List<ArticleVO> listSelectedArticles() {
        return articleVOAssembler.toVoList(loadSelectedArticles());
    }

    @Override
    @Transactional
    public void saveHomeArticles(List<Long> articleIds) {
        if (articleIds == null) {
            throw new BusinessException("文章ID列表不能为空");
        }

        List<Long> normalized = normalizeIds(articleIds);
        requireArticlesExist(normalized);

        // 整理结果是整体覆盖：先清空，再按顺序写入，sortOrder 从 0 开始递增。
        homeArticleMapper.delete(null);

        LocalDateTime now = LocalDateTime.now();
        int sortOrder = 0;
        for (Long articleId : normalized) {
            HomeArticle row = new HomeArticle();
            row.setArticleId(articleId);
            row.setSortOrder(sortOrder);
            row.setCreatedAt(now);
            homeArticleMapper.insert(row);
            sortOrder++;
        }
    }

    /** 按首页顺序读取已选文章，查不到的文章直接跳过（外键级联会先清掉这种行）。 */
    private List<Article> loadSelectedArticles() {
        List<HomeArticle> rows = homeArticleMapper.selectList(new LambdaQueryWrapper<HomeArticle>()
                .orderByAsc(HomeArticle::getSortOrder)
                .orderByAsc(HomeArticle::getArticleId));
        if (rows.isEmpty()) {
            return List.of();
        }

        Set<Long> articleIds = rows.stream()
                .map(HomeArticle::getArticleId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Article> articlesById = articleMapper.selectByIds(articleIds).stream()
                .collect(Collectors.toMap(Article::getId, article -> article));

        List<Article> ordered = new ArrayList<>();
        for (Long articleId : articleIds) {
            Article article = articlesById.get(articleId);
            if (article != null) {
                ordered.add(article);
            }
        }
        return ordered;
    }

    /** 兜底：最近三篇已发布文章，排序口径和公开列表一致。 */
    private List<Article> loadLatestPublished() {
        return articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, STATUS_PUBLISHED)
                .orderByDesc(Article::getCreatedAt)
                .orderByDesc(Article::getId)
                .last("limit " + FALLBACK_SIZE));
    }

    /** 去重保序（重复的只留第一次出现的位置），id 为空直接报 400。 */
    private List<Long> normalizeIds(List<Long> articleIds) {
        Map<Long, Boolean> unique = new LinkedHashMap<>();
        for (Long articleId : articleIds) {
            if (articleId == null) {
                throw new BusinessException("文章ID不能为空");
            }
            unique.putIfAbsent(articleId, Boolean.TRUE);
        }
        return new ArrayList<>(unique.keySet());
    }

    /** 所选文章必须都存在；草稿允许保留在列表里（它不上首页）。 */
    private void requireArticlesExist(List<Long> articleIds) {
        if (articleIds.isEmpty()) {
            return;
        }

        Set<Long> existingIds = articleMapper.selectByIds(articleIds).stream()
                .map(Article::getId)
                .collect(Collectors.toSet());
        if (existingIds.size() == articleIds.size()) {
            return;
        }

        Long missing = articleIds.stream()
                .filter(articleId -> !existingIds.contains(articleId))
                .findFirst()
                .orElse(null);
        throw new BusinessException("文章不存在：" + missing);
    }
}
