package com.acove.blog.project.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.acove.blog.common.exception.BusinessException;
import com.acove.blog.project.dto.ProjectDTO;
import com.acove.blog.project.dto.ProjectVO;
import com.acove.blog.project.entity.OpenSourceProject;
import com.acove.blog.project.mapper.OpenSourceProjectMapper;
import com.acove.blog.project.service.ProjectService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    /** open_source_project.name 是 VARCHAR(200)，超长直接给 400，避免数据库截断异常变成 500 */
    private static final int NAME_MAX_LENGTH = 200;

    /** open_source_project.url 是 VARCHAR(500) */
    private static final int URL_MAX_LENGTH = 500;

    private final OpenSourceProjectMapper projectMapper;

    @Override
    public List<ProjectVO> listProjects() {
        // 新建的排前面；同一秒创建再用 id 兜底，保证顺序稳定
        return projectMapper.selectList(new LambdaQueryWrapper<OpenSourceProject>()
                        .orderByDesc(OpenSourceProject::getCreatedAt)
                        .orderByDesc(OpenSourceProject::getId))
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public ProjectVO createProject(ProjectDTO projectDTO) {
        String name = normalizeName(projectDTO.getName());

        LocalDateTime now = LocalDateTime.now();
        OpenSourceProject project = new OpenSourceProject();
        project.setName(name);
        project.setUrl(normalizeUrl(projectDTO.getUrl()));
        project.setDescription(normalizeText(projectDTO.getDescription()));
        project.setUpdateLog(normalizeText(projectDTO.getUpdateLog()));
        project.setNextStep(normalizeText(projectDTO.getNextStep()));
        // 预览图暂未启用：先统一留空，等后期补上传入口再写进来
        project.setPreviewImage(normalizeText(projectDTO.getPreviewImage()));
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        projectMapper.insert(project);

        return toVO(project);
    }

    @Override
    public ProjectVO updateProject(ProjectDTO projectDTO) {
        OpenSourceProject existing = requireProject(projectDTO.getId());

        OpenSourceProject update = new OpenSourceProject();
        update.setId(existing.getId());
        boolean changed = false;
        if (projectDTO.getName() != null) {
            update.setName(normalizeName(projectDTO.getName()));
            changed = true;
        }
        if (projectDTO.getUrl() != null) {
            update.setUrl(normalizeUrl(projectDTO.getUrl()));
            changed = true;
        }
        if (projectDTO.getDescription() != null) {
            update.setDescription(normalizeText(projectDTO.getDescription()));
            changed = true;
        }
        if (projectDTO.getUpdateLog() != null) {
            update.setUpdateLog(normalizeText(projectDTO.getUpdateLog()));
            changed = true;
        }
        if (projectDTO.getNextStep() != null) {
            update.setNextStep(normalizeText(projectDTO.getNextStep()));
            changed = true;
        }
        if (projectDTO.getPreviewImage() != null) {
            update.setPreviewImage(normalizeText(projectDTO.getPreviewImage()));
            changed = true;
        }
        // 一个字段都没传时直接回显，避免生成没有 SET 子句的 UPDATE
        if (!changed) {
            return toVO(existing);
        }

        projectMapper.updateById(update);
        return toVO(projectMapper.selectById(existing.getId()));
    }

    @Override
    public void deleteProject(Long id) {
        projectMapper.deleteById(requireProject(id).getId());
    }

    /** 取一条存在的项目，不存在（或没传 id）就按业务异常抛出去。 */
    private OpenSourceProject requireProject(Long id) {
        if (id == null) {
            throw new BusinessException("项目 ID不能为空");
        }
        OpenSourceProject project = projectMapper.selectById(id);
        if (project == null) {
            throw new BusinessException(404, "项目不存在");
        }
        return project;
    }

    private String normalizeName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException("项目名字不能为空");
        }
        String value = name.trim();
        if (value.length() > NAME_MAX_LENGTH) {
            throw new BusinessException("项目名字不能超过 " + NAME_MAX_LENGTH + " 个字符");
        }
        return value;
    }

    /** 地址可选：空就落空串；有值则去空格并卡长度。 */
    private String normalizeUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        String value = url.trim();
        if (value.length() > URL_MAX_LENGTH) {
            throw new BusinessException("项目地址不能超过 " + URL_MAX_LENGTH + " 个字符");
        }
        return value;
    }

    /** TEXT 列允许 NULL，这里统一落空串，前端和 VO 都不用再判 null。 */
    private String normalizeText(String text) {
        return text == null ? "" : text;
    }

    private ProjectVO toVO(OpenSourceProject project) {
        ProjectVO vo = new ProjectVO();
        vo.setId(project.getId());
        vo.setName(project.getName());
        vo.setUrl(normalizeText(project.getUrl()));
        vo.setDescription(normalizeText(project.getDescription()));
        vo.setUpdateLog(normalizeText(project.getUpdateLog()));
        vo.setNextStep(normalizeText(project.getNextStep()));
        vo.setPreviewImage(normalizeText(project.getPreviewImage()));
        vo.setCreatedAt(project.getCreatedAt());
        vo.setUpdatedAt(project.getUpdatedAt());
        return vo;
    }
}
