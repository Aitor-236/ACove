package com.acove.blog.todo.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.acove.blog.common.exception.BusinessException;
import com.acove.blog.todo.dto.TodoDTO;
import com.acove.blog.todo.dto.TodoVO;
import com.acove.blog.todo.entity.Todo;
import com.acove.blog.todo.mapper.TodoMapper;
import com.acove.blog.todo.service.TodoService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TodoServiceImpl implements TodoService {

    /** todo.status 的三个取值，和 db/migration/V2__create_todo.sql 的列注释一致 */
    private static final String STATUS_TODO = "todo";
    private static final String STATUS_DOING = "doing";
    private static final String STATUS_DONE = "done";
    private static final Set<String> STATUSES = Set.of(STATUS_TODO, STATUS_DOING, STATUS_DONE);

    /** todo.title 是 VARCHAR(200)，超长直接给 400，避免数据库截断异常变成 500 */
    private static final int TITLE_MAX_LENGTH = 200;

    private static final int PINNED = 1;
    private static final int UNPINNED = 0;

    private final TodoMapper todoMapper;

    @Override
    public List<TodoVO> listTodos() {
        // is_pinned 只有 0 / 1，倒序即置顶优先；其余按创建时间新 → 旧，同秒再用 id 兜底
        return todoMapper.selectList(new LambdaQueryWrapper<Todo>()
                        .orderByDesc(Todo::getIsPinned)
                        .orderByDesc(Todo::getCreatedAt)
                        .orderByDesc(Todo::getId))
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public TodoVO createTodo(TodoDTO todoDTO) {
        String title = normalizeTitle(todoDTO.getTitle());
        String status = todoDTO.getStatus() == null
                ? STATUS_TODO
                : normalizeStatus(todoDTO.getStatus());

        LocalDateTime now = LocalDateTime.now();
        Todo todo = new Todo();
        todo.setTitle(title);
        todo.setDescription(normalizeDescription(todoDTO.getDescription()));
        todo.setStatus(status);
        todo.setIsPinned(UNPINNED);
        todo.setCreatedAt(now);
        todo.setUpdatedAt(now);
        todoMapper.insert(todo);

        return toVO(todo);
    }

    @Override
    public TodoVO updateTodo(TodoDTO todoDTO) {
        Todo existing = requireTodo(todoDTO.getId());

        Todo update = new Todo();
        update.setId(existing.getId());
        boolean changed = false;
        if (todoDTO.getTitle() != null) {
            update.setTitle(normalizeTitle(todoDTO.getTitle()));
            changed = true;
        }
        if (todoDTO.getDescription() != null) {
            update.setDescription(normalizeDescription(todoDTO.getDescription()));
            changed = true;
        }
        if (todoDTO.getStatus() != null) {
            update.setStatus(normalizeStatus(todoDTO.getStatus()));
            changed = true;
        }
        // 一个字段都没传时直接回显，避免生成没有 SET 子句的 UPDATE
        if (!changed) {
            return toVO(existing);
        }

        todoMapper.updateById(update);
        return toVO(todoMapper.selectById(existing.getId()));
    }

    @Override
    public void deleteTodo(Long id) {
        todoMapper.deleteById(requireTodo(id).getId());
    }

    @Override
    public TodoVO updateStatus(Long id, String status) {
        Todo existing = requireTodo(id);

        Todo update = new Todo();
        update.setId(existing.getId());
        update.setStatus(normalizeStatus(status));
        todoMapper.updateById(update);

        return toVO(todoMapper.selectById(existing.getId()));
    }

    @Override
    @Transactional
    public TodoVO pin(Long id) {
        Todo target = requireTodo(id);

        // 表上有唯一索引 uk_todo_pinned（建在生成列 pinned_flag 上），
        // 必须先取消旧置顶再置顶新的，否则第二条 is_pinned = 1 会直接撞唯一键。
        todoMapper.update(null, new LambdaUpdateWrapper<Todo>()
                .eq(Todo::getIsPinned, PINNED)
                .ne(Todo::getId, target.getId())
                .set(Todo::getIsPinned, UNPINNED));

        Todo update = new Todo();
        update.setId(target.getId());
        update.setIsPinned(PINNED);
        todoMapper.updateById(update);

        return toVO(todoMapper.selectById(target.getId()));
    }

    @Override
    public TodoVO unpin(Long id) {
        Todo target = requireTodo(id);

        Todo update = new Todo();
        update.setId(target.getId());
        update.setIsPinned(UNPINNED);
        todoMapper.updateById(update);

        return toVO(todoMapper.selectById(target.getId()));
    }

    /** 取一条存在的 Todo，不存在（或没传 id）就按业务异常抛出去。 */
    private Todo requireTodo(Long id) {
        if (id == null) {
            throw new BusinessException("Todo ID不能为空");
        }
        Todo todo = todoMapper.selectById(id);
        if (todo == null) {
            throw new BusinessException(404, "Todo 不存在");
        }
        return todo;
    }

    private String normalizeTitle(String title) {
        if (!StringUtils.hasText(title)) {
            throw new BusinessException("标题不能为空");
        }
        String value = title.trim();
        if (value.length() > TITLE_MAX_LENGTH) {
            throw new BusinessException("标题不能超过 " + TITLE_MAX_LENGTH + " 个字符");
        }
        return value;
    }

    /** description 列允许 NULL，这里统一落空串，前端和 VO 都不用再判 null。 */
    private String normalizeDescription(String description) {
        return description == null ? "" : description;
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            throw new BusinessException("状态不能为空");
        }
        String value = status.trim().toLowerCase(Locale.ROOT);
        if (!STATUSES.contains(value)) {
            throw new BusinessException("状态只能是 todo（酝酿中）、doing（打磨中）、done（已完成）");
        }
        return value;
    }

    private TodoVO toVO(Todo todo) {
        TodoVO vo = new TodoVO();
        vo.setId(todo.getId());
        vo.setTitle(todo.getTitle());
        vo.setDescription(normalizeDescription(todo.getDescription()));
        vo.setStatus(todo.getStatus());
        vo.setIsPinned(todo.getIsPinned());
        vo.setCreatedAt(todo.getCreatedAt());
        vo.setUpdatedAt(todo.getUpdatedAt());
        return vo;
    }
}
