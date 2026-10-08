package com.acove.blog.auth.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.acove.blog.auth.entity.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper 
public interface SysUserMapper extends BaseMapper<SysUser> {
    
}
