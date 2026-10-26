package com.acove.blog.auth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import com.acove.blog.auth.entity.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper 
public interface SysUserMapper extends BaseMapper<SysUser> {
    /** 密码与令牌版本原子更新；并发改密 / 账号重置后，旧校验结果不能覆盖新密码。 */
    @Update("""
            UPDATE sys_user
            SET password = #{newPassword}, token_version = token_version + 1
            WHERE id = #{id} AND password = #{oldPassword} AND token_version = #{tokenVersion}
            """)
    int changePasswordAndRevokeTokens(@Param("id") Long id,
            @Param("oldPassword") String oldPassword, @Param("newPassword") String newPassword,
            @Param("tokenVersion") Long tokenVersion);
}
