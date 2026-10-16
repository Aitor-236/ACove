package com.acove.blog.gallery.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.acove.blog.common.exception.BusinessException;
import com.acove.blog.gallery.dto.GalleryAlbumVO;
import com.acove.blog.gallery.dto.GalleryDetailVO;
import com.acove.blog.gallery.dto.GalleryImageVO;
import com.acove.blog.gallery.entity.GalleryImage;
import com.acove.blog.gallery.mapper.GalleryAlbumMapper;
import com.acove.blog.gallery.mapper.GalleryImageMapper;
import com.acove.blog.gallery.service.GalleryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GalleryServiceImpl implements GalleryService {

    private final GalleryAlbumMapper galleryAlbumMapper;
    private final GalleryImageMapper galleryImageMapper;

    @Override
    public List<GalleryAlbumVO> listGalleries() {
        return galleryAlbumMapper.selectGallerySummaries();
    }

    @Override
    public GalleryDetailVO getGalleryDetail(Long id) {
        GalleryAlbumVO summary = getGallerySummary(id);
        List<GalleryImageVO> images = galleryImageMapper.selectList(
                        new LambdaQueryWrapper<GalleryImage>()
                                .eq(GalleryImage::getGalleryId, id)
                                .orderByAsc(GalleryImage::getSortOrder)
                                .orderByAsc(GalleryImage::getId))
                .stream()
                .map(GalleryImageVO::from)
                .collect(Collectors.toList());

        GalleryDetailVO detail = new GalleryDetailVO();
        detail.setId(summary.getId());
        detail.setName(summary.getName());
        detail.setDescription(summary.getDescription());
        detail.setCoverImage(summary.getCoverImage());
        detail.setImageCount(summary.getImageCount());
        detail.setCreatedAt(summary.getCreatedAt());
        detail.setUpdatedAt(summary.getUpdatedAt());
        detail.setImages(images);
        return detail;
    }

    @Override
    public GalleryAlbumVO getGallerySummary(Long id) {
        if (id == null) {
            throw new BusinessException("图库 ID 不能为空");
        }
        GalleryAlbumVO summary = galleryAlbumMapper.selectGallerySummary(id);
        if (summary == null) {
            throw new BusinessException(404, "图库不存在");
        }
        return summary;
    }
}
