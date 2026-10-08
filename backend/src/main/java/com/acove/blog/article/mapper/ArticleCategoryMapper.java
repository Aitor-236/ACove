package com.acove.blog.article.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.acove.blog.article.entity.ArticleCategory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface ArticleCategoryMapper extends BaseMapper<ArticleCategory> {
}
