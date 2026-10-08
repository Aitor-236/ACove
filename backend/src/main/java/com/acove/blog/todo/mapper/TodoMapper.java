package com.acove.blog.todo.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.acove.blog.todo.entity.Todo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * Todo 清单 Mapper：表里的 pinned_flag 是生成列，实体没有对应字段，
 * 所以 BaseMapper 的增删改查都不会碰到它。
 */
@Mapper
public interface TodoMapper extends BaseMapper<Todo> {
}
