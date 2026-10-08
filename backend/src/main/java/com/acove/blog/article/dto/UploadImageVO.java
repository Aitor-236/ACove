package com.acove.blog.article.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 正文配图上传结果，前端拿到 url 后直接写进 Markdown。
 */
@Data
@AllArgsConstructor
public class UploadImageVO {

    /** 图片相对地址，形如 /uploads/article/xxx.png；渲染时前端会加 /api 前缀 */
    private String url;

    /** 原始文件名，用于生成图片的 alt 文案 */
    private String name;

    /** 文件大小（字节） */
    private Long size;
}
