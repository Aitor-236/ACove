package com.acove.blog.auth.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.acove.blog.auth.dto.SiteSettingDTO;
import com.acove.blog.auth.entity.SiteSetting;
import com.acove.blog.auth.mapper.SiteSettingMapper;
import com.acove.blog.common.exception.BusinessException;

class SiteSettingServiceImplTest {

    @TempDir
    Path uploadDir;

    private SiteSettingMapper mapper;
    private SiteSettingServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(SiteSettingMapper.class);
        service = new SiteSettingServiceImpl(mapper);
        ReflectionTestUtils.setField(service, "uploadDir", uploadDir.toString());
    }

    @Test
    void missingSettingsUseDefaultSiteIcon() {
        when(mapper.selectById(1L)).thenReturn(null);

        var settings = service.getSettings();

        assertEquals("ACove", settings.getSiteName());
        assertEquals("", settings.getSiteIcon());
    }

    @Test
    void uploadPngWritesUniqueFileAndUpdatesOnlySiteSetting() throws Exception {
        SiteSetting current = existingSettings();
        when(mapper.selectById(1L)).thenReturn(current);
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2};
        MockMultipartFile file = new MockMultipartFile("file", "icon.png", "image/png", png);

        var result = service.updateSiteIcon(file);

        assertTrue(result.getSiteIcon().startsWith("/uploads/favicon/"));
        assertTrue(result.getSiteIcon().endsWith(".png"));
        assertEquals("/uploads/hero/hero.png", result.getHeroImage());
        assertEquals("ACove", result.getSiteName());

        ArgumentCaptor<SiteSetting> update = ArgumentCaptor.forClass(SiteSetting.class);
        verify(mapper).updateById(update.capture());
        assertEquals(result.getSiteIcon(), update.getValue().getSiteIcon());
        assertEquals("center text", update.getValue().getHeroText());
        Path saved = uploadDir.resolve("favicon").resolve(result.getSiteIcon().substring("/uploads/favicon/".length()));
        org.junit.jupiter.api.Assertions.assertArrayEquals(png, Files.readAllBytes(saved));
    }

    @Test
    void invalidIconSignatureIsRejectedBeforeWriting() {
        when(mapper.selectById(1L)).thenReturn(existingSettings());
        MockMultipartFile file = new MockMultipartFile("file", "icon.png", "image/png", "not png".getBytes());

        var error = assertThrows(BusinessException.class, () -> service.updateSiteIcon(file));

        assertEquals("所选文件不是有效的 PNG 图片", error.getMessage());
        verify(mapper, never()).updateById(any(SiteSetting.class));
    }

    @Test
    void unsupportedIconFormatIsRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "favicon.svg", "image/svg+xml", "<svg/>".getBytes());

        var error = assertThrows(BusinessException.class, () -> service.updateSiteIcon(file));

        assertEquals("站点图标仅支持 ICO 或 PNG 图片", error.getMessage());
        verify(mapper, never()).updateById(any(SiteSetting.class));
    }

    @Test
    void uploadIcoAcceptsIcoSignature() {
        when(mapper.selectById(1L)).thenReturn(existingSettings());
        byte[] ico = {0, 0, 1, 0, 1, 0, 0};
        MockMultipartFile file = new MockMultipartFile("file", "favicon.ico", "image/x-icon", ico);

        var result = service.updateSiteIcon(file);

        assertTrue(result.getSiteIcon().startsWith("/uploads/favicon/"));
        assertTrue(result.getSiteIcon().endsWith(".ico"));
    }

    @Test
    void iconLargerThanOneMegabyteIsRejected() {
        byte[] bytes = new byte[1024 * 1024 + 1];
        bytes[0] = (byte) 0x89;
        bytes[1] = 'P';
        bytes[2] = 'N';
        bytes[3] = 'G';
        bytes[4] = 0x0D;
        bytes[5] = 0x0A;
        bytes[6] = 0x1A;
        bytes[7] = 0x0A;
        MockMultipartFile file = new MockMultipartFile("file", "icon.png", "image/png", bytes);

        var error = assertThrows(BusinessException.class, () -> service.updateSiteIcon(file));

        assertEquals("站点图标不能超过 1MB", error.getMessage());
        verify(mapper, never()).updateById(any(SiteSetting.class));
    }

    @Test
    void emptySiteIconRestoresDefaultWithoutChangingOtherSettings() {
        when(mapper.selectById(1L)).thenReturn(existingSettings());
        SiteSettingDTO dto = new SiteSettingDTO();
        dto.setSiteIcon("");

        var result = service.updateSettings(dto);

        assertEquals("", result.getSiteIcon());
        assertEquals("/uploads/hero/hero.png", result.getHeroImage());
        assertEquals("center text", result.getHeroText());
        verify(mapper).updateById(any(SiteSetting.class));
    }

    @Test
    void nonEmptySiteIconCannotBeSetThroughGenericSettingsUpdate() {
        when(mapper.selectById(1L)).thenReturn(existingSettings());
        SiteSettingDTO dto = new SiteSettingDTO();
        dto.setSiteIcon("/uploads/favicon/other.png");

        var error = assertThrows(BusinessException.class, () -> service.updateSettings(dto));

        assertEquals("站点图标请通过上传接口设置", error.getMessage());
        verify(mapper, never()).updateById(any(SiteSetting.class));
    }

    private SiteSetting existingSettings() {
        SiteSetting settings = new SiteSetting();
        settings.setId(1L);
        settings.setSiteName("ACove");
        settings.setHeroImage("/uploads/hero/hero.png");
        settings.setHeroText("center text");
        settings.setSiteIcon("");
        return settings;
    }
}
