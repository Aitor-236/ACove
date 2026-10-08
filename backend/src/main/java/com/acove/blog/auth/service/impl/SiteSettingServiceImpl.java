package com.acove.blog.auth.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.acove.blog.auth.dto.SiteSettingDTO;
import com.acove.blog.auth.dto.SiteSettingVO;
import com.acove.blog.auth.entity.SiteSetting;
import com.acove.blog.auth.mapper.SiteSettingMapper;
import com.acove.blog.auth.service.SiteSettingService;
import com.acove.blog.common.exception.BusinessException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SiteSettingServiceImpl implements SiteSettingService {

    /** 单行配置表固定的主键 */
    private static final long SETTING_ID = 1L;

    /** 还没配置过网站名时的兜底值，和 sql/init_database.sql 里的默认值保持一致 */
    private static final String DEFAULT_SITE_NAME = "ACove";

    /** 头图在数据库里存的是相对地址，统一以这个前缀开头 */
    private static final String UPLOAD_URL_PREFIX = "/uploads/";

    /** 头图落盘的子目录，和头像（avatar）/ 正文配图（article）分开 */
    private static final String HERO_IMAGE_DIR = "hero";

    /** 各字段长度上限，和 sql/init_database.sql 里的列定义保持一致 */
    private static final int SITE_NAME_MAX_LENGTH = 50;
    private static final int HERO_TEXT_MAX_LENGTH = 200;
    private static final int HERO_IMAGE_MAX_LENGTH = 255;

    /** 头图文件大小上限，和 application.yml 里的 multipart 限制保持一致 */
    private static final long HERO_IMAGE_MAX_SIZE = 5L * 1024 * 1024;

    /** 允许的头图扩展名，落盘文件名沿用其中之一 */
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp", "gif");

    private final SiteSettingMapper siteSettingMapper;

    /** 上传文件的根目录，默认是运行目录下的 uploads/ */
    @Value("${blog.upload.dir:./uploads}")
    private String uploadDir;

    @Override
    public SiteSettingVO getSettings() {
        return toVO(siteSettingMapper.selectById(SETTING_ID));
    }

    @Override
    @Transactional
    public SiteSettingVO updateSettings(SiteSettingDTO settingDTO) {
        if (settingDTO == null) {
            throw new BusinessException("没有需要更新的内容");
        }

        SiteSetting current = currentOrDefault();

        String siteName = trimToNull(settingDTO.getSiteName());
        if (siteName != null) {
            requireWithinLength(siteName, SITE_NAME_MAX_LENGTH, "网站名");
            current.setSiteName(siteName);
        }

        // 首页文字允许清空，所以空串也算一次有效修改
        if (settingDTO.getHeroText() != null) {
            String heroText = settingDTO.getHeroText().trim();
            requireWithinLength(heroText, HERO_TEXT_MAX_LENGTH, "首页文字");
            current.setHeroText(heroText);
        }

        // 头图只接受自己上传产生的相对地址，避免被写进任意外链
        if (settingDTO.getHeroImage() != null) {
            String heroImage = settingDTO.getHeroImage().trim();
            if (!heroImage.isEmpty() && !heroImage.startsWith(UPLOAD_URL_PREFIX)) {
                throw new BusinessException("头图地址不合法");
            }
            requireWithinLength(heroImage, HERO_IMAGE_MAX_LENGTH, "头图地址");
            current.setHeroImage(heroImage);
        }

        return save(current);
    }

    @Override
    @Transactional
    public SiteSettingVO updateHeroImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的图片");
        }
        if (file.getSize() > HERO_IMAGE_MAX_SIZE) {
            throw new BusinessException("头图不能超过 5MB");
        }

        String extension = resolveExtension(file);
        String filename = System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8)
                + "." + extension;

        Path heroDir = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(HERO_IMAGE_DIR);
        try {
            Files.createDirectories(heroDir);
            file.transferTo(heroDir.resolve(filename));
        } catch (IOException | IllegalStateException ex) {
            throw new BusinessException(500, "头图保存失败，请稍后重试");
        }

        SiteSetting current = currentOrDefault();
        current.setHeroImage(UPLOAD_URL_PREFIX + HERO_IMAGE_DIR + "/" + filename);
        return save(current);
    }

    /**
     * 取当前配置行；数据库里还没有（没跑过初始化脚本）时按默认值建一份内存对象。
     */
    private SiteSetting currentOrDefault() {
        SiteSetting setting = siteSettingMapper.selectById(SETTING_ID);
        if (setting != null) {
            return setting;
        }
        SiteSetting created = new SiteSetting();
        created.setId(SETTING_ID);
        created.setSiteName(DEFAULT_SITE_NAME);
        created.setHeroImage("");
        created.setHeroText("");
        return created;
    }

    /**
     * 写回配置行：不存在就插入，存在就整体覆盖。
     * 每次都显式写全部字段，避免 MyBatis-Plus 只更新非 null 字段带来的丢字段问题；
     * updated_at 不在这里赋值，交给数据库的 ON UPDATE CURRENT_TIMESTAMP。
     */
    private SiteSettingVO save(SiteSetting current) {
        SiteSetting update = new SiteSetting();
        update.setId(SETTING_ID);
        update.setSiteName(current.getSiteName());
        update.setHeroImage(current.getHeroImage() == null ? "" : current.getHeroImage());
        update.setHeroText(current.getHeroText() == null ? "" : current.getHeroText());

        if (siteSettingMapper.selectById(SETTING_ID) != null) {
            siteSettingMapper.updateById(update);
        } else {
            siteSettingMapper.insert(update);
        }
        return toVO(update);
    }

    private SiteSettingVO toVO(SiteSetting setting) {
        if (setting == null) {
            return new SiteSettingVO(DEFAULT_SITE_NAME, "", "");
        }
        String siteName = StringUtils.hasText(setting.getSiteName()) ? setting.getSiteName() : DEFAULT_SITE_NAME;
        return new SiteSettingVO(siteName,
                setting.getHeroImage() == null ? "" : setting.getHeroImage(),
                setting.getHeroText() == null ? "" : setting.getHeroText());
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
            default -> throw new BusinessException("头图仅支持 png / jpg / webp / gif 图片");
        };
    }

    private void requireWithinLength(String value, int maxLength, String field) {
        if (value.length() > maxLength) {
            throw new BusinessException(field + "不能超过 " + maxLength + " 个字符");
        }
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
