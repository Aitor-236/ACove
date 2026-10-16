package com.acove.blog.gallery.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.acove.blog.common.result.Result;
import com.acove.blog.gallery.dto.GalleryAlbumVO;
import com.acove.blog.gallery.dto.GalleryDTO;
import com.acove.blog.gallery.dto.GalleryImageVO;
import com.acove.blog.gallery.dto.GalleryReorderDTO;
import com.acove.blog.gallery.service.AdminGalleryService;
import com.acove.blog.gallery.service.GalleryService;

import lombok.RequiredArgsConstructor;

/** 后台图库管理接口；路径不在 JWT 匿名白名单内。 */
@RestController
@RequestMapping("/admin/gallery")
@RequiredArgsConstructor
public class AdminGalleryController {

    private final GalleryService galleryService;
    private final AdminGalleryService adminGalleryService;

    @GetMapping("/list")
    public Result<List<GalleryAlbumVO>> list() {
        return Result.success(galleryService.listGalleries());
    }

    @PostMapping("/create")
    public Result<GalleryAlbumVO> create(@RequestBody GalleryDTO galleryDTO) {
        return Result.success(adminGalleryService.createGallery(galleryDTO));
    }

    @PostMapping("/update")
    public Result<GalleryAlbumVO> update(@RequestBody GalleryDTO galleryDTO) {
        return Result.success(adminGalleryService.updateGallery(galleryDTO));
    }

    @PostMapping("/delete")
    public Result<Void> delete(@RequestParam Long id) {
        adminGalleryService.deleteGallery(id);
        return Result.success();
    }

    @PostMapping("/{galleryId}/images/upload")
    public Result<GalleryImageVO> uploadImage(
            @PathVariable Long galleryId,
            @RequestPart("file") MultipartFile file) {
        return Result.success(adminGalleryService.uploadImage(galleryId, file));
    }

    @PostMapping("/images/reorder")
    public Result<Void> reorderImages(@RequestBody GalleryReorderDTO reorderDTO) {
        adminGalleryService.reorderImages(reorderDTO);
        return Result.success();
    }

    @PostMapping("/images/delete")
    public Result<Void> deleteImage(@RequestParam Long id) {
        adminGalleryService.deleteImage(id);
        return Result.success();
    }
}
