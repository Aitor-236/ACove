package com.acove.blog.gallery.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.acove.blog.common.exception.BusinessException;
import com.acove.blog.gallery.dto.GalleryDTO;
import com.acove.blog.gallery.dto.GalleryReorderDTO;
import com.acove.blog.gallery.entity.GalleryAlbum;
import com.acove.blog.gallery.entity.GalleryImage;
import com.acove.blog.gallery.mapper.GalleryAlbumMapper;
import com.acove.blog.gallery.mapper.GalleryImageMapper;
import com.acove.blog.gallery.service.GalleryTransactionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GalleryTransactionServiceImpl implements GalleryTransactionService {

    private static final int NAME_MAX_LENGTH = 200;
    private static final int DESCRIPTION_MAX_BYTES = 65_535;
    private static final int IMAGE_URL_MAX_LENGTH = 255;

    private final GalleryAlbumMapper galleryAlbumMapper;
    private final GalleryImageMapper galleryImageMapper;

    @Override
    @Transactional
    public GalleryAlbum createGallery(GalleryDTO galleryDTO) {
        if (galleryDTO == null) {
            throw new BusinessException("图库信息不能为空");
        }

        LocalDateTime now = LocalDateTime.now();
        GalleryAlbum album = new GalleryAlbum();
        album.setName(normalizeName(galleryDTO.getName()));
        album.setDescription(normalizeDescription(galleryDTO.getDescription()));
        album.setCreatedAt(now);
        album.setUpdatedAt(now);
        galleryAlbumMapper.insert(album);
        return album;
    }

    @Override
    @Transactional
    public GalleryAlbum updateGallery(GalleryDTO galleryDTO) {
        if (galleryDTO == null || galleryDTO.getId() == null) {
            throw new BusinessException("图库 ID 不能为空");
        }

        GalleryAlbum existing = lockGallery(galleryDTO.getId());
        if (galleryDTO.getName() == null && galleryDTO.getDescription() == null) {
            return existing;
        }

        LambdaUpdateWrapper<GalleryAlbum> update = new LambdaUpdateWrapper<GalleryAlbum>()
                .eq(GalleryAlbum::getId, existing.getId())
                .set(GalleryAlbum::getUpdatedAt, LocalDateTime.now());
        if (galleryDTO.getName() != null) {
            update.set(GalleryAlbum::getName, normalizeName(galleryDTO.getName()));
        }
        if (galleryDTO.getDescription() != null) {
            update.set(GalleryAlbum::getDescription,
                    normalizeDescription(galleryDTO.getDescription()));
        }
        galleryAlbumMapper.update(null, update);
        return galleryAlbumMapper.selectById(existing.getId());
    }

    @Override
    @Transactional
    public List<String> deleteGallery(Long id) {
        GalleryAlbum album = lockGallery(id);
        List<String> imageUrls = galleryImageMapper.selectList(
                        new LambdaQueryWrapper<GalleryImage>()
                                .eq(GalleryImage::getGalleryId, album.getId())
                                .orderByAsc(GalleryImage::getId))
                .stream()
                .map(GalleryImage::getImageUrl)
                .collect(Collectors.toList());

        // 由外键级联删除图片行；调用方在事务提交后清理这些地址对应的文件。
        galleryAlbumMapper.deleteById(album.getId());
        return imageUrls;
    }

    @Override
    @Transactional
    public GalleryImage addImage(Long galleryId, String imageUrl, String originalName) {
        GalleryAlbum album = lockGallery(galleryId);
        if (!StringUtils.hasText(imageUrl) || imageUrl.length() > IMAGE_URL_MAX_LENGTH) {
            throw new BusinessException("图片地址不合法");
        }

        GalleryImage lastImage = galleryImageMapper.selectOne(
                new LambdaQueryWrapper<GalleryImage>()
                        .eq(GalleryImage::getGalleryId, album.getId())
                        .orderByDesc(GalleryImage::getSortOrder)
                        .orderByDesc(GalleryImage::getId)
                        .last("LIMIT 1"));
        Integer lastSortOrder = lastImage == null ? null : lastImage.getSortOrder();
        if (lastImage != null && (lastSortOrder == null || lastSortOrder < 0
                || lastSortOrder == Integer.MAX_VALUE)) {
            throw new BusinessException("图库图片数量已达到上限");
        }
        int sortOrder = lastImage == null ? 0 : lastSortOrder + 1;

        GalleryImage image = new GalleryImage();
        image.setGalleryId(album.getId());
        image.setImageUrl(imageUrl);
        image.setOriginalName(originalName == null ? "" : originalName);
        image.setSortOrder(sortOrder);
        image.setCreatedAt(LocalDateTime.now());
        galleryImageMapper.insert(image);
        touchGallery(album.getId());
        return image;
    }

    @Override
    @Transactional
    public void reorderImages(GalleryReorderDTO reorderDTO) {
        if (reorderDTO == null || reorderDTO.getGalleryId() == null
                || reorderDTO.getImageIds() == null) {
            throw new BusinessException("图库 ID 和图片顺序不能为空");
        }

        GalleryAlbum album = lockGallery(reorderDTO.getGalleryId());
        List<Long> requestedIds = reorderDTO.getImageIds();
        if (requestedIds.stream().anyMatch(id -> id == null)) {
            throw new BusinessException("图片 ID 不能为空");
        }
        Set<Long> requestedSet = new HashSet<>(requestedIds);
        if (requestedSet.size() != requestedIds.size()) {
            throw new BusinessException("图片顺序中不能包含重复 ID");
        }

        List<Long> currentIds = galleryImageMapper.selectList(
                        new LambdaQueryWrapper<GalleryImage>()
                                .eq(GalleryImage::getGalleryId, album.getId())
                                .orderByAsc(GalleryImage::getSortOrder)
                                .orderByAsc(GalleryImage::getId))
                .stream()
                .map(GalleryImage::getId)
                .collect(Collectors.toList());
        if (requestedIds.size() != currentIds.size()
                || !requestedSet.equals(new HashSet<>(currentIds))) {
            throw new BusinessException("图片顺序必须包含该图库的全部图片，且不能包含其他图库的图片");
        }

        for (int index = 0; index < requestedIds.size(); index++) {
            galleryImageMapper.update(null,
                    new LambdaUpdateWrapper<GalleryImage>()
                            .eq(GalleryImage::getId, requestedIds.get(index))
                            .eq(GalleryImage::getGalleryId, album.getId())
                            .set(GalleryImage::getSortOrder, index));
        }
        if (!requestedIds.isEmpty()) {
            touchGallery(album.getId());
        }
    }

    @Override
    @Transactional
    public String deleteImage(Long id) {
        if (id == null) {
            throw new BusinessException("图片 ID 不能为空");
        }

        GalleryImage observed = galleryImageMapper.selectById(id);
        if (observed == null) {
            throw new BusinessException(404, "图片不存在");
        }

        GalleryAlbum album = lockGallery(observed.getGalleryId());
        GalleryImage image = galleryImageMapper.selectByIdForUpdate(id);
        if (image == null || !album.getId().equals(image.getGalleryId())) {
            throw new BusinessException(404, "图片不存在");
        }

        galleryImageMapper.deleteById(image.getId());
        touchGallery(album.getId());
        return image.getImageUrl();
    }

    private GalleryAlbum lockGallery(Long id) {
        if (id == null) {
            throw new BusinessException("图库 ID 不能为空");
        }
        GalleryAlbum album = galleryAlbumMapper.selectByIdForUpdate(id);
        if (album == null) {
            throw new BusinessException(404, "图库不存在");
        }
        return album;
    }

    private void touchGallery(Long id) {
        galleryAlbumMapper.update(null,
                new LambdaUpdateWrapper<GalleryAlbum>()
                        .eq(GalleryAlbum::getId, id)
                        .set(GalleryAlbum::getUpdatedAt, LocalDateTime.now()));
    }

    private String normalizeName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException("图库名称不能为空");
        }
        String normalized = name.trim();
        if (normalized.codePointCount(0, normalized.length()) > NAME_MAX_LENGTH) {
            throw new BusinessException("图库名称不能超过 " + NAME_MAX_LENGTH + " 个字符");
        }
        return normalized;
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        if (description.getBytes(StandardCharsets.UTF_8).length > DESCRIPTION_MAX_BYTES) {
            throw new BusinessException("图库简介不能超过数据库 TEXT 字段容量");
        }
        return description;
    }
}
