package com.acove.blog.auth.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.acove.blog.auth.dto.UserProfileDTO;
import com.acove.blog.auth.dto.UserProfileVO;
import com.acove.blog.auth.entity.SysUser;
import com.acove.blog.auth.mapper.SysUserMapper;
import com.acove.blog.auth.service.AdminUserService;
import com.acove.blog.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    /** sys_user.username / email 的列长度，超长直接给 400，避免数据库截断异常变成 500。 */
    private static final int USERNAME_MAX_LENGTH = 50;
    private static final int EMAIL_MAX_LENGTH = 100;

    /** 头像文件大小上限，和 application.yml 里的 multipart 限制保持一致。 */
    private static final long AVATAR_MAX_SIZE = 5L * 1024 * 1024;

    /** 允许的头像扩展名，落盘文件名沿用其中之一。 */
    private static final Set<String> AVATAR_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp", "gif");

    /** 邮箱格式校验，和前端表单保持同一口径。 */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final SysUserMapper sysUserMapper;

    /** 上传文件的根目录，默认是运行目录下的 uploads/。 */
    @Value("${blog.upload.dir:./uploads}")
    private String uploadDir;

    @Override
    public UserProfileVO getProfile(Long userId) {
        return toProfileVO(requireUser(userId));
    }

    @Override
    @Transactional
    public UserProfileVO updateProfile(Long userId, UserProfileDTO profileDTO) {
        SysUser existing = requireUser(userId);
        if (profileDTO == null) {
            throw new BusinessException("没有需要更新的内容");
        }

        String username = trimToNull(profileDTO.getUsername());
        String email = trimToNull(profileDTO.getEmail());
        if (username == null && email == null) {
            throw new BusinessException("没有需要更新的内容");
        }

        SysUser update = new SysUser();
        update.setId(existing.getId());

        if (username != null && !username.equals(existing.getUsername())) {
            requireWithinLength(username, USERNAME_MAX_LENGTH, "用户名");
            if (existsByUsername(username, existing.getId())) {
                throw new BusinessException("用户名已被占用，换一个试试");
            }
            update.setUsername(username);
        }

        if (email != null && !email.equals(existing.getEmail())) {
            requireWithinLength(email, EMAIL_MAX_LENGTH, "邮箱");
            if (!EMAIL_PATTERN.matcher(email).matches()) {
                throw new BusinessException("邮箱格式不正确");
            }
            // 登录支持邮箱登录，selectOne 遇到重复邮箱会报错，所以这里先挡住
            if (existsByEmail(email, existing.getId())) {
                throw new BusinessException("邮箱已被占用，换一个试试");
            }
            update.setEmail(email);
        }

        if (update.getUsername() != null || update.getEmail() != null) {
            sysUserMapper.updateById(update);
        }

        return getProfile(existing.getId());
    }

    @Override
    @Transactional
    public UserProfileVO updateAvatar(Long userId, MultipartFile file) {
        SysUser existing = requireUser(userId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的图片");
        }
        if (file.getSize() > AVATAR_MAX_SIZE) {
            throw new BusinessException("头像图片不能超过 5MB");
        }

        String extension = resolveExtension(file);
        String filename = existing.getId() + "-" + System.currentTimeMillis() + "-"
                + UUID.randomUUID().toString().substring(0, 8) + "." + extension;

        Path avatarDir = Paths.get(uploadDir).toAbsolutePath().normalize().resolve("avatar");
        try {
            Files.createDirectories(avatarDir);
            file.transferTo(avatarDir.resolve(filename));
        } catch (IOException | IllegalStateException ex) {
            throw new BusinessException(500, "头像保存失败，请稍后重试");
        }

        SysUser update = new SysUser();
        update.setId(existing.getId());
        // 数据库里存相对地址，前端按 /api 前缀访问，开发和生产都不用改代码
        update.setAvatar("/uploads/avatar/" + filename);
        sysUserMapper.updateById(update);

        return getProfile(existing.getId());
    }

    /** 取当前登录用户，未登录（token 里没有 userId）或用户已被删掉时抛业务异常。 */
    private SysUser requireUser(Long userId) {
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return user;
    }

    private boolean existsByUsername(String username, Long excludeUserId) {
        return sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .ne(SysUser::getId, excludeUserId)) > 0;
    }

    private boolean existsByEmail(String email, Long excludeUserId) {
        return sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getEmail, email)
                .ne(SysUser::getId, excludeUserId)) > 0;
    }

    /**
     * 优先按原始文件名取后缀；浏览器没给文件名时退回按 Content-Type 判断，
     * 两者都认不出来就按格式不支持处理。
     */
    private String resolveExtension(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (StringUtils.hasText(originalFilename) && originalFilename.lastIndexOf('.') >= 0) {
            String extension = originalFilename
                    .substring(originalFilename.lastIndexOf('.') + 1)
                    .toLowerCase(Locale.ROOT);
            if (AVATAR_EXTENSIONS.contains(extension)) {
                return extension;
            }
        }

        return switch (String.valueOf(file.getContentType()).toLowerCase(Locale.ROOT)) {
            case "image/png" -> "png";
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> throw new BusinessException("头像仅支持 png / jpg / webp / gif 图片");
        };
    }

    private void requireWithinLength(String value, int maxLength, String field) {
        if (value.length() > maxLength) {
            throw new BusinessException(field + "不能超过 " + maxLength + " 个字符");
        }
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private UserProfileVO toProfileVO(SysUser user) {
        String avatar = user.getAvatar() == null ? "" : user.getAvatar();
        return new UserProfileVO(user.getId(), user.getUsername(), user.getEmail(), avatar);
    }
}
