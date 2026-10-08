package com.acove.blog.todo.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acove.blog.common.result.Result;
import com.acove.blog.todo.dto.TodoVO;
import com.acove.blog.todo.service.TodoService;

import lombok.RequiredArgsConstructor;

/**
 * 前台 Todo 清单接口：只读，匿名可访问（/todo/list 已加进 JwtInterceptor 白名单）。
 */
@RestController
@RequestMapping("/todo")
@RequiredArgsConstructor
public class TodoController {

    private final TodoService todoService;

    /** 清单列表：置顶那条永远最先，其余按创建时间新 → 旧。 */
    @GetMapping("/list")
    public Result<List<TodoVO>> list() {
        return Result.success(todoService.listTodos());
    }
}
