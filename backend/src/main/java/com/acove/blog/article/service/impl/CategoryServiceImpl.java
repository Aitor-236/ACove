package com.acove.blog.article.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.acove.blog.article.dto.CategoryVO;
import com.acove.blog.article.entity.Article;
import com.acove.blog.article.entity.ArticleCategory;
import com.acove.blog.article.mapper.ArticleCategoryMapper;
import com.acove.blog.article.mapper.ArticleMapper;
import com.acove.blog.article.service.CategoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private static final String STATUS_PUBLISHED = "published";

    private final ArticleCategoryMapper articleCategoryMapper;
    private final ArticleMapper articleMapper;

    @Override
    public List<CategoryVO> listWithArticleCounts() {
        List<ArticleCategory> categories = articleCategoryMapper.selectList(
                new LambdaQueryWrapper<ArticleCategory>()
                        .orderByAsc(ArticleCategory::getSortOrder)
                        .orderByAsc(ArticleCategory::getId));

        Map<Long, Long> countByCategoryId = loadPublishedCountByCategoryId();

        return categories.stream()
                .map(category -> {
                    CategoryVO vo = new CategoryVO();
                    vo.setId(category.getId());
                    vo.setName(category.getName());
                    vo.setSlug(category.getSlug());
                    vo.setArticleCount(countByCategoryId.getOrDefault(category.getId(), 0L));
                    return vo;
                })
                .collect(Collectors.toList());
    }

    private Map<Long, Long> loadPublishedCountByCategoryId() {
        List<Map<String, Object>> rows = articleMapper.selectMaps(
                new QueryWrapper<Article>()
                        .select("category_id", "COUNT(*) AS article_count")
                        .eq("status", STATUS_PUBLISHED)
                        .groupBy("category_id"));

        Map<Long, Long> counts = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object categoryId = row.get("category_id");
            Object count = row.get("article_count");
            if (categoryId instanceof Number && count instanceof Number) {
                counts.put(
                        ((Number) categoryId).longValue(),
                        ((Number) count).longValue());
            }
        }
        return counts;
    }
}
