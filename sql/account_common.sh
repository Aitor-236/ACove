#!/usr/bin/env bash
# 供 init_account.sh 和 deploy.sh 共用；仅生成 SQL / 错误提示，不连接数据库。

# 用 UTF-8 十六进制字面量，避免 SQL mode（如 NO_BACKSLASH_ESCAPES）改变转义含义。
account_sql_text() {
    local hex
    hex="$(printf '%s' "$1" | od -An -v -tx1 | tr -d ' \n')"
    # 字符集 introducer 保持字面量的 coercibility，让比较沿用目标列的排序规则。
    printf "_utf8mb4 X'%s'" "$hex"
}

account_write_sql() {
    local username email hash
    username="$(account_sql_text "$1")"
    email="$(account_sql_text "$2")"
    hash="$(account_sql_text "$3")"
    cat <<SQL
START TRANSACTION;
-- 旧 schema 必须先升级，缺少邮箱唯一约束或会话版本时不允许创建 / 重置账号。
SELECT id, token_version FROM sys_user FORCE INDEX (uk_email) WHERE 1 = 0;
UPDATE sys_user SET email = $email, password = $hash, token_version = token_version + 1
WHERE username = $username;
INSERT INTO sys_user (username, email, password)
SELECT $username, $email, $hash
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = $username);
SET @owner_exists := (SELECT COUNT(*) FROM sys_user WHERE role = 'owner');
SET @first_user_id := (SELECT MIN(id) FROM sys_user);
UPDATE sys_user SET role = 'owner'
WHERE @owner_exists = 0 AND @first_user_id IS NOT NULL AND id = @first_user_id;
COMMIT;
SQL
}

account_write_error() {
    local error="$1"
    if [[ "$error" == *"ERROR 1062 "* && ( "$error" == *"'sys_user.uk_email'" || "$error" == *"'uk_email'" ) ]]; then
        echo "错误：邮箱已被占用，账号未写入。请使用其他邮箱。" >&2
    elif [[ "$error" == *"ERROR 1062 "* && ( "$error" == *"'sys_user.uk_username'" || "$error" == *"'uk_username'" ) ]]; then
        echo "错误：用户名存在并发写入冲突，账号未写入，请重试。" >&2
    else
        printf '%s\n' "$error" >&2
        echo "错误：写入账号失败，请确认 MySQL 已启动且已应用 V7 邮箱唯一约束及 V10 会话撤销迁移。" >&2
    fi
}
