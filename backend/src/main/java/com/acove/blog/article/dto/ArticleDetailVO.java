package com.acove.blog.article.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.acove.blog.article.entity.Article;
import com.acove.blog.article.entity.ArticleCategory;

import lombok.Data;

/**
 * 后台文章详情，供编辑页回显。
 * <p>
 * 比列表用的 ArticleVO 多出正文和分类标识，字段名（title/summary/content/categoryName）
 * 与 ArticleDTO 对齐，前端拿到后可以直接回填表单再提交修改。
 */
@Data
public class ArticleDetailVO {

    private Long id;
    private String title;
    private String summary;

    /** Markdown 正文，对应 ArticleDTO.content */
    private String content;

    private Long categoryId;
    private String categoryName;
    private String categorySlug;

    /** draft-草稿，published-已发布 */
    private String status;

    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 标签名列表，编辑页回显选中的标签；没有标签时是空列表 */
    private List<String> tags;

    public ArticleDetailVO(Article article, ArticleCategory category, List<String> tags) {
        this.id = article.getId();
        this.title = article.getTitle();
        this.summary = article.getSummary();
        this.content = article.getContentMarkdown();
        this.categoryId = article.getCategoryId();
        this.categoryName = category == null ? null : category.getName();
        this.categorySlug = category == null ? null : category.getSlug();
        this.status = article.getStatus();
        this.publishedAt = article.getPublishedAt();
        this.createdAt = article.getCreatedAt();
        this.updatedAt = article.getUpdatedAt();
        this.tags = tags == null ? List.of() : tags;
    }
}
