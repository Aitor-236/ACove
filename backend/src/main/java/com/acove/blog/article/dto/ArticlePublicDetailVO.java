package com.acove.blog.article.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.acove.blog.article.entity.Article;
import com.acove.blog.article.entity.ArticleCategory;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 前台文章详情页的返回内容，对应 GET /article/detail/{id}。
 * <p>
 * 和后台编辑页用的 ArticleDetailVO 区别：
 * <ul>
 *   <li>只对已发布文章开放，因此不返回 status 等后台字段；</li>
 *   <li>正文原文放在 content 里，由前端渲染 Markdown；</li>
 *   <li>额外带上阅读时长、标签，供详情页头部展示。</li>
 * </ul>
 */
@Data
@NoArgsConstructor
public class ArticlePublicDetailVO {

    private Long id;

    private String title;

    private String summary;

    /** Markdown 正文原文，前端负责渲染 */
    private String content;

    /** 分类ID，前端可用它跳回该分类的文章列表 */
    private Long categoryId;

    private String categoryName;

    /** 分类英文标识，筛选文章列表时用的就是它 */
    private String categorySlug;

    /** 标签名称列表，没有标签时为空列表而不是 null */
    private List<String> tags;

    /** 预计阅读时长（分钟） */
    private Integer readingMinutes;

    /** 发布时间 */
    private LocalDateTime publishedAt;

    /** 最后更新时间 */
    private LocalDateTime updatedAt;

    /**
     * 由文章实体构建详情展示对象。
     *
     * @param article  文章实体，不能为空
     * @param category 文章分类，分类被删除时允许为 null
     * @param tags     标签名称，允许为 null，统一转成空列表
     */
    public ArticlePublicDetailVO(Article article, ArticleCategory category, List<String> tags) {
        this.id = article.getId();
        this.title = article.getTitle();
        this.summary = article.getSummary();
        this.content = article.getContentMarkdown();
        this.categoryId = article.getCategoryId();
        this.categoryName = category == null ? null : category.getName();
        this.categorySlug = category == null ? null : category.getSlug();
        this.tags = tags == null ? List.of() : List.copyOf(tags);
        this.readingMinutes = article.getReadingMinutes();
        this.publishedAt = article.getPublishedAt();
        this.updatedAt = article.getUpdatedAt();
    }
}
