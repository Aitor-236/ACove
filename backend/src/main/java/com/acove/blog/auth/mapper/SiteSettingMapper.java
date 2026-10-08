package com.acove.blog.auth.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.acove.blog.auth.entity.SiteSetting;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface SiteSettingMapper extends BaseMapper<SiteSetting> {
}
