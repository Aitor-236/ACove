package com.acove.blog.project.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 开源项目实体：对应 {@code open_source_project} 表，一行就是前台「开源项目」页的一张卡片。
 *
 * <p>{@code description} / {@code updateLog} / {@code nextStep} 是 TEXT 列（可为 NULL），
 * service 统一落成空串，前端和 VO 都不用再判 null。
 *
 * <p>{@code previewImage} 是预览图相对地址，<b>暂未启用</b>：列已建好、默认空串，
 * 等后期做好预览图上传再补 UI，前台现在不展示、也不占用位置。
 */
@Data
@TableName("open_source_project")
public class OpenSourceProject {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目名字 */
    private String name;

    /** 项目地址（对外链接），空串表示还没填 */
    private String url;

    /** 项目介绍 */
    private String description;

    /** 日志：最新更新内容 */
    private String updateLog;

    /** 下一步（todo） */
    private String nextStep;

    /** 预览图相对地址；暂未启用，保持空串，前台不展示 */
    private String previewImage;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
