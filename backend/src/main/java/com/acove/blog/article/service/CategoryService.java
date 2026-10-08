package com.acove.blog.article.service;

import java.util.List;

import com.acove.blog.article.dto.CategoryVO;

public interface CategoryService {

    /**
     * 返回全部分类及各自的已发布文章数，按 sortOrder 升序。
     */
    List<CategoryVO> listWithArticleCounts();
}
