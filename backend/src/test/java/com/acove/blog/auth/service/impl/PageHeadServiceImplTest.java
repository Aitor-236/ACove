package com.acove.blog.auth.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.acove.blog.article.entity.Article;
import com.acove.blog.article.mapper.ArticleMapper;
import com.acove.blog.auth.dto.SiteSettingVO;
import com.acove.blog.auth.service.SiteSettingService;
import com.acove.blog.gallery.dto.GalleryAlbumVO;
import com.acove.blog.gallery.mapper.GalleryAlbumMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

class PageHeadServiceImplTest {
    private final SiteSettingService settings = mock(SiteSettingService.class);
    private final ArticleMapper articles = mock(ArticleMapper.class);
    private final GalleryAlbumMapper galleries = mock(GalleryAlbumMapper.class);
    private final PageHeadServiceImpl service = new PageHeadServiceImpl(settings, articles, galleries);

    @BeforeEach
    void setup() {
        when(settings.getSettings()).thenReturn(new SiteSettingVO(
                "自定义小站", "/uploads/hero/share.jpg", "记录日常", "", "", false));
    }

    @Test
    void homepageIncludesCustomNameDescriptionAndAbsoluteUrlsWithoutJavaScript() {
        String html = service.render("/?source=test", "https", "blog.example:8443");
        assertTrue(html.contains("<title>自定义小站</title>"));
        assertTrue(html.contains("name=\"description\" content=\"记录日常\""));
        assertTrue(html.contains("property=\"og:site_name\" content=\"自定义小站\""));
        assertTrue(html.contains("rel=\"canonical\" href=\"https://blog.example:8443/\""));
        assertTrue(html.contains("property=\"og:image\" content=\"https://blog.example:8443/api/uploads/hero/share.jpg\""));
        verifyNoInteractions(articles, galleries);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void publishedArticleUsesPublicTitleAndSummaryAndFiltersDraftsInQuery() {
        Article article = new Article();
        article.setTitle("一篇文章"); article.setSummary("公开摘要"); article.setStatus("published");
        when(articles.selectOne(any())).thenReturn(article);
        String html = service.render("/articles/42?tag=%26test", "http", "127.0.0.1:8088");
        assertTrue(html.contains("<title>一篇文章 | 自定义小站</title>"));
        assertTrue(html.contains("property=\"og:type\" content=\"article\""));
        assertTrue(html.contains("property=\"og:description\" content=\"公开摘要\""));
        assertTrue(html.contains("href=\"http://127.0.0.1:8088/articles/42\""));
        ArgumentCaptor<QueryWrapper> query = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(articles).selectOne(query.capture());
        assertTrue(query.getValue().getSqlSegment().contains("status"));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue("published"));
    }

    @Test
    void galleryUsesItsOwnCoverAndCanonicalWithoutQuery() {
        GalleryAlbumVO gallery = new GalleryAlbumVO();
        gallery.setName("旅行图库"); gallery.setDescription("旅行照片");
        gallery.setCoverImage("/uploads/gallery/cover.webp");
        when(galleries.selectGallerySummary(7L)).thenReturn(gallery);
        String html = service.render("/gallery/7/?from=home", "https", "example.com");
        assertTrue(html.contains("<title>旅行图库 | 自定义小站</title>"));
        assertTrue(html.contains("content=\"旅行照片\""));
        assertTrue(html.contains("content=\"https://example.com/api/uploads/gallery/cover.webp\""));
        assertTrue(html.contains("href=\"https://example.com/gallery/7\""));
    }

    @Test
    void missingDraftMalformedAndOverflowIdsRemainPrivateAndNoindex() {
        Article draft = new Article();
        draft.setTitle("秘密标题"); draft.setSummary("秘密摘要"); draft.setStatus("draft");
        when(articles.selectOne(any())).thenReturn(draft);
        for (String uri : new String[]{"/articles/9", "/gallery/9", "/articles/0", "/articles/abc",
                "/articles/9999999999999999999999999999", "/login", "/admin/articles", "/missing"}) {
            String html = service.render(uri, "https", "example.com");
            assertTrue(html.contains("content=\"noindex, nofollow\""), uri);
            assertFalse(html.contains("秘密"), uri);
            assertFalse(html.contains("rel=\"canonical\""), uri);
            assertFalse(html.contains("og:image"), uri);
        }
    }

    @Test
    void escapesEveryHtmlContextAndRejectsOriginInjection() {
        when(settings.getSettings()).thenReturn(new SiteSettingVO(
                "</title><script>alert(1)</script>\"&'", "", "\"><img src=x onerror=alert(1)>", "", "", false));
        String html = service.render("/", "https", "example.com\"><script>");
        assertFalse(html.contains("<script>"));
        assertFalse(html.contains("<img"));
        assertTrue(html.contains("&lt;/title&gt;"));
        assertTrue(html.contains("&quot;"));
        assertTrue(html.contains("&amp;"));
        assertFalse(html.contains("canonical"));
        assertFalse(html.contains("og:url"));
    }

    @Test
    void invalidSchemeOrAuthorityNeverProducesAnAbsoluteLink() {
        for (String host : new String[]{"user@example.com", "example.com/path", "example.com:99999", "", "example.com?x"}) {
            assertFalse(service.render("/", "https", host).contains("canonical"), host);
        }
        assertFalse(service.render("/", "javascript", "example.com").contains("canonical"));
        assertTrue(service.render("/", "http", "[::1]:8090").contains("http://[::1]:8090/"));
    }
}
