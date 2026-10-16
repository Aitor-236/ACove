package com.acove.blog.gallery.dto;

import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GalleryDetailVO extends GalleryAlbumVO {

    private List<GalleryImageVO> images;
}
