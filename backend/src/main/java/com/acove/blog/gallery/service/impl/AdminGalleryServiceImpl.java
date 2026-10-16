package com.acove.blog.gallery.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.acove.blog.common.exception.BusinessException;
import com.acove.blog.common.utils.UploadFileNaming;
import com.acove.blog.gallery.dto.GalleryAlbumVO;
import com.acove.blog.gallery.dto.GalleryDTO;
import com.acove.blog.gallery.dto.GalleryImageVO;
import com.acove.blog.gallery.dto.GalleryReorderDTO;
import com.acove.blog.gallery.entity.GalleryImage;
import com.acove.blog.gallery.service.AdminGalleryService;
import com.acove.blog.gallery.service.GalleryService;
import com.acove.blog.gallery.service.GalleryTransactionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminGalleryServiceImpl implements AdminGalleryService {

    private static final long IMAGE_MAX_SIZE = 5L * 1024 * 1024;
    private static final String GALLERY_IMAGE_DIR = "gallery";
    private static final String GALLERY_IMAGE_URL_PREFIX = "/uploads/gallery/";
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp", "gif");

    @Value("${blog.upload.dir:./uploads}")
    private String uploadDir;

    private final GalleryService galleryService;
    private final GalleryTransactionService galleryTransactionService;

    @Override
    public GalleryAlbumVO createGallery(GalleryDTO galleryDTO) {
        return galleryService.getGallerySummary(
                galleryTransactionService.createGallery(galleryDTO).getId());
    }

    @Override
    public GalleryAlbumVO updateGallery(GalleryDTO galleryDTO) {
        return galleryService.getGallerySummary(
                galleryTransactionService.updateGallery(galleryDTO).getId());
    }

    @Override
    public void deleteGallery(Long id) {
        cleanupStoredFiles(galleryTransactionService.deleteGallery(id));
    }

    @Override
    public GalleryImageVO uploadImage(Long galleryId, MultipartFile file) {
        validateImage(file);
        String extension = resolveExtension(file);
        String filename = UploadFileNaming.create(UploadFileNaming.Module.GALLERY, extension);
        Path imageDir = galleryImageDirectory();
        Path target = imageDir.resolve(filename).normalize();
        if (!target.startsWith(imageDir)) {
            throw new BusinessException("图片保存路径不合法");
        }

        try {
            Files.createDirectories(imageDir);
            file.transferTo(target);
        } catch (IOException | IllegalStateException ex) {
            deleteNewFileQuietly(target);
            log.warn("Unable to save gallery image", ex);
            throw new BusinessException(500, "图片保存失败，请稍后重试");
        }

        try {
            GalleryImage image = galleryTransactionService.addImage(
                    galleryId, GALLERY_IMAGE_URL_PREFIX + filename, originalName(file, filename));
            return GalleryImageVO.from(image);
        } catch (RuntimeException ex) {
            deleteNewFileQuietly(target);
            throw ex;
        }
    }

    @Override
    public void reorderImages(GalleryReorderDTO reorderDTO) {
        galleryTransactionService.reorderImages(reorderDTO);
    }

    @Override
    public void deleteImage(Long id) {
        cleanupStoredFiles(List.of(galleryTransactionService.deleteImage(id)));
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的图片");
        }
        if (file.getSize() > IMAGE_MAX_SIZE) {
            throw new BusinessException("图片不能超过 5MB");
        }
    }

    private String resolveExtension(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (StringUtils.hasText(originalFilename) && originalFilename.lastIndexOf('.') >= 0) {
            String extension = originalFilename
                    .substring(originalFilename.lastIndexOf('.') + 1)
                    .toLowerCase(Locale.ROOT);
            if (IMAGE_EXTENSIONS.contains(extension)) {
                return extension;
            }
        }

        return switch (String.valueOf(file.getContentType()).toLowerCase(Locale.ROOT)) {
            case "image/png" -> "png";
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> throw new BusinessException("仅支持 png / jpg / webp / gif 图片");
        };
    }

    private String originalName(MultipartFile file, String fallback) {
        String originalFilename = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename)) {
            return fallback;
        }

        String basename = originalFilename.replace('\\', '/');
        basename = basename.substring(basename.lastIndexOf('/') + 1);
        if (basename.isBlank()) {
            return fallback;
        }
        int codePointCount = basename.codePointCount(0, basename.length());
        if (codePointCount > 255) {
            basename = basename.substring(0, basename.offsetByCodePoints(0, 255));
        }
        return basename;
    }

    private Path galleryImageDirectory() {
        return Paths.get(uploadDir).toAbsolutePath().normalize()
                .resolve(GALLERY_IMAGE_DIR).normalize();
    }

    private void cleanupStoredFiles(List<String> imageUrls) {
        List<String> failedUrls = new ArrayList<>();
        for (String imageUrl : imageUrls) {
            try {
                Path imagePath = resolveStoredImagePath(imageUrl);
                Files.deleteIfExists(imagePath);
            } catch (IOException | RuntimeException ex) {
                failedUrls.add(imageUrl);
                log.error("Unable to clean up gallery image file for URL {}", imageUrl, ex);
            }
        }

        if (!failedUrls.isEmpty()) {
            throw new BusinessException(500,
                    "图库记录已删除，但部分图片文件清理失败，请检查后端日志");
        }
    }

    private Path resolveStoredImagePath(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith(GALLERY_IMAGE_URL_PREFIX)) {
            throw new IllegalArgumentException("Stored gallery image URL is outside the gallery directory");
        }
        String filename = imageUrl.substring(GALLERY_IMAGE_URL_PREFIX.length());
        if (filename.isBlank() || filename.contains("/") || filename.contains("\\")) {
            throw new IllegalArgumentException("Stored gallery image URL is not a single file path");
        }

        Path imageDir = galleryImageDirectory();
        Path imagePath = imageDir.resolve(filename).normalize();
        if (!imagePath.startsWith(imageDir)) {
            throw new IllegalArgumentException("Stored gallery image URL escapes the gallery directory");
        }
        return imagePath;
    }

    private void deleteNewFileQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Unable to remove an uncommitted gallery image file", ex);
        }
    }
}
