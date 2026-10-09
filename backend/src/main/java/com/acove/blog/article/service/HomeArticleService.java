package com.acove.blog.article.service;

import java.util.List;

import com.acove.blog.article.dto.ArticleVO;
import com.acove.blog.article.dto.HomeArticleVO;

/**
 * 首页展示文章：后台挑选 + 排序，前台按这个顺序展示。
 */
public interface HomeArticleService {

    /**
     * 前台首页要展示的文章：已发布且被后台选中的按保存顺序返回；
     * 一篇都没选中（或选中的都没发布）时回退到最近三篇已发布文章。
     *
     * @return 首页文章卡片 + 已发布文章总数
     */
    HomeArticleVO listHomeArticles();

    /**
     * 后台「首页展示」页的已选列表：顺序即首页顺序，包含当前是草稿的文章
     * （草稿不会显示在首页，但选择要保留，重新发布会自动回到原位置）。
     *
     * @return 已选文章卡片，带 status
     */
    List<ArticleVO> listSelectedArticles();

    /**
     * 整体覆盖首页展示选择，数组顺序就是首页展示顺序。
     *
     * @param articleIds 文章ID，空数组表示清空；null 视为参数不合法
     * @throws com.acove.blog.common.exception.BusinessException 参数缺失或文章不存在时抛出 400
     */
    void saveHomeArticles(List<Long> articleIds);
}
