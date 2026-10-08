package com.acove.blog.article.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import lombok.Data;

@Data
public class CategoryDTO {

    /** 分类ID，新增时为空，修改时必填 */
    @JsonAlias("categoryId")
    private Long id;

    @JsonAlias("name")
    private String categoryName;

    @JsonAlias("slug")
    private String categoryIdentifier;
}
