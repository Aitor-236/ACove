package com.acove.blog.article.dto;

import lombok.Data;

@Data
public class CategoryVO {
    private Long id;
    private String name;
    private String slug;
    private Long articleCount;
}
