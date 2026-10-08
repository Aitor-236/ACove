package com.acove.blog.todo.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.acove.blog.common.result.Result;
import com.acove.blog.todo.dto.TodoDTO;
import com.acove.blog.todo.dto.TodoVO;
import com.acove.blog.todo.service.TodoService;

import lombok.RequiredArgsConstructor;

/**
 * 后台 Todo 管理接口，需要登录（路径不在白名单里）。
 * 写操作沿用项目惯例：一律 POST + query 参数或 body。
 */
@RestController
@RequestMapping("/admin/todo")
@RequiredArgsConstructor
public class AdminTodoController {

    private final TodoService todoService;

    /** 后台清单列表，和前台同一份数据，方便管理页刷新。 */
    @GetMapping("/list")
    public Result<List<TodoVO>> list() {
        return Result.success(todoService.listTodos());
    }

    /** 新建：标题必填，状态缺省「酝酿中」，新建的永远不是置顶。 */
    @PostMapping("/create")
    public Result<TodoVO> create(@RequestBody TodoDTO todoDTO) {
        return Result.success(todoService.createTodo(todoDTO));
    }

    /** 局部更新：id 必填，只改传了值的字段；置顶走 /pin 与 /unpin。 */
    @PostMapping("/update")
    public Result<TodoVO> update(@RequestBody TodoDTO todoDTO) {
        return Result.success(todoService.updateTodo(todoDTO));
    }

    /** 物理删除，不存在返回 404。 */
    @PostMapping("/delete")
    public Result<Void> delete(@RequestParam Long id) {
        todoService.deleteTodo(id);
        return Result.success();
    }

    /** 改状态：todo（酝酿中）/ doing（打磨中）/ done（已完成）。 */
    @PostMapping("/status")
    public Result<TodoVO> status(@RequestParam Long id, @RequestParam String status) {
        return Result.success(todoService.updateStatus(id, status));
    }

    /** 置顶：同一事务里会取消原来的置顶，全表只剩这一条置顶。 */
    @PostMapping("/pin")
    public Result<TodoVO> pin(@RequestParam Long id) {
        return Result.success(todoService.pin(id));
    }

    /** 取消置顶。 */
    @PostMapping("/unpin")
    public Result<TodoVO> unpin(@RequestParam Long id) {
        return Result.success(todoService.unpin(id));
    }
}
