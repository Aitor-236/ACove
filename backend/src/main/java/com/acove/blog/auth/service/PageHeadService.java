package com.acove.blog.auth.service;

/** HTML head fragment for Nginx SSI; contains public metadata only. */
public interface PageHeadService {
    String render(String requestUri, String scheme, String host);
}
