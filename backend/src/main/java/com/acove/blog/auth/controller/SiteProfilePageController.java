package com.acove.blog.auth.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acove.blog.auth.dto.SiteProfilePageVO;
import com.acove.blog.auth.service.SiteProfilePageService;
import com.acove.blog.common.result.Result;

import lombok.RequiredArgsConstructor;

/** Public read-only endpoints for the owner's personal introduction page. */
@RestController
@RequestMapping("/site/profile-page")
@RequiredArgsConstructor
public class SiteProfilePageController {

    private static final String HTML_CSP = "sandbox allow-same-origin allow-popups allow-popups-to-escape-sandbox; "
            + "default-src 'none'; script-src 'none'; connect-src 'none'; "
            + "style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; "
            + "object-src 'none'; frame-src 'none'; form-action 'none'; base-uri 'self'";

    private final SiteProfilePageService siteProfilePageService;

    @GetMapping
    public Result<SiteProfilePageVO> getPublicPage() {
        return Result.success(siteProfilePageService.getPublicPage());
    }

    /** The raw document is only returned for the currently active HTML bundle. */
    @GetMapping(value = "/html/{bundleId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getPublicHtmlDocument(@PathVariable String bundleId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8));
        headers.set("Content-Security-Policy", HTML_CSP);
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "SAMEORIGIN");
        headers.set("Referrer-Policy", "no-referrer");
        headers.setCacheControl(CacheControl.noCache().mustRevalidate());
        return ResponseEntity.ok()
                .headers(headers)
                .body(siteProfilePageService.getPublicHtmlDocument(bundleId));
    }
}
