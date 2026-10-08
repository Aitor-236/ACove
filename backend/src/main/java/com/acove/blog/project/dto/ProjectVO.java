package com.acove.blog.project.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 开源项目出参：前台卡片和后台管理列表共用同一份结构。
 *
 * <p>文本字段（description / updateLog / nextStep）没有内容是空串，不会是 null；
 * {@code previewImage} 暂未启用，当前恒为空串，前端不展示。
 */
@Data
public class ProjectVO {

    private Long id;

    private String name;

    /** 项目地址，没有时是空串 */
    private String url;

    private String description;

    /** 日志：最新更新内容 */
    private String updateLog;

    /** 下一步（todo） */
    private String nextStep;

    /** 预览图相对地址；暂未启用，恒为空串 */
    private String previewImage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
