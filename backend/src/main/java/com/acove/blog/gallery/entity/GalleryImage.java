package com.acove.blog.gallery.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("gallery_image")
public class GalleryImage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long galleryId;

    private String imageUrl;

    private String originalName;

    private Integer sortOrder;

    private LocalDateTime createdAt;
}
