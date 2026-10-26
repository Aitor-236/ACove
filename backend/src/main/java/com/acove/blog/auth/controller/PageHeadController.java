package com.acove.blog.auth.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.acove.blog.auth.service.PageHeadService;
import lombok.RequiredArgsConstructor;

/** Nginx SSI rendering endpoint: intentionally returns HTML rather than the JSON Result envelope. */
@RestController
@RequiredArgsConstructor
public class PageHeadController {
    private final PageHeadService pageHeadService;

    @GetMapping(value = "/site/page-head", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> head(
            @RequestHeader(value = "X-Page-URI", defaultValue = "/") String uri,
            @RequestHeader(value = "X-Page-Scheme", defaultValue = "") String scheme,
            @RequestHeader(value = "X-Page-Host", defaultValue = "") String host) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8))
                .body(pageHeadService.render(uri, scheme, host));
    }
}
