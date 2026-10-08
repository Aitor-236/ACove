package com.acove.blog.article.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 文章主表实体。
 */
@Data
@TableName("article")
public class Article {

    /** 文章ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 作者ID，关联 sys_user.id */
    private Long authorId;

    /** 主分类ID，关联 article_category.id */
    private Long categoryId;

    /** 文章标题 */
    private String title;

    /** 文章摘要，用于列表卡片展示 */
    private String summary;

    /** Markdown 正文原文 */
    private String contentMarkdown;

    /** 预计阅读时长（分钟） */
    private Integer readingMinutes;

    /** 状态：draft-草稿，published-已发布 */
    private String status;

    /** 发布时间，发布时写入 */
    private LocalDateTime publishedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
