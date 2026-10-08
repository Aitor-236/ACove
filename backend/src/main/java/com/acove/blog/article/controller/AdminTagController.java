package com.acove.blog.article.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.acove.blog.article.dto.TagDTO;
import com.acove.blog.article.dto.TagVO;
import com.acove.blog.article.service.AdminTagService;
import com.acove.blog.common.result.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;

/**
 * 后台标签管理接口。controller 只负责参数绑定和 Result 包装，
 * 参数校验和业务异常都在 service 层处理，由 GlobalExceptionHandler 统一返回。
 */
@RestController
@RequestMapping("/admin/tag")
@RequiredArgsConstructor
public class AdminTagController {

    private final AdminTagService adminTagService;

    /**
     * 标签分页列表，keyword 可选、按标签名模糊匹配，每条附带被引用的文章数。
     */
    @GetMapping("/list")
    public Result<Page<TagVO>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword) {
        return Result.success(adminTagService.listTags(page, size, keyword));
    }

    /**
     * 新建标签，名称必填且不能与已有标签重名，重名返回 400。
     */
    @PostMapping("/create")
    public Result<TagVO> create(@RequestBody TagDTO tagDTO) {
        return Result.success(adminTagService.createTag(tagDTO.getName()));
    }

    /**
     * 重命名标签：id 放在请求体里传，标签不存在返回 404，重名返回 400。
     */
    @PostMapping("/update")
    public Result<TagVO> update(@RequestBody TagDTO tagDTO) {
        return Result.success(adminTagService.updateTag(tagDTO.getId(), tagDTO.getName()));
    }

    /**
     * 删除标签：被文章引用的标签也能删，关联记录由外键级联清理。
     */
    @PostMapping("/delete")
    public Result<Void> delete(@RequestParam Long id) {
        adminTagService.deleteTag(id);
        return Result.success();
    }
}
