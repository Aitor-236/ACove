package com.acove.blog.article.service;

import org.springframework.web.multipart.MultipartFile;

import com.acove.blog.article.dto.UploadImageVO;

/** 后台正文配图上传。 */
public interface AdminUploadService {

    /**
     * 保存一张正文配图，返回可直接写进 Markdown 的相对地址。
     * 只允许 png / jpg / webp / gif，且不超过 5MB。
     */
    UploadImageVO uploadImage(MultipartFile file);
}
