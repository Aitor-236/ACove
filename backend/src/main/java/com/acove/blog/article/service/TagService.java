package com.acove.blog.article.service;

import java.util.List;

import com.acove.blog.article.dto.TagVO;

/** 前台展示用的标签。 */
public interface TagService {

    /**
     * 前台标签面板的数据：只返回至少有一篇已发布文章的标签，
     * `articleCount` 也只统计已发布文章，草稿不计入。
     */
    List<TagVO> listPublishedTags();
}
