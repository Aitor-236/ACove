package com.acove.blog.article.service;

import com.acove.blog.article.dto.ArticleDTO;
import com.acove.blog.article.dto.ArticleDeleteResult;
import com.acove.blog.article.dto.ArticleDetailVO;
import com.acove.blog.article.dto.ArticleVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 后台文章管理。service 层只返回业务对象或抛业务异常，
 * 统一由 controller 包装成 Result 返回前端。
 */
public interface AdminArticleService {

    /**
     * 后台分页列表：包含草稿和已发布，按最近更新时间倒序。
     *
     * @param status 可选的状态筛选，取值 draft / published，其它值视为不筛选
     */
    Page<ArticleVO> listAll(long page, long size, String category, String keyword, String status);

    /**
     * 新建草稿文章，返回创建后的展示对象。
     *
     * @param articleDTO 文章内容，作者由登录态决定，不由前端传入
     * @param authorId   当前登录用户ID，来自 token
     */
    ArticleVO createArticle(ArticleDTO articleDTO, Long authorId);

    /** 局部更新文章，返回更新后的展示对象。 */
    ArticleVO updateArticle(ArticleDTO articleDTO);

    /** 按ID查询文章详情（含正文），供后台编辑页回显。 */
    ArticleDetailVO getArticleDetail(Long id);

    /** 发布文章：写入发布时间并置为已发布，返回发布后的展示对象。 */
    ArticleVO publishArticle(Long id);

    /** 取消发布：把已发布的文章回退为草稿，返回回退后的展示对象。 */
    ArticleVO unpublishArticle(Long id);

    /** 已发布文章取消发布、草稿文章物理删除，返回本次实际动作。 */
    ArticleDeleteResult deleteArticle(Long id);

    /** 物理删除草稿文章：已发布的文章不允许直接删，需要先取消发布。 */
    void forceDeleteArticle(Long id);
}
