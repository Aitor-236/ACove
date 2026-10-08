package com.acove.blog.todo.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * Todo 清单实体：站长个人的一份待办清单。
 *
 * <p>状态 {@code status} 用中性英文码存库 —— {@code todo}（酝酿中，新建默认）、
 * {@code doing}（打磨中）、{@code done}（已完成）；展示文案由前端映射。
 *
 * <p>全表最多一条置顶：由表里的生成列 {@code pinned_flag} 配唯一索引 {@code uk_todo_pinned}
 * 兜底，所以这里的 {@code isPinned} 只是 0 / 1 业务标记，
 * <b>生成列刻意不映射成字段</b>，增删改查不会碰到它。
 * 置顶新的一条时要先取消旧的置顶，再置顶新的，否则会撞唯一键。
 */
@Data
@TableName("todo")
public class Todo {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 标题 */
    private String title;

    /** 描述 / 备注，可为空 */
    private String description;

    /** 状态：todo-酝酿中，doing-打磨中，done-已完成 */
    private String status;

    /** 是否置顶：1-置顶，0-普通（全表最多一条为 1） */
    private Integer isPinned;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
