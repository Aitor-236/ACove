package com.acove.blog.article.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.acove.blog.article.dto.UploadImageVO;
import com.acove.blog.article.service.AdminUploadService;
import com.acove.blog.common.exception.BusinessException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminUploadServiceImpl implements AdminUploadService {

    /** 正文配图大小上限，和 application.yml 里的 multipart 限制保持一致。 */
    private static final long IMAGE_MAX_SIZE = 5L * 1024 * 1024;

    /** 允许的图片扩展名，落盘文件名沿用其中之一。 */
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp", "gif");

    /** 正文配图的子目录，和头像分开放，便于按用途清理或迁移。 */
    private static final String ARTICLE_IMAGE_DIR = "article";

    /** 上传文件的根目录，默认是运行目录下的 uploads/。 */
    @Value("${blog.upload.dir:./uploads}")
    private String uploadDir;

    @Override
    public UploadImageVO uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的图片");
        }
        if (file.getSize() > IMAGE_MAX_SIZE) {
            throw new BusinessException("图片不能超过 5MB");
        }

        String extension = resolveExtension(file);
        String filename = System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8)
                + "." + extension;

        Path imageDir = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(ARTICLE_IMAGE_DIR);
        try {
            Files.createDirectories(imageDir);
            file.transferTo(imageDir.resolve(filename));
        } catch (IOException | IllegalStateException ex) {
            throw new BusinessException(500, "图片保存失败，请稍后重试");
        }

        // 和头像一样只存相对地址，正文渲染时统一加 /api 前缀
        return new UploadImageVO("/uploads/" + ARTICLE_IMAGE_DIR + "/" + filename,
                originalName(file, filename), file.getSize());
    }

    /**
     * 优先按原始文件名取后缀；浏览器没给文件名时退回按 Content-Type 判断，
     * 两者都认不出来就按格式不支持处理。
     */
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

    /** 原始文件名，取不到时退回落盘文件名。 */
    private String originalName(MultipartFile file, String fallback) {
        String originalFilename = file.getOriginalFilename();
        return StringUtils.hasText(originalFilename) ? originalFilename : fallback;
    }
}
