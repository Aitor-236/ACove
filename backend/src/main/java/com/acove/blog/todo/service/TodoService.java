package com.acove.blog.todo.service;

import java.util.List;

import com.acove.blog.todo.dto.TodoDTO;
import com.acove.blog.todo.dto.TodoVO;

/**
 * Todo 清单：前台只读、后台可增删改，置顶全表唯一。
 * service 层只返回业务对象或抛业务异常，统一由 controller 包成 Result。
 */
public interface TodoService {

    /** 清单列表：置顶那条永远最先，其余按创建时间新 → 旧。 */
    List<TodoVO> listTodos();

    /** 新建 Todo：标题必填，状态缺省「酝酿中」，新建的永远不是置顶。 */
    TodoVO createTodo(TodoDTO todoDTO);

    /** 局部更新：id 必填，只改传了值的字段；置顶不走这里。 */
    TodoVO updateTodo(TodoDTO todoDTO);

    /** 物理删除，不存在返回 404。 */
    void deleteTodo(Long id);

    /** 改状态，只接受 todo / doing / done。 */
    TodoVO updateStatus(Long id, String status);

    /** 置顶：同一事务里先取消旧的置顶再置顶它，保证全表只有一条置顶。 */
    TodoVO pin(Long id);

    /** 取消置顶（本来就是普通状态时也算成功）。 */
    TodoVO unpin(Long id);
}
