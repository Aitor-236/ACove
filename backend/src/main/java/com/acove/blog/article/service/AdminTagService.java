package com.acove.blog.article.service;

import com.acove.blog.article.dto.TagVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 后台标签管理。service 层只返回业务对象或抛业务异常，
 * 统一由 controller 包装成 Result 返回前端。
 */
public interface AdminTagService {

    /** 标签分页列表，keyword 按名称模糊匹配，每条附带被引用的文章数。 */
    Page<TagVO> listTags(long page, long size, String keyword);

    /** 新建标签，名称必填、不能超过列长度、不能与已有标签重名。 */
    TagVO createTag(String tagName);

    /** 重命名标签，标签不存在抛 404，重名抛 400。 */
    TagVO updateTag(Long tagId, String tagName);

    /** 删除标签，标签不存在抛 404；文章上的关联由级联外键一并清理。 */
    void deleteTag(Long tagId);
}
