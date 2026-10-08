package com.acove.blog.article.service;

import com.acove.blog.article.dto.ArticleVO;
import com.acove.blog.article.dto.ArticlePublicDetailVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface ArticleService {
    /**
     * 分页查询已发布文章。
     *
     * @param page         当前页码，从 1 开始
     * @param size         每页条数
     * @param categorySlug 分类英文标识，为空表示不筛选
     * @param keyword      标题/摘要关键字，为空表示不筛选
     * @param tagName      标签名称，为空表示不筛选
     * @return 文章卡片分页结果
     */
    Page<ArticleVO> listPublished(long page, long size, String categorySlug, String keyword, String tagName);

    /**
     * 查询单篇已发布文章的详情。
     *
     * @param id 文章ID
     * @return 详情页需要的全部字段
     * @throws com.acove.blog.common.exception.BusinessException 文章不存在或未发布时抛出 404
     */
    ArticlePublicDetailVO getPublishedDetail(Long id);
}
