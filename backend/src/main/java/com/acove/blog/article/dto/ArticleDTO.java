package com.acove.blog.article.dto;

import java.util.List;

import lombok.Data;

@Data 
public class ArticleDTO {
    /** 文章ID，新增时为空，更新时必填 */
    private Long id;
    private String title;
    private String summary;
    private String content;
    private String categoryName;

    /**
     * 标签名列表，编辑器里选中的标签整体提交。
     * null 表示本次不动标签（局部更新语义），空列表表示清空这篇文章的标签。
     */
    private List<String> tags;
}
