package com.acove.blog.article.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.acove.blog.article.entity.Article;
import com.acove.blog.article.entity.ArticleCategory;

import lombok.Data;

@Data
public class ArticleVO {
    private Long id;
    private String title;
    private String summary;
    private Long categoryId;
    private String categoryName;
    private String categorySlug;
    private String status;
    private LocalDateTime publishedAt;
    /** 最后更新时间：前台列表卡片和详情页统一显示这个时间 */
    private LocalDateTime updatedAt;
    private Integer readingMinutes;

    /** 标签名列表，列表卡片上展示；没有标签时是空列表 */
    private List<String> tags = new ArrayList<>();

    public ArticleVO() {
    }

    public ArticleVO(Article article) {
        this.id = article.getId();
        this.title = article.getTitle();
        this.summary = article.getSummary();
        this.categoryId = article.getCategoryId();
        this.status = article.getStatus();
        this.publishedAt = article.getPublishedAt();
        this.updatedAt = article.getUpdatedAt();
        this.readingMinutes = article.getReadingMinutes();
    }

    /**
     * 由文章实体构建展示对象，关联分类同时补齐名称和稳定的筛选标识。
     */
    public static ArticleVO from(Article article, ArticleCategory category) {
        ArticleVO vo = new ArticleVO(article);
        if (category != null) {
            vo.setCategoryName(category.getName());
            vo.setCategorySlug(category.getSlug());
        }
        return vo;
    }
}
