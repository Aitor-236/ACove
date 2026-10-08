package com.acove.blog.article.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 文章分类表实体。
 */
@Data
@TableName("article_category")
public class ArticleCategory {

    /** 分类ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类名称 */
    private String name;

    /** 分类英文标识，用于稳定筛选 */
    private String slug;

    /** 排序值，越小越靠前 */
    private Integer sortOrder;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
