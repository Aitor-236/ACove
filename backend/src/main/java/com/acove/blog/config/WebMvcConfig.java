package com.acove.blog.config;

import java.nio.file.Paths;
import java.time.Duration;

import com.acove.blog.common.interceptor.JwtInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@RequiredArgsConstructor
@Configuration 
public class WebMvcConfig implements WebMvcConfigurer {
    private final JwtInterceptor jwtInterceptor;

    /** 上传文件的落盘目录，和 AdminUserServiceImpl 共用同一份配置。 */
    @Value("${blog.upload.dir:./uploads}")
    private String uploadDir;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
        .addPathPatterns("/**")                // intercept all requests
        .excludePathPatterns(
                "/auth/login",                 // login endpoint
                "/article/list",               // public article cards
                "/article/home",               // 前台首页动态列表（后台挑选 + 顺序）
                "/article/detail/**",          // public article detail
                "/category/list",              // public category list
                "/tag/list",                   // public tag list（前台标签面板）
                "/site/owner",                 // public site owner profile
                "/site/settings",              // public site settings（网站名 / 站点图标 / 首页头图 / 首页文字 / ICP 备案信息）
                "/site/profile-page",          // public personal introduction content
                "/site/profile-page/html/**",  // public, sandboxed active HTML document
                "/todo/list",                  // 前台 Todo 清单（只读；写操作都在 /admin/todo/** 下）
                "/project/list",               // 前台开源项目列表（只读；写操作都在 /admin/project/** 下）
                "/gallery/list",               // 前台图库列表（只读；写操作都在 /admin/gallery/** 下）
                "/gallery/detail/**",          // 前台图库详情
                "/visit/report",               // 前台页面浏览上报（匿名，访客标识走 Cookie）
                "/uploads/**");                // 头像等静态资源，<img> 请求不会带 token
    }

    /**
     * 把上传目录挂到 /uploads/** 上：数据库里存的是 /uploads/avatar/xxx.png，
     * 前端访问时加 /api 前缀（dev 由 vite、生产由 nginx 去掉前缀转发到后端）。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadDir).toAbsolutePath().normalize().toUri().toString();
        String uploadLocation = location.endsWith("/") ? location : location + "/";
        CacheControl immutableImageCache = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();
        registry.addResourceHandler("/uploads/article/**")
                .addResourceLocations(uploadLocation + "article/")
                .setCacheControl(immutableImageCache);
        registry.addResourceHandler("/uploads/gallery/**")
                .addResourceLocations(uploadLocation + "gallery/")
                .setCacheControl(immutableImageCache);
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadLocation);
    }
}
