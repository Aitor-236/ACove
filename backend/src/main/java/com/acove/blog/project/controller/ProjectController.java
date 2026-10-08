package com.acove.blog.project.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acove.blog.common.result.Result;
import com.acove.blog.project.dto.ProjectVO;
import com.acove.blog.project.service.ProjectService;

import lombok.RequiredArgsConstructor;

/**
 * 前台开源项目接口：只读，匿名可访问（/project/list 已加进 JwtInterceptor 白名单）。
 */
@RestController
@RequestMapping("/project")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    /** 项目列表：一次性返回全部，按创建时间新 → 旧排好，前端直接渲染卡片。 */
    @GetMapping("/list")
    public Result<List<ProjectVO>> list() {
        return Result.success(projectService.listProjects());
    }
}
