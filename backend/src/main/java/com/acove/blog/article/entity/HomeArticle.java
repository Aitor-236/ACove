package com.acove.blog.article.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 首页展示文章实体：一行 = 首页动态列表里的一条，按 sortOrder 升序展示。
 */
@Data
@TableName("home_article")
public class HomeArticle {

    /** 文章ID，关联 article.id（主键，一篇最多出现一次） */
    @TableId(value = "article_id", type = IdType.INPUT)
    private Long articleId;

    /** 首页展示顺序，越小越靠前 */
    private Integer sortOrder;

    /** 加入首页展示的时间 */
    private LocalDateTime createdAt;
}
