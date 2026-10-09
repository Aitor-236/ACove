package com.acove.blog.article.dto;

import java.util.List;

import lombok.Data;

/**
 * 首页展示文章的整理入参：整体覆盖，数组顺序就是首页展示顺序。
 */
@Data
public class HomeArticleDTO {

    /** 首页展示的文章ID，按展示顺序排列；空数组表示清空（首页回退到最近三篇） */
    private List<Long> articleIds;
}
