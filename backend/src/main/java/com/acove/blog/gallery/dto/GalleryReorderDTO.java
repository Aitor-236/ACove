package com.acove.blog.gallery.dto;

import java.util.List;

import lombok.Data;

@Data
public class GalleryReorderDTO {

    private Long galleryId;

    /** 该图库当前图片的完整顺序。 */
    private List<Long> imageIds;
}
