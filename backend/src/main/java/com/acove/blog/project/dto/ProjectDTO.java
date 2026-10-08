package com.acove.blog.project.dto;

import lombok.Data;

/**
 * 开源项目入参：新建只传 name（其余字段可选），修改时需要同时传 id。
 *
 * <p>{@code previewImage} 也收，但 <b>当前前端没有入口</b>：列先建在表里、值留空，
 * 等后期做预览图上传时再补 UI；这里保留字段是为了到时候不用改契约。
 */
@Data
public class ProjectDTO {

    /** 项目 ID，新增时为空，修改时必填 */
    private Long id;

    /** 项目名字，必填，不能超过 200 个字符 */
    private String name;

    /** 项目地址（对外链接），可选，不能超过 500 个字符 */
    private String url;

    /** 项目介绍，可选，传空串表示清空 */
    private String description;

    /** 日志：最新更新内容，可选，传空串表示清空 */
    private String updateLog;

    /** 下一步（todo），可选，传空串表示清空 */
    private String nextStep;

    /** 预览图相对地址；暂未启用（前端没有入口，后期再补） */
    private String previewImage;
}
