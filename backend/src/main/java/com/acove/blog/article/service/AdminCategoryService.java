package com.acove.blog.article.service;

import java.util.List;

import com.acove.blog.article.dto.CategoryVO;

/**
 * 后台分类管理。service 层只返回业务对象或抛业务异常，
 * 统一由 controller 包装成 Result 返回前端。
 */
public interface AdminCategoryService {

    /**
     * 后台分类列表：返回所有分类，articleCount 统计的是全部文章（草稿+已发布），
     * 与删除校验口径一致；公开的 /category/list 只统计已发布文章，两者不要混用。
     */
    List<CategoryVO> listCategories();

    /** 新建分类，返回创建后的分类。 */
    CategoryVO createCategory(String categoryName, String categoryIdentifier);

    /** 修改分类名称/英文标识，留空表示不修改该项。 */
    CategoryVO updateCategory(Long categoryId, String categoryName, String categoryIdentifier);

    /** 删除分类，分类下仍有文章时抛业务异常。 */
    void deleteCategory(Long categoryId);
}
