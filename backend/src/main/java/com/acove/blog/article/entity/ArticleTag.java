package com.acove.blog.article.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 文章与标签关联表实体。
 * <p>
 * 表主键为 (article_id, tag_id) 复合主键，MyBatis-Plus 基于单主键的
 * selectById/deleteById/updateById 等 BaseMapper 方法不适用于该表，
 * 关联数据应通过自定义 Mapper SQL 或 QueryWrapper 的字段条件操作。
 */
@Data
@TableName("article_tag")
public class ArticleTag {

    /** 文章ID，关联 article.id */
    private Long articleId;

    /** 标签ID，关联 tag.id */
    private Long tagId;
}
