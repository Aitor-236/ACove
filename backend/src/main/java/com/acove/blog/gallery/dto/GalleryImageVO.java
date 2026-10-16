package com.acove.blog.gallery.dto;

import com.acove.blog.gallery.entity.GalleryImage;

import lombok.Data;

@Data
public class GalleryImageVO {

    private Long id;

    private String url;

    private Integer sortOrder;

    public static GalleryImageVO from(GalleryImage image) {
        GalleryImageVO vo = new GalleryImageVO();
        vo.setId(image.getId());
        vo.setUrl(image.getImageUrl());
        vo.setSortOrder(image.getSortOrder());
        return vo;
    }
}
