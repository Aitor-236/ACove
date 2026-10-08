package com.acove.blog.project.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.acove.blog.project.entity.OpenSourceProject;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/** 开源项目 Mapper：单一整表，没有复合主键或生成列，BaseMapper 的默认方法就够用。 */
@Mapper
public interface OpenSourceProjectMapper extends BaseMapper<OpenSourceProject> {
}
