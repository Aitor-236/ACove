package com.acove.blog.auth.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.acove.blog.auth.dto.SiteOwnerVO;
import com.acove.blog.auth.entity.SysUser;
import com.acove.blog.auth.mapper.SysUserMapper;
import com.acove.blog.auth.service.SiteProfileService;
import com.acove.blog.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SiteProfileServiceImpl implements SiteProfileService {

    /** 角色优先级，数值越小级别越高；出现没见过的角色时排到最后。 */
    private static final int ROLE_PRIORITY_OWNER = 0;
    private static final int ROLE_PRIORITY_ADMIN = 1;
    private static final int ROLE_PRIORITY_USER = 2;
    private static final int ROLE_PRIORITY_UNKNOWN = 3;

    private final SysUserMapper sysUserMapper;

    @Override
    public SiteOwnerVO getSiteOwner() {
        // 按 id 升序取出，再挑优先级最高的；同优先级时保留先遍历到的，也就是最早注册的那个
        List<SysUser> users = sysUserMapper.selectList(
                new LambdaQueryWrapper<SysUser>().orderByAsc(SysUser::getId));
        if (users.isEmpty()) {
            throw new BusinessException(404, "站点还没有创建用户");
        }

        SysUser owner = users.get(0);
        int bestPriority = rolePriority(owner.getRole());
        for (SysUser user : users) {
            int priority = rolePriority(user.getRole());
            if (priority < bestPriority) {
                owner = user;
                bestPriority = priority;
            }
        }

        String avatar = owner.getAvatar() == null ? "" : owner.getAvatar();
        return new SiteOwnerVO(owner.getUsername(), avatar);
    }

    private int rolePriority(String role) {
        if (role == null) {
            return ROLE_PRIORITY_UNKNOWN;
        }
        return switch (role.toLowerCase()) {
            case "owner" -> ROLE_PRIORITY_OWNER;
            case "admin" -> ROLE_PRIORITY_ADMIN;
            case "user" -> ROLE_PRIORITY_USER;
            default -> ROLE_PRIORITY_UNKNOWN;
        };
    }
}
