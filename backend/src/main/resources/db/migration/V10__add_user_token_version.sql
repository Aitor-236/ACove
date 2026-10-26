-- 密码修改 / 账号重置递增版本，所有旧版本 JWT 随即失效。
ALTER TABLE sys_user
    ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0 COMMENT '登录令牌版本，改密或重置账号时递增';
