package com.acove.blog.article.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.acove.blog.article.entity.Article;

import lombok.Data;

@Data
public class ArticleVO {
    private Long id;
    private String title;
    private String summary;
    private String categoryName;
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
        this.status = article.getStatus();
        this.publishedAt = article.getPublishedAt();
        this.updatedAt = article.getUpdatedAt();
        this.readingMinutes = article.getReadingMinutes();
    }

    /**
     * 由文章实体构建展示对象，分类名称由调用方补充（通常来自关联查询）。
     */
    public static ArticleVO from(Article article, String categoryName) {
        ArticleVO vo = new ArticleVO(article);
        vo.setCategoryName(categoryName);
        return vo;
    }
}
