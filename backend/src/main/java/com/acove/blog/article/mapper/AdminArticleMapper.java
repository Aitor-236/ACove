package com.acove.blog.article.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.acove.blog.article.entity.Article;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper 
public interface AdminArticleMapper extends BaseMapper<Article> {
    
}
