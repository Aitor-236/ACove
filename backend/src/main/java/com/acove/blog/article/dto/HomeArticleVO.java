package com.acove.blog.article.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 前台首页动态列表：文章卡片和左栏「文章」条数一次请求拿全。
 */
@Data
public class HomeArticleVO {

    /** 首页要展示的文章卡片，已按首页顺序排好 */
    private List<ArticleVO> records = new ArrayList<>();

    /** 已发布文章总数，首页左栏的「文章」统计用它 */
    private long total;

    public HomeArticleVO() {
    }

    public HomeArticleVO(List<ArticleVO> records, long total) {
        this.records = records;
        this.total = total;
    }
}
