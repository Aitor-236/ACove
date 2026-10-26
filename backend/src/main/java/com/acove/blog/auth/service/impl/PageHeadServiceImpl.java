package com.acove.blog.auth.service.impl;

import java.net.URI;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import org.springframework.util.StringUtils;

import com.acove.blog.article.entity.Article;
import com.acove.blog.article.mapper.ArticleMapper;
import com.acove.blog.auth.dto.SiteSettingVO;
import com.acove.blog.auth.service.PageHeadService;
import com.acove.blog.auth.service.SiteSettingService;
import com.acove.blog.gallery.dto.GalleryAlbumVO;
import com.acove.blog.gallery.mapper.GalleryAlbumMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PageHeadServiceImpl implements PageHeadService {
    private static final Set<String> PUBLIC_PAGES = Set.of("/", "/articles", "/gallery", "/projects", "/about");
    private static final Pattern DETAIL = Pattern.compile("^/(articles|gallery)/([1-9][0-9]*)/?$");
    private final SiteSettingService siteSettingService;
    private final ArticleMapper articleMapper;
    private final GalleryAlbumMapper galleryAlbumMapper;

    @Override
    public String render(String requestUri, String scheme, String host) {
        SiteSettingVO site = siteSettingService.getSettings();
        String title = site.getSiteName();
        String description = text(site.getHeroText());
        String image = site.getHeroImage();
        String type = "website";
        String path = text(requestUri).split("\\?", 2)[0];
        boolean noindex = !PUBLIC_PAGES.contains(path);
        var detail = DETAIL.matcher(path);
        if (detail.matches()) {
            Long id = positiveId(detail.group(2));
            if (id != null && "articles".equals(detail.group(1))) {
                // Do not load draft title, summary or body into this public response.
                Article article = articleMapper.selectOne(new QueryWrapper<Article>()
                        .select("id", "title", "summary", "status")
                        .eq("id", id).eq("status", "published"));
                if (article != null && "published".equals(article.getStatus())) {
                    title = article.getTitle() + " | " + site.getSiteName();
                    description = text(article.getSummary());
                    type = "article";
                    noindex = false;
                    path = "/articles/" + id;
                }
            } else if (id != null) {
                GalleryAlbumVO gallery = galleryAlbumMapper.selectGallerySummary(id);
                if (gallery != null) {
                    title = gallery.getName() + " | " + site.getSiteName();
                    description = StringUtils.hasText(gallery.getDescription())
                            ? gallery.getDescription() : description;
                    image = StringUtils.hasText(gallery.getCoverImage()) ? gallery.getCoverImage() : image;
                    noindex = false;
                    path = "/gallery/" + id;
                }
            }
        }
        String origin = origin(scheme, host);
        StringBuilder head = new StringBuilder("<title>").append(escape(title)).append("</title>\n");
        meta(head, "name", "description", description);
        meta(head, "name", "robots", noindex ? "noindex, nofollow" : "index, follow");
        meta(head, "property", "og:site_name", site.getSiteName());
        meta(head, "property", "og:title", title);
        meta(head, "property", "og:description", description);
        meta(head, "property", "og:type", type);
        meta(head, "name", "twitter:card", "summary_large_image");
        meta(head, "name", "twitter:title", title);
        meta(head, "name", "twitter:description", description);
        // Invalid or private routes have no canonical URL pointing search engines at them.
        if (!noindex && !origin.isEmpty()) {
            String canonical = origin + path;
            head.append("<link rel=\"canonical\" href=\"").append(escape(canonical)).append("\">\n");
            meta(head, "property", "og:url", canonical);
        }
        String imageUrl = imageUrl(origin, image);
        if (!noindex && !imageUrl.isEmpty()) {
            meta(head, "property", "og:image", imageUrl);
            meta(head, "name", "twitter:image", imageUrl);
        }
        return head.toString();
    }

    private Long positiveId(String value) {
        try { return Long.valueOf(value); }
        catch (NumberFormatException ignored) { return null; }
    }

    /** Only accept the scheme and authority overwritten by our Nginx location. */
    private String origin(String scheme, String host) {
        if (!("http".equals(scheme) || "https".equals(scheme)) || host == null
                || !host.matches("[A-Za-z0-9.\\[\\]:-]+")) return "";
        try {
            URI uri = URI.create(scheme + "://" + host);
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getPort() > 65535) return "";
            return scheme + "://" + host;
        } catch (IllegalArgumentException ignored) { return ""; }
    }

    private String imageUrl(String origin, String image) {
        // Uploaded paths are served behind /api in both dev and production.
        if (origin.isEmpty() || image == null || !image.startsWith("/uploads/")) return "";
        try { return origin + "/api" + new URI(null, null, image, null).toASCIIString(); }
        catch (java.net.URISyntaxException ignored) { return ""; }
    }

    private void meta(StringBuilder head, String attribute, String name, String content) {
        head.append("<meta ").append(attribute).append("=\"").append(name)
                .append("\" content=\"").append(escape(content)).append("\">\n");
    }
    private String text(String value) { return value == null ? "" : value; }
    private String escape(String value) { return HtmlUtils.htmlEscape(text(value), "UTF-8"); }
}
