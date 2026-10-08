package com.acove.blog.article.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import lombok.Data;

/**
 * 标签入参：新增只传标签名，修改时需要同时传 id。
 */
@Data
public class TagDTO {

    /** 标签ID，新增时为空，修改时必填 */
    private Long id;

    /** 标签名称，同名标签在表中唯一 */
    @JsonAlias("tagName")
    private String name;
}
