package com.acove.blog.article.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acove.blog.article.dto.CategoryDTO;
import com.acove.blog.article.dto.CategoryVO;
import com.acove.blog.article.service.AdminCategoryService;
import com.acove.blog.common.result.Result;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 后台分类管理接口。controller 只负责参数绑定和 Result 包装，
 * 参数校验和业务异常都在 service 层处理，由 GlobalExceptionHandler 统一返回。
 */
@RestController 
@RequestMapping ("/admin/category")
@RequiredArgsConstructor 
public class AdminCategoryController {

    private final AdminCategoryService adminCategoryService;

    /**
     * 后台分类列表，按排序值升序；articleCount 统计全部文章（含草稿），
     * 与删除校验同一口径，前端据此判断能否删除。
     */
    @GetMapping("/list")
    public Result<List<CategoryVO>> list() {
        return Result.success(adminCategoryService.listCategories());
    }

    /**
     * 新建分类，英文标识（slug）在表中唯一，重复会返回 400。
     */
    @PostMapping("/create")
    public Result<CategoryVO> create(@RequestBody CategoryDTO categoryDTO) {
        return Result.success(adminCategoryService.createCategory(
                categoryDTO.getCategoryName(), categoryDTO.getCategoryIdentifier()));
    }

    /**
     * 修改分类：只更新请求体里带了值的字段，留空表示该项不变，id 放在请求体里传。
     */
    @PostMapping("/update")
    public Result<CategoryVO> update(@RequestBody CategoryDTO categoryDTO) {
        return Result.success(adminCategoryService.updateCategory(
                categoryDTO.getId(),
                categoryDTO.getCategoryName(),
                categoryDTO.getCategoryIdentifier()));
    }

    /**
     * 删除分类：分类下还有文章时返回 400，需要先把那些文章改到别的分类。
     */
    @PostMapping("/delete")
    public Result<Void> delete(@RequestParam Long id) {
        adminCategoryService.deleteCategory(id);
        return Result.success();
    }
}
