package com.acove.blog.todo.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * Todo 出参：前台只读清单和后台管理列表共用同一份结构。
 */
@Data
public class TodoVO {

    private Long id;

    private String title;

    /** 描述 / 备注，没有时是空串，不会是 null */
    private String description;

    /** 状态：todo-酝酿中，doing-打磨中，done-已完成 */
    private String status;

    /** 是否置顶：1-置顶，0-普通；全表最多一条为 1 */
    private Integer isPinned;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
