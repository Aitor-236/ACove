package com.acove.blog.gallery.dto;

import lombok.Data;

/** 新建与局部更新图库使用；更新时 null 表示不修改。 */
@Data
public class GalleryDTO {

    private Long id;

    private String name;

    private String description;
}
