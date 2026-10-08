package com.acove.blog.todo.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import lombok.Data;

/**
 * Todo 入参：新建只传 title（description / status 可选），修改时需要同时传 id。
 *
 * <p>刻意不接受 isPinned：置顶要走 /admin/todo/pin 与 /admin/todo/unpin，
 * 那里会在同一事务里先取消旧置顶，保证全表只有一条置顶。
 */
@Data
public class TodoDTO {

    /** Todo ID，新增时为空，修改时必填 */
    private Long id;

    /** 标题，必填，不能超过 200 个字符 */
    private String title;

    /** 描述 / 备注，可选，传空串表示清空 */
    private String description;

    /** 状态：todo-酝酿中（默认），doing-打磨中，done-已完成；其它值返回 400 */
    @JsonAlias("todoStatus")
    private String status;
}
