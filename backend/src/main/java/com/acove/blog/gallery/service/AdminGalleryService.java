package com.acove.blog.gallery.service;

import org.springframework.web.multipart.MultipartFile;

import com.acove.blog.gallery.dto.GalleryAlbumVO;
import com.acove.blog.gallery.dto.GalleryDTO;
import com.acove.blog.gallery.dto.GalleryImageVO;
import com.acove.blog.gallery.dto.GalleryReorderDTO;

public interface AdminGalleryService {

    GalleryAlbumVO createGallery(GalleryDTO galleryDTO);

    GalleryAlbumVO updateGallery(GalleryDTO galleryDTO);

    void deleteGallery(Long id);

    GalleryImageVO uploadImage(Long galleryId, MultipartFile file);

    void reorderImages(GalleryReorderDTO reorderDTO);

    void deleteImage(Long id);
}
