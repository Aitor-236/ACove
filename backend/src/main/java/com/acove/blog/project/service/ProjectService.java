package com.acove.blog.project.service;

import java.util.List;

import com.acove.blog.project.dto.ProjectDTO;
import com.acove.blog.project.dto.ProjectVO;

/**
 * 开源项目：前台只读、后台可增删改。
 * service 层只返回业务对象或抛业务异常，统一由 controller 包成 Result。
 */
public interface ProjectService {

    /** 项目列表：按创建时间新 → 旧（同秒再按 id 兜底）。 */
    List<ProjectVO> listProjects();

    /** 新建：项目名字必填。 */
    ProjectVO createProject(ProjectDTO projectDTO);

    /** 局部更新：id 必填，只改传了值的字段。 */
    ProjectVO updateProject(ProjectDTO projectDTO);

    /** 物理删除，不存在返回 404。 */
    void deleteProject(Long id);
}
