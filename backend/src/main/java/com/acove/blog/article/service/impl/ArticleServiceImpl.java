package com.acove.blog.article.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.acove.blog.article.dto.ArticlePublicDetailVO;
import com.acove.blog.article.dto.ArticleVO;
import com.acove.blog.article.entity.Article;
import com.acove.blog.article.entity.ArticleCategory;
import com.acove.blog.article.mapper.ArticleCategoryMapper;
import com.acove.blog.article.mapper.ArticleMapper;
import com.acove.blog.article.mapper.ArticleTagMapper;
import com.acove.blog.article.service.ArticleService;
import com.acove.blog.common.exception.BusinessException;
import com.acove.blog.common.utils.PageParam;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ArticleServiceImpl implements ArticleService {

    private static final String STATUS_PUBLISHED = "published";

    private final ArticleMapper articleMapper;
    private final ArticleCategoryMapper articleCategoryMapper;
    private final ArticleTagMapper articleTagMapper;
    private final ArticleVOAssembler articleVOAssembler;

    @Override
    public Page<ArticleVO> listPublished(long page, long size, String categorySlug, String keyword,
            String tagName) {
        size = PageParam.requireValid(page, size);

        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, STATUS_PUBLISHED)
                // 列表卡片展示的是更新时间，但排序暂时按创建时间倒序；
                // 同一秒创建的记录再按 ID 倒序兜底，避免分页出现顺序抖动。
                .orderByDesc(Article::getCreatedAt)
                .orderByDesc(Article::getId);

        if (StringUtils.hasText(categorySlug)) {
            ArticleCategory category = articleCategoryMapper.selectOne(
                    new LambdaQueryWrapper<ArticleCategory>()
                            .eq(ArticleCategory::getSlug, categorySlug));
            if (category == null) {
                return articleVOAssembler.emptyPage(page, size);
            }
            wrapper.eq(Article::getCategoryId, category.getId());
        }

        if (StringUtils.hasText(keyword)) {
            String likeKeyword = keyword.trim();
            wrapper.and(w -> w.like(Article::getTitle, likeKeyword)
                    .or()
                    .like(Article::getSummary, likeKeyword));
        }

        if (StringUtils.hasText(tagName)) {
            // 标签名来自前台，用 {0} 占位符交给 MyBatis 绑定参数，不拼 SQL
            wrapper.apply("id IN (SELECT atg.article_id FROM article_tag atg "
                    + "JOIN tag t ON t.id = atg.tag_id WHERE t.name = {0})", tagName.trim());
        }

        Page<Article> articlePage = articleMapper.selectPage(new Page<>(page, size), wrapper);
        return articleVOAssembler.toVoPage(articlePage);
    }

    @Override
    public ArticlePublicDetailVO getPublishedDetail(Long id) {
        if (id == null) {
            throw new BusinessException("文章ID不能为空");
        }

        Article article = articleMapper.selectById(id);
        // 草稿和未发布的文章对前台统一表现为"不存在"，避免从错误信息里猜到草稿ID。
        if (article == null || !STATUS_PUBLISHED.equals(article.getStatus())) {
            throw new BusinessException(404, "文章不存在");
        }

        ArticleCategory category = articleCategoryMapper.selectById(article.getCategoryId());
        List<String> tags = articleTagMapper.selectTagNamesByArticleId(article.getId());
        return new ArticlePublicDetailVO(article, category, tags);
    }
}
