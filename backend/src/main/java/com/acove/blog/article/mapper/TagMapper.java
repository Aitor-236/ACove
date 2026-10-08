package com.acove.blog.article.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.acove.blog.article.entity.Tag;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface TagMapper extends BaseMapper<Tag> {

    /**
     * 前台标签面板用：统计每个标签下"已发布"的文章数。
     * 只返回至少有一篇已发布文章的标签，草稿不计入。
     */
    @Select("SELECT t.id AS tag_id, t.name AS name, COUNT(a.id) AS article_count "
            + "FROM tag t "
            + "JOIN article_tag atg ON atg.tag_id = t.id "
            + "JOIN article a ON a.id = atg.article_id "
            + "WHERE a.status = 'published' "
            + "GROUP BY t.id, t.name "
            + "ORDER BY article_count DESC, t.id")
    List<Map<String, Object>> selectPublishedTagCounts();
}
