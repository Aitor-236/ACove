package com.acove.blog.article.dto;

/**
 * 删除文章的结果，用于让前端区分两种"删除"。
 */
public enum ArticleDeleteResult {

    /** 已发布的文章被取消发布，回退为草稿，数据仍在。 */
    UNPUBLISHED,

    /** 草稿文章被物理删除，数据已不存在。 */
    DELETED
}
