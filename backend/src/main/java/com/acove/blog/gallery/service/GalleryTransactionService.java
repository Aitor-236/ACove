package com.acove.blog.gallery.service;

import java.util.List;

import com.acove.blog.gallery.dto.GalleryDTO;
import com.acove.blog.gallery.dto.GalleryReorderDTO;
import com.acove.blog.gallery.entity.GalleryAlbum;
import com.acove.blog.gallery.entity.GalleryImage;

/** 每个写操作各自位于事务代理边界内；文件操作由 AdminGalleryService 编排。 */
public interface GalleryTransactionService {

    GalleryAlbum createGallery(GalleryDTO galleryDTO);

    GalleryAlbum updateGallery(GalleryDTO galleryDTO);

    List<String> deleteGallery(Long id);

    GalleryImage addImage(Long galleryId, String imageUrl, String originalName);

    void reorderImages(GalleryReorderDTO reorderDTO);

    String deleteImage(Long id);
}
