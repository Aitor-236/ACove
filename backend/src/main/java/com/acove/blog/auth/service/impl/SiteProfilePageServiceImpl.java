package com.acove.blog.auth.service.impl;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.acove.blog.auth.dto.AdminSiteProfilePageVO;
import com.acove.blog.auth.dto.SiteProfileHtmlPackageVO;
import com.acove.blog.auth.dto.SiteProfilePageDTO;
import com.acove.blog.auth.dto.SiteProfilePageVO;
import com.acove.blog.auth.entity.SiteProfileHtmlBundle;
import com.acove.blog.auth.entity.SiteProfilePage;
import com.acove.blog.auth.entity.SysUser;
import com.acove.blog.auth.mapper.SiteProfileHtmlBundleMapper;
import com.acove.blog.auth.mapper.SiteProfilePageMapper;
import com.acove.blog.auth.mapper.SysUserMapper;
import com.acove.blog.auth.service.SiteProfilePageService;
import com.acove.blog.auth.service.SiteProfileService;
import com.acove.blog.auth.support.ProfilePageHtmlPackageValidator;
import com.acove.blog.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SiteProfilePageServiceImpl implements SiteProfilePageService {

    private static final int PAGE_ID = 1;
    private static final int MARKDOWN_MAX_LENGTH = 200_000;
    private static final String MODE_MARKDOWN = "markdown";
    private static final String MODE_HTML = "html";
    private static final String BUNDLE_STAGED = "staged";
    private static final String BUNDLE_ACTIVE = "active";
    private static final String BUNDLE_PATH_PREFIX = "profile-page/";

    private final SiteProfilePageMapper pageMapper;
    private final SiteProfileHtmlBundleMapper bundleMapper;
    private final SysUserMapper sysUserMapper;
    private final SiteProfileService siteProfileService;
    private final ProfilePageHtmlPackageValidator htmlPackageValidator;

    @Value("${blog.upload.dir:./uploads}")
    private String uploadDir;

    @Override
    public SiteProfilePageVO getPublicPage() {
        SiteProfilePage page = currentOrDefault();
        String mode = normalizedMode(page.getActiveMode());
        String htmlUrl = "";
        if (MODE_HTML.equals(mode) && StringUtils.hasText(page.getHtmlBundleId())) {
            SiteProfileHtmlBundle activeBundle = bundleMapper.selectById(page.getHtmlBundleId());
            if (activeBundle != null && BUNDLE_ACTIVE.equals(activeBundle.getStatus())) {
                htmlUrl = htmlUrl(page.getHtmlBundleId());
            } else {
                mode = MODE_MARKDOWN;
            }
        } else if (MODE_HTML.equals(mode)) {
            mode = MODE_MARKDOWN;
        }
        String publicMarkdown = MODE_MARKDOWN.equals(mode) ? nullToEmpty(page.getMarkdownContent()) : "";
        String contactEmail = "";
        if (MODE_MARKDOWN.equals(mode)) {
            try {
                contactEmail = siteProfileService.getSiteOwnerEmail();
            } catch (BusinessException ex) {
                if (ex.getCode() != 404) throw ex;
            }
        }
        return new SiteProfilePageVO(mode, publicMarkdown, htmlUrl, contactEmail);
    }

    @Override
    public String getPublicHtmlDocument(String bundleId) {
        if (!isCanonicalUuid(bundleId)) {
            throw new BusinessException(404, "HTML页面不存在");
        }
        SiteProfilePage page = currentOrDefault();
        if (!MODE_HTML.equals(normalizedMode(page.getActiveMode()))
                || !bundleId.equalsIgnoreCase(page.getHtmlBundleId())) {
            throw new BusinessException(404, "HTML页面不存在");
        }
        SiteProfileHtmlBundle bundle = bundleMapper.selectById(bundleId);
        if (bundle == null || !BUNDLE_ACTIVE.equals(bundle.getStatus())) {
            throw new BusinessException(404, "HTML页面不存在");
        }
        return renderHtmlDocument(bundle);
    }

    @Override
    public String getAdminPreviewHtmlDocument(Long userId, String bundleId) {
        requireOwner(userId);
        if (!isCanonicalUuid(bundleId)) {
            throw new BusinessException(404, "HTML页面不存在");
        }
        SiteProfileHtmlBundle bundle = bundleMapper.selectById(bundleId);
        if (bundle == null || !(BUNDLE_ACTIVE.equals(bundle.getStatus()) || BUNDLE_STAGED.equals(bundle.getStatus()))) {
            throw new BusinessException(404, "HTML页面不存在");
        }
        return renderHtmlDocument(bundle);
    }

    @Override
    public AdminSiteProfilePageVO getAdminPage(Long userId) {
        requireOwner(userId);
        SiteProfilePage page = currentOrDefault();
        String bundleId = page.getHtmlBundleId();
        SiteProfileHtmlBundle bundle = StringUtils.hasText(bundleId) ? bundleMapper.selectById(bundleId) : null;
        if (bundle == null) {
            bundleId = "";
        }
        return new AdminSiteProfilePageVO(
                normalizedMode(page.getActiveMode()),
                nullToEmpty(page.getMarkdownContent()),
                bundleId,
                StringUtils.hasText(bundleId) ? adminPreviewUrl(bundleId) : "");
    }

    @Override
    @Transactional
    public SiteProfileHtmlPackageVO uploadHtmlPackage(Long userId, MultipartFile file) {
        requireOwner(userId);
        ProfilePageHtmlPackageValidator.ValidatedPackage validated = htmlPackageValidator.validate(file);
        String bundleId = UUID.randomUUID().toString();
        String assetPath = BUNDLE_PATH_PREFIX + bundleId;
        Path bundleDirectory = uploadRoot().resolve(bundleId);
        Path temporaryDirectory = uploadRoot().resolve(".stage-" + bundleId);

        try {
            Files.createDirectories(temporaryDirectory);
            for (var asset : validated.assets().entrySet()) {
                Path target = temporaryDirectory.resolve(asset.getKey()).normalize();
                if (!target.startsWith(temporaryDirectory)) {
                    throw new BusinessException("资源包包含不安全的文件路径");
                }
                Files.createDirectories(target.getParent());
                Files.write(target, asset.getValue());
            }
            Files.createDirectories(bundleDirectory.getParent());
            Files.move(temporaryDirectory, bundleDirectory, StandardCopyOption.ATOMIC_MOVE);
            cleanupOnRollback(bundleDirectory);

            SiteProfileHtmlBundle bundle = new SiteProfileHtmlBundle();
            bundle.setId(bundleId);
            bundle.setHtmlContent(validated.htmlDocument());
            bundle.setAssetPath(assetPath);
            bundle.setStatus(BUNDLE_STAGED);
            bundleMapper.insert(bundle);

            List<SiteProfileHtmlBundle> oldStaged = bundleMapper.selectList(
                    new LambdaQueryWrapper<SiteProfileHtmlBundle>()
                            .eq(SiteProfileHtmlBundle::getStatus, BUNDLE_STAGED)
                            .ne(SiteProfileHtmlBundle::getId, bundleId));
            oldStaged.forEach(this::deleteBundleAfterCommit);

            return new SiteProfileHtmlPackageVO(bundleId, adminPreviewUrl(bundleId));
        } catch (BusinessException ex) {
            deleteDirectoryQuietly(temporaryDirectory);
            deleteDirectoryQuietly(bundleDirectory);
            throw ex;
        } catch (IOException | RuntimeException ex) {
            deleteDirectoryQuietly(temporaryDirectory);
            deleteDirectoryQuietly(bundleDirectory);
            log.error("Failed to save personal profile HTML package", ex);
            throw new BusinessException(500, "HTML资源包保存失败，请稍后重试");
        }
    }

    @Override
    @Transactional
    public AdminSiteProfilePageVO save(Long userId, SiteProfilePageDTO pageDTO) {
        requireOwner(userId);
        if (pageDTO == null) {
            throw new BusinessException("没有需要保存的内容");
        }

        String mode = normalizedMode(pageDTO.getMode());
        if (!MODE_MARKDOWN.equals(mode) && !MODE_HTML.equals(mode)) {
            throw new BusinessException("展示模式只能是 Markdown 或 HTML");
        }

        SiteProfilePage current = currentOrDefault();
        String markdownContent = pageDTO.getMarkdownContent() == null
                ? nullToEmpty(current.getMarkdownContent())
                : pageDTO.getMarkdownContent();
        if (markdownContent.length() > MARKDOWN_MAX_LENGTH) {
            throw new BusinessException("Markdown简介不能超过 200000 个字符");
        }

        List<SiteProfileHtmlBundle> stagedBundles = bundleMapper.selectList(
                new LambdaQueryWrapper<SiteProfileHtmlBundle>()
                        .eq(SiteProfileHtmlBundle::getStatus, BUNDLE_STAGED));
        String nextBundleId = current.getHtmlBundleId();
        SiteProfileHtmlBundle selectedBundle = null;

        if (MODE_HTML.equals(mode)) {
            String requestedBundleId = StringUtils.hasText(pageDTO.getHtmlBundleId())
                    ? pageDTO.getHtmlBundleId().trim()
                    : current.getHtmlBundleId();
            if (!isCanonicalUuid(requestedBundleId)) {
                throw new BusinessException("请先导入并预览一个 HTML 资源包");
            }
            selectedBundle = bundleMapper.selectById(requestedBundleId);
            if (selectedBundle == null
                    || !(BUNDLE_STAGED.equals(selectedBundle.getStatus())
                            || (BUNDLE_ACTIVE.equals(selectedBundle.getStatus())
                                    && requestedBundleId.equals(current.getHtmlBundleId())))) {
                throw new BusinessException("HTML资源包已失效，请重新导入");
            }
            nextBundleId = requestedBundleId;
        }

        String previousBundleId = current.getHtmlBundleId();
        current.setId(PAGE_ID);
        current.setActiveMode(mode);
        current.setMarkdownContent(markdownContent);
        current.setHtmlBundleId(nextBundleId);
        savePage(current);

        if (selectedBundle != null) {
            selectedBundle.setStatus(BUNDLE_ACTIVE);
            bundleMapper.updateById(selectedBundle);
        }

        for (SiteProfileHtmlBundle staged : stagedBundles) {
            if (!staged.getId().equals(nextBundleId)) {
                deleteBundleAfterCommit(staged);
            }
        }

        if (StringUtils.hasText(previousBundleId) && !previousBundleId.equals(nextBundleId)) {
            SiteProfileHtmlBundle previousBundle = bundleMapper.selectById(previousBundleId);
            if (previousBundle != null) {
                deleteBundleAfterCommit(previousBundle);
            }
        }

        return getAdminPage(userId);
    }

    private String renderHtmlDocument(SiteProfileHtmlBundle bundle) {
        String html = bundle.getHtmlContent();
        String baseTag = "<base href=\"/api/uploads/" + bundle.getAssetPath() + "/\" target=\"_blank\">";
        java.util.regex.Matcher head = java.util.regex.Pattern.compile("(?is)<head(?:\\s[^>]*)?>").matcher(html);
        if (head.find()) {
            return html.substring(0, head.end()) + baseTag + html.substring(head.end());
        }
        return "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" "
                + "content=\"width=device-width, initial-scale=1\">" + baseTag
                + "</head><body>" + html + "</body></html>";
    }

    private SiteProfilePage currentOrDefault() {
        SiteProfilePage page = pageMapper.selectById(PAGE_ID);
        if (page != null) {
            return page;
        }
        SiteProfilePage fallback = new SiteProfilePage();
        fallback.setId(PAGE_ID);
        fallback.setActiveMode(MODE_MARKDOWN);
        fallback.setMarkdownContent("");
        fallback.setHtmlBundleId(null);
        return fallback;
    }

    private void savePage(SiteProfilePage page) {
        if (pageMapper.selectById(PAGE_ID) == null) {
            pageMapper.insert(page);
        } else {
            pageMapper.updateById(page);
        }
    }

    private void requireOwner(Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if (!"owner".equals(user.getRole())) {
            throw new BusinessException(403, "仅站长可以管理个人简介");
        }
    }

    private String normalizedMode(String mode) {
        return StringUtils.hasText(mode) ? mode.trim().toLowerCase(Locale.ROOT) : MODE_MARKDOWN;
    }

    private String htmlUrl(String bundleId) {
        return "/site/profile-page/html/" + bundleId;
    }

    private String adminPreviewUrl(String bundleId) {
        return "/admin/profile-page/html-preview/" + bundleId;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean isCanonicalUuid(String value) {
        if (!StringUtils.hasText(value)) return false;
        try {
            return UUID.fromString(value).toString().equalsIgnoreCase(value);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private Path uploadRoot() {
        return Paths.get(uploadDir).toAbsolutePath().normalize().resolve("profile-page");
    }

    private void deleteBundleAfterCommit(SiteProfileHtmlBundle bundle) {
        bundleMapper.deleteById(bundle.getId());
        Runnable cleanup = () -> deleteBundleDirectory(bundle.getAssetPath());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cleanup.run();
                }
            });
        } else {
            cleanup.run();
        }
    }

    private void cleanupOnRollback(Path directory) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    deleteDirectoryQuietly(directory);
                }
            }
        });
    }

    private void deleteBundleDirectory(String assetPath) {
        if (!StringUtils.hasText(assetPath)) return;
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize().resolve("profile-page");
        Path directory = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(assetPath).normalize();
        if (!directory.startsWith(root) || directory.equals(root)) return;
        deleteDirectoryQuietly(directory);
    }

    private void deleteDirectoryQuietly(Path directory) {
        if (directory == null || !Files.exists(directory)) return;
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (exc != null) throw exc;
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            log.warn("Failed to remove profile page asset directory {}", directory, ex);
        }
    }
}
