package com.acove.blog.gallery.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acove.blog.common.result.Result;
import com.acove.blog.gallery.dto.GalleryAlbumVO;
import com.acove.blog.gallery.dto.GalleryDetailVO;
import com.acove.blog.gallery.service.GalleryService;

import lombok.RequiredArgsConstructor;

/** 前台图库接口：匿名只读。 */
@RestController
@RequestMapping("/gallery")
@RequiredArgsConstructor
public class GalleryController {

    private final GalleryService galleryService;

    @GetMapping("/list")
    public Result<List<GalleryAlbumVO>> list() {
        return Result.success(galleryService.listGalleries());
    }

    @GetMapping("/detail/{id}")
    public Result<GalleryDetailVO> detail(@PathVariable Long id) {
        return Result.success(galleryService.getGalleryDetail(id));
    }
}
