package com.acove.blog.auth.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.acove.blog.auth.service.PageHeadService;

class PageHeadControllerTest {
    @Test
    void returnsUnwrappedUtf8HtmlAndForwardsOriginalPageHeaders() throws Exception {
        PageHeadService service = mock(PageHeadService.class);
        when(service.render("/articles/3?x=%26", "https", "blog.example:8443"))
                .thenReturn("<title>自定义文章</title>");
        var mvc = MockMvcBuilders.standaloneSetup(new PageHeadController(service)).build();
        var response = mvc.perform(get("/site/page-head")
                .header("X-Page-URI", "/articles/3?x=%26")
                .header("X-Page-Scheme", "https")
                .header("X-Page-Host", "blog.example:8443")).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertEquals("text/html;charset=UTF-8", response.getContentType());
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertEquals("<title>自定义文章</title>", response.getContentAsString());
        verify(service).render("/articles/3?x=%26", "https", "blog.example:8443");
    }
}
