package com.acove.blog.auth.service;

import org.springframework.web.multipart.MultipartFile;

import com.acove.blog.auth.dto.AdminSiteProfilePageVO;
import com.acove.blog.auth.dto.SiteProfileHtmlPackageVO;
import com.acove.blog.auth.dto.SiteProfilePageDTO;
import com.acove.blog.auth.dto.SiteProfilePageVO;

/** Site owner's public introduction page. */
public interface SiteProfilePageService {

    SiteProfilePageVO getPublicPage();

    String getPublicHtmlDocument(String bundleId);

    AdminSiteProfilePageVO getAdminPage(Long userId);

    SiteProfileHtmlPackageVO uploadHtmlPackage(Long userId, MultipartFile file);

    AdminSiteProfilePageVO save(Long userId, SiteProfilePageDTO pageDTO);

    String getAdminPreviewHtmlDocument(Long userId, String bundleId);
}
