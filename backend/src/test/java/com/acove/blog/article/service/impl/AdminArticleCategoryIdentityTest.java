package com.acove.blog.article.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.acove.blog.article.dto.ArticleDTO;
import com.acove.blog.article.dto.ArticleVO;
import com.acove.blog.article.entity.Article;
import com.acove.blog.article.entity.ArticleCategory;
import com.acove.blog.article.mapper.AdminArticleMapper;
import com.acove.blog.article.mapper.ArticleCategoryMapper;
import com.acove.blog.article.mapper.ArticleTagMapper;
import com.acove.blog.article.mapper.TagMapper;
import com.acove.blog.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

class AdminArticleCategoryIdentityTest {
    private AdminArticleMapper articles;
    private ArticleCategoryMapper categories;
    private ArticleTagMapper tags;
    private AdminArticleServiceImpl service;
    private ArticleVOAssembler assembler;
    private Article stored;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), Article.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), ArticleCategory.class);
        articles = mock(AdminArticleMapper.class);
        categories = mock(ArticleCategoryMapper.class);
        tags = mock(ArticleTagMapper.class);
        assembler = new ArticleVOAssembler(categories, tags);
        service = new AdminArticleServiceImpl(articles, categories, tags, mock(TagMapper.class), assembler);
        stored = article(1L, 10L);
        when(categories.selectById(10L)).thenReturn(category(10L, "Same", "first"));
        when(categories.selectById(20L)).thenReturn(category(20L, "Same", "second"));
        when(articles.selectById(anyLong())).thenAnswer(invocation -> stored);
        when(articles.insert(any(Article.class))).thenAnswer(invocation -> {
            stored = invocation.getArgument(0); stored.setId(1L); return 1;
        });
        when(articles.updateById(any(Article.class))).thenAnswer(invocation -> {
            Article update = invocation.getArgument(0);
            if (update.getCategoryId() != null) stored.setCategoryId(update.getCategoryId());
            return 1;
        });
    }

    @Test
    void createUsesIdEvenWhenNameConflicts() {
        ArticleDTO input = input(20L, "another name");
        ArticleVO result = service.createArticle(input, 3L);
        assertEquals(20L, stored.getCategoryId());
        assertEquals(20L, result.getCategoryId());
        assertEquals("second", result.getCategorySlug());
        verify(categories, never()).selectList(any());
    }

    @Test
    void updateCanSwitchBetweenIdenticallyNamedCategories() {
        ArticleDTO input = input(20L, "Same"); input.setId(1L);
        ArticleVO result = service.updateArticle(input);
        assertEquals(20L, stored.getCategoryId());
        assertEquals("Same", result.getCategoryName());
        assertEquals("second", result.getCategorySlug());
    }

    @Test
    void duplicateLegacyNamesAreRejectedForCreateAndUpdateBeforeSlugFallback() {
        when(categories.selectList(any())).thenReturn(List.of(
                category(10L, "Same", "first"), category(20L, "Same", "Same")));
        ArticleDTO input = input(null, "Same");
        assertThrows(BusinessException.class, () -> service.createArticle(input, 3L));
        input.setId(1L);
        assertThrows(BusinessException.class, () -> service.updateArticle(input));
        verify(categories, never()).selectOne(any());
        verify(articles, never()).insert(any(Article.class));
        verify(articles, never()).updateById(any(Article.class));
    }

    @Test
    void uniqueLegacyNameAndSlugRequestsRemainSupported() {
        when(categories.selectList(any())).thenReturn(List.of(category(20L, "Same", "second")));
        assertEquals(20L, service.createArticle(input(null, " Same "), 3L).getCategoryId());
        when(categories.selectList(any())).thenReturn(List.of());
        when(categories.selectOne(any())).thenReturn(category(10L, "Same", "first"));
        assertEquals(10L, service.createArticle(input(null, "first"), 3L).getCategoryId());
    }

    @Test
    void invalidIdsAreRejectedWithoutLegacyNameFallback() {
        for (Long id : List.of(0L, -1L, 999L)) {
            ArticleDTO input = input(id, "Same");
            assertThrows(BusinessException.class, () -> service.createArticle(input, 3L));
            input.setId(1L);
            assertThrows(BusinessException.class, () -> service.updateArticle(input));
        }
        verify(categories, never()).selectList(any());
        verify(articles, never()).insert(any(Article.class));
        verify(articles, never()).updateById(any(Article.class));
    }

    @Test
    void partialTextUpdateKeepsExistingCategory() {
        ArticleDTO input = input(null, null); input.setId(1L);
        ArticleVO result = service.updateArticle(input);
        ArgumentCaptor<Article> update = ArgumentCaptor.forClass(Article.class);
        verify(articles).updateById(update.capture());
        assertNull(update.getValue().getCategoryId());
        assertEquals(10L, result.getCategoryId());
        assertEquals("first", result.getCategorySlug());
        verify(categories, never()).selectList(any());
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void listFilterPrefersExactSlugOverAnotherCategoryName() {
        when(categories.selectOne(any())).thenReturn(category(20L, "Same", "second"));
        when(articles.selectPage(any(Page.class), any())).thenReturn(new Page<>(1, 10));
        service.listAll(1, 10, "second", null, null);
        ArgumentCaptor<Wrapper<Article>> filter = ArgumentCaptor.forClass((Class) Wrapper.class);
        verify(articles).selectPage(any(Page.class), filter.capture());
        assertTrue(filter.getValue().getSqlSegment().contains("category_id"));
        assertTrue(((com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Article>) filter.getValue())
                .getParamNameValuePairs().containsValue(20L));
        verify(categories, never()).selectList(any());
    }

    @Test
    void listsPreserveIdsAndSlugsForIdenticallyNamedCategories() {
        when(categories.selectByIds(anyCollection())).thenReturn(List.of(
                category(10L, "Same", "first"), category(20L, "Same", "second")));
        List<ArticleVO> result = assembler.toVoList(List.of(article(1L, 10L), article(2L, 20L)));
        assertEquals(List.of(10L, 20L), result.stream().map(ArticleVO::getCategoryId).toList());
        assertEquals(List.of("first", "second"), result.stream().map(ArticleVO::getCategorySlug).toList());
        assertEquals(List.of("Same", "Same"), result.stream().map(ArticleVO::getCategoryName).toList());
    }

    private static ArticleDTO input(Long categoryId, String categoryName) {
        ArticleDTO input = new ArticleDTO(); input.setTitle("Title");
        input.setCategoryId(categoryId); input.setCategoryName(categoryName);
        return input;
    }

    private static Article article(Long id, Long categoryId) {
        Article article = new Article(); article.setId(id); article.setCategoryId(categoryId);
        return article;
    }

    private static ArticleCategory category(Long id, String name, String slug) {
        ArticleCategory category = new ArticleCategory(); category.setId(id); category.setName(name); category.setSlug(slug);
        return category;
    }
}
