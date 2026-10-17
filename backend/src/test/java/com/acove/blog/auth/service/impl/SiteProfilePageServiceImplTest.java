package com.acove.blog.auth.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.acove.blog.auth.dto.SiteProfilePageDTO;
import com.acove.blog.auth.dto.SiteProfilePageVO;
import com.acove.blog.auth.entity.SiteProfileHtmlBundle;
import com.acove.blog.auth.entity.SiteProfilePage;
import com.acove.blog.auth.entity.SysUser;
import com.acove.blog.auth.mapper.SiteProfileHtmlBundleMapper;
import com.acove.blog.auth.mapper.SiteProfilePageMapper;
import com.acove.blog.auth.mapper.SysUserMapper;
import com.acove.blog.auth.support.ProfilePageHtmlPackageValidator;
import com.acove.blog.common.exception.BusinessException;

class SiteProfilePageServiceImplTest {

    private SiteProfilePageMapper pageMapper;
    private SiteProfileHtmlBundleMapper bundleMapper;
    private SysUserMapper sysUserMapper;
    private SiteProfilePageServiceImpl service;

    @BeforeEach
    void setUp() {
        pageMapper = mock(SiteProfilePageMapper.class);
        bundleMapper = mock(SiteProfileHtmlBundleMapper.class);
        sysUserMapper = mock(SysUserMapper.class);
        service = new SiteProfilePageServiceImpl(
                pageMapper, bundleMapper, sysUserMapper, new ProfilePageHtmlPackageValidator());
    }

    @Test
    void getAdminPage_rejectsNonOwnerBeforeReadingPageConfiguration() {
        SysUser user = new SysUser();
        user.setId(42L);
        user.setRole("admin");
        when(sysUserMapper.selectById(42L)).thenReturn(user);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.getAdminPage(42L));

        assertEquals(403, exception.getCode());
        verify(pageMapper, never()).selectById(1);
    }

    @Test
    void getAdminPage_requiresExactOwnerRole() {
        SysUser user = new SysUser();
        user.setId(42L);
        user.setRole("OWNER");
        when(sysUserMapper.selectById(42L)).thenReturn(user);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.getAdminPage(42L));

        assertEquals(403, exception.getCode());
        verify(pageMapper, never()).selectById(1);
    }

    @Test
    void getPublicPage_doesNotExposeRetainedMarkdownWhenHtmlIsActive() {
        String bundleId = "8d3f8db5-3aeb-4a47-83c7-92418f8b8e90";
        SiteProfilePage page = page("html", "private retained markdown", bundleId);
        SiteProfileHtmlBundle bundle = bundle(bundleId, "active");
        when(pageMapper.selectById(1)).thenReturn(page);
        when(bundleMapper.selectById(bundleId)).thenReturn(bundle);

        SiteProfilePageVO result = service.getPublicPage();

        assertEquals("html", result.getMode());
        assertEquals("", result.getMarkdownContent());
        assertEquals("/site/profile-page/html/" + bundleId, result.getHtmlUrl());
    }

    @Test
    void getPublicHtmlDocument_rejectsStagedOrInactiveBundle() {
        String bundleId = "8d3f8db5-3aeb-4a47-83c7-92418f8b8e90";
        when(pageMapper.selectById(1)).thenReturn(page("markdown", "public markdown", bundleId));

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.getPublicHtmlDocument(bundleId));

        assertEquals(404, exception.getCode());
        verify(bundleMapper, never()).selectById(bundleId);
    }

    @Test
    void saveMarkdown_keepsPreviouslySavedHtmlBundle() {
        Long ownerId = 7L;
        String bundleId = "8d3f8db5-3aeb-4a47-83c7-92418f8b8e90";
        SysUser owner = new SysUser();
        owner.setId(ownerId);
        owner.setRole("owner");
        SiteProfilePage current = page("html", "old markdown", bundleId);
        SiteProfileHtmlBundle active = bundle(bundleId, "active");
        when(sysUserMapper.selectById(ownerId)).thenReturn(owner);
        when(pageMapper.selectById(1)).thenReturn(current);
        when(bundleMapper.selectList(any())).thenReturn(List.of());
        when(bundleMapper.selectById(bundleId)).thenReturn(active);

        SiteProfilePageDTO request = new SiteProfilePageDTO();
        request.setMode("markdown");
        request.setMarkdownContent("updated markdown");

        var saved = service.save(ownerId, request);

        assertEquals("markdown", saved.getMode());
        assertEquals("updated markdown", saved.getMarkdownContent());
        assertEquals(bundleId, saved.getHtmlBundleId());
        verify(bundleMapper, never()).deleteById(bundleId);
    }

    @Test
    void getAdminPreviewHtmlDocument_addsAssetBaseForSandboxedSrcdoc() {
        Long ownerId = 7L;
        String bundleId = "8d3f8db5-3aeb-4a47-83c7-92418f8b8e90";
        SysUser owner = new SysUser();
        owner.setId(ownerId);
        owner.setRole("owner");
        SiteProfileHtmlBundle staged = bundle(bundleId, "staged");
        staged.setHtmlContent("<!doctype html><html><head></head><body>preview</body></html>");
        when(sysUserMapper.selectById(ownerId)).thenReturn(owner);
        when(bundleMapper.selectById(bundleId)).thenReturn(staged);

        String preview = service.getAdminPreviewHtmlDocument(ownerId, bundleId);

        assertTrue(preview.contains("/api/uploads/profile-page/" + bundleId + "/"));
        assertTrue(preview.contains("target=\"_blank\""));
    }

    private static SiteProfilePage page(String mode, String markdown, String bundleId) {
        SiteProfilePage page = new SiteProfilePage();
        page.setId(1);
        page.setActiveMode(mode);
        page.setMarkdownContent(markdown);
        page.setHtmlBundleId(bundleId);
        return page;
    }

    private static SiteProfileHtmlBundle bundle(String id, String status) {
        SiteProfileHtmlBundle bundle = new SiteProfileHtmlBundle();
        bundle.setId(id);
        bundle.setStatus(status);
        bundle.setHtmlContent("<html><head></head><body>page</body></html>");
        bundle.setAssetPath("profile-page/" + id);
        return bundle;
    }
}
