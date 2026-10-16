package com.acove.blog.gallery.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class GalleryAlbumVO {

    private Long id;

    private String name;

    private String description;

    /** 空图库时为 null。 */
    private String coverImage;

    private Long imageCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
