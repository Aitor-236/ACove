package com.acove.blog.gallery.service;

import java.util.List;

import com.acove.blog.gallery.dto.GalleryAlbumVO;
import com.acove.blog.gallery.dto.GalleryDetailVO;

public interface GalleryService {

    List<GalleryAlbumVO> listGalleries();

    GalleryDetailVO getGalleryDetail(Long id);

    /** 后台写入成功后读取摘要，不对外单独暴露路由。 */
    GalleryAlbumVO getGallerySummary(Long id);
}
