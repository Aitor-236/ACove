package com.acove.blog.project.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.acove.blog.common.result.Result;
import com.acove.blog.project.dto.ProjectDTO;
import com.acove.blog.project.dto.ProjectVO;
import com.acove.blog.project.service.ProjectService;

import lombok.RequiredArgsConstructor;

/**
 * 后台开源项目管理接口，需要登录（路径不在白名单里）。
 * 写操作沿用项目惯例：一律 POST + query 参数或 body。
 */
@RestController
@RequestMapping("/admin/project")
@RequiredArgsConstructor
public class AdminProjectController {

    private final ProjectService projectService;

    /** 后台项目列表，和前台同一份数据，方便管理页刷新。 */
    @GetMapping("/list")
    public Result<List<ProjectVO>> list() {
        return Result.success(projectService.listProjects());
    }

    /** 新建：项目名字必填，其余字段可选。 */
    @PostMapping("/create")
    public Result<ProjectVO> create(@RequestBody ProjectDTO projectDTO) {
        return Result.success(projectService.createProject(projectDTO));
    }

    /** 局部更新：id 必填，只改传了值的字段。 */
    @PostMapping("/update")
    public Result<ProjectVO> update(@RequestBody ProjectDTO projectDTO) {
        return Result.success(projectService.updateProject(projectDTO));
    }

    /** 物理删除，不存在返回 404。 */
    @PostMapping("/delete")
    public Result<Void> delete(@RequestParam Long id) {
        projectService.deleteProject(id);
        return Result.success();
    }
}
