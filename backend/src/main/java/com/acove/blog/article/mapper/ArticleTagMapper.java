package com.acove.blog.article.mapper;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.acove.blog.article.entity.ArticleTag;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface ArticleTagMapper extends BaseMapper<ArticleTag> {

    /**
     * 查询一篇文章的标签名称，按标签ID升序。
     * <p>
     * article_tag 是复合主键表，BaseMapper 的单主键方法用不了，
     * 这里用一条 join 把标签名直接取出来，省掉两次查询。
     */
    @Select("SELECT t.name FROM tag t "
            + "JOIN article_tag atg ON atg.tag_id = t.id "
            + "WHERE atg.article_id = #{articleId} "
            + "ORDER BY t.id")
    List<String> selectTagNamesByArticleId(@Param("articleId") Long articleId);

    /**
     * 批量查询一批文章的标签名，供列表卡片一次补齐标签，避免按文章逐条查。
     * 文章ID集合不能为空，调用前先判空。
     */
    @Select("<script>"
            + "SELECT atg.article_id AS article_id, t.name AS name FROM tag t "
            + "JOIN article_tag atg ON atg.tag_id = t.id "
            + "WHERE atg.article_id IN "
            + "<foreach collection='articleIds' item='articleId' open='(' separator=',' close=')'>#{articleId}</foreach> "
            + "ORDER BY t.id"
            + "</script>")
    List<Map<String, Object>> selectTagNamesByArticleIds(@Param("articleIds") Collection<Long> articleIds);

    /**
     * 统计一批标签分别被多少篇文章引用（草稿和已发布都算），
     * 供后台标签列表展示使用量。标签ID集合不能为空，调用前先判空。
     */
    @Select("<script>"
            + "SELECT tag_id AS tag_id, COUNT(*) AS article_count FROM article_tag "
            + "WHERE tag_id IN "
            + "<foreach collection='tagIds' item='tagId' open='(' separator=',' close=')'>#{tagId}</foreach> "
            + "GROUP BY tag_id"
            + "</script>")
    List<Map<String, Object>> countArticlesByTagIds(@Param("tagIds") Collection<Long> tagIds);
}
