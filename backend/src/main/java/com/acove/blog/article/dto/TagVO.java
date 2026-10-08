package com.acove.blog.article.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 后台标签出参：带上被引用的文章数，方便前端提示删除影响面。
 */
@Data
public class TagVO {

    private Long id;
    private String name;

    /** 引用了该标签的文章数，草稿和已发布都算 */
    private Long articleCount;

    private LocalDateTime createdAt;
}
