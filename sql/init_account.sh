#!/usr/bin/env bash
# ============================================================
# ACove：部署后创建登录账号
# 用法：
#   ./sql/init_account.sh                              # 交互式输入用户名、邮箱、密码
#   ./sql/init_account.sh acove acove.x@outlook.com     # 用户名邮箱走参数，密码仍交互输入
#   ./sql/init_account.sh --print-hash                  # 只生成 BCrypt 哈希并打印，不连数据库
#                                                      # （Docker 部署时由 ./deploy.sh account 调用）
# 连接信息可用环境变量覆盖（默认与 application-local.yml.template 保持一致）：
#   BLOG_DB_HOST（默认 127.0.0.1）
#   BLOG_DB_PORT（默认 3306）
#   BLOG_DB_NAME（默认 blog_db）
#   BLOG_DB_USER（默认 root）
#   BLOG_DB_PASSWORD
#   BLOG_ACCOUNT_PASSWORD  设置后跳过密码交互，便于 CI / 自动化部署
# 说明：
#   - 后端用 BCryptPasswordEncoder 校验密码，所以这里先生成 BCrypt 哈希再入库，
#     数据库中永远不存明文密码。
#   - 用户名已存在时更新它的邮箱和密码，可以当作"重置密码"来用。
#   - 依赖：mysql 客户端，以及 python3 + bcrypt（或 apache2-utils 里的 htpasswd）。
# ============================================================
set -euo pipefail

DB_HOST="${BLOG_DB_HOST:-127.0.0.1}"
DB_PORT="${BLOG_DB_PORT:-3306}"
DB_NAME="${BLOG_DB_NAME:-blog_db}"
DB_USER="${BLOG_DB_USER:-root}"
DB_PASSWORD="${BLOG_DB_PASSWORD:-}"

USERNAME="${1:-}"
EMAIL="${2:-}"

# 生成 BCrypt 哈希（$2b$10$，与后端 BCryptPasswordEncoder 默认强度一致）
# 密码通过环境变量传给 python，避免出现在进程命令行里被 ps 看到
hash_password() {
    local plain="$1"
    if command -v python3 >/dev/null 2>&1 && python3 -c 'import bcrypt' >/dev/null 2>&1; then
        BLOG_PLAIN_PW="$plain" python3 -c \
            'import bcrypt, os; print(bcrypt.hashpw(os.environ["BLOG_PLAIN_PW"].encode(), bcrypt.gensalt(rounds=10, prefix=b"2b")).decode())'
    elif command -v htpasswd >/dev/null 2>&1; then
        # htpasswd 生成的是 $2y$ 前缀，Spring Security 同样支持
        htpasswd -bnBC 10 "" "$plain" | tr -d ':\n' | sed 's/^\$2y\$/\$2b\$/'
    else
        return 1
    fi
}

# --print-hash：只算哈希给别的脚本用（Docker 部署时宿主机算、容器里只写库），
# 所以这个分支必须放在「检查 mysql 客户端」之前，也不需要连数据库。
if [ "$USERNAME" = "--print-hash" ]; then
    PASSWORD="${BLOG_ACCOUNT_PASSWORD:-}"
    if [ -z "$PASSWORD" ]; then
        read -r -s -p "密码: " PASSWORD
        echo
    fi
    if [ -z "$PASSWORD" ]; then
        echo "错误：密码不能为空。" >&2
        exit 1
    fi
    if ! HASH_OUT="$(hash_password "$PASSWORD")"; then
        cat >&2 <<'MSG'
错误：没法生成 BCrypt 哈希，请先安装任一依赖再重试：
  - Python：pip install bcrypt
  - 或 apache2-utils：apt install apache2-utils
MSG
        exit 1
    fi
    printf '%s\n' "$HASH_OUT"
    exit 0
fi

if ! command -v mysql >/dev/null 2>&1; then
    echo "错误：找不到 mysql 客户端，请先安装 MySQL 客户端。" >&2
    exit 1
fi

if [ -z "$USERNAME" ]; then
    read -r -p "用户名: " USERNAME
fi
if [ -z "$USERNAME" ]; then
    echo "错误：用户名不能为空。" >&2
    exit 1
fi

if [ -z "$EMAIL" ]; then
    read -r -p "邮箱: " EMAIL
fi
if [ -z "$EMAIL" ]; then
    echo "错误：邮箱不能为空。" >&2
    exit 1
fi

# 读取密码：优先用环境变量，否则交互输入两次做校验
if [ -n "${BLOG_ACCOUNT_PASSWORD:-}" ]; then
    PASSWORD="$BLOG_ACCOUNT_PASSWORD"
else
    read -r -s -p "密码: " PASSWORD
    echo
    read -r -s -p "再输入一次密码: " PASSWORD_CONFIRM
    echo
    if [ "$PASSWORD" != "$PASSWORD_CONFIRM" ]; then
        echo "错误：两次输入的密码不一致。" >&2
        exit 1
    fi
fi

if [ -z "$PASSWORD" ]; then
    echo "错误：密码不能为空。" >&2
    exit 1
fi

if ! HASH="$(hash_password "$PASSWORD")"; then
    cat >&2 <<'MSG'
错误：没法生成 BCrypt 哈希，请先安装任一依赖再重试：
  - Python：pip install bcrypt
  - 或 apache2-utils：apt install apache2-utils
MSG
    exit 1
fi

# 转义 SQL 字符串里的反斜杠和单引号
sql_escape() {
    printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e "s/'/''/g"
}

SQL="INSERT INTO sys_user (username, email, password)
VALUES ('$(sql_escape "$USERNAME")', '$(sql_escape "$EMAIL")', '$(sql_escape "$HASH")')
ON DUPLICATE KEY UPDATE email = VALUES(email), password = VALUES(password);"

# 还没有站长时，把最早注册的账号提升为站长（前台首页只展示这个账号）
PROMOTE_SQL="SET @owner_exists := (SELECT COUNT(*) FROM sys_user WHERE role = 'owner');
SET @first_user_id := (SELECT MIN(id) FROM sys_user);
UPDATE sys_user
SET role = 'owner'
WHERE @owner_exists = 0
  AND @first_user_id IS NOT NULL
  AND id = @first_user_id;"

if [ -n "$DB_PASSWORD" ]; then
    export MYSQL_PWD="$DB_PASSWORD"
fi

mysql --default-character-set=utf8mb4 --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" --database="$DB_NAME" --execute="$SQL"

# 单独跑一次站长提升：老库补 role 列后也能靠这步确定站长
mysql --default-character-set=utf8mb4 --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" --database="$DB_NAME" --execute="$PROMOTE_SQL"

echo "账号已写入 $DB_NAME.sys_user：$USERNAME <$EMAIL>"

# 回读并校验哈希，确保密码真的能对应上
if command -v python3 >/dev/null 2>&1 && python3 -c 'import bcrypt' >/dev/null 2>&1; then
    STORED="$(mysql --default-character-set=utf8mb4 --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" \
        --database="$DB_NAME" --batch --skip-column-names \
        --execute="SELECT password FROM sys_user WHERE username = '$(sql_escape "$USERNAME")';")"
    BLOG_PLAIN_PW="$PASSWORD" BLOG_STORED_HASH="$STORED" python3 -c \
        'import bcrypt, os, sys; ok = bcrypt.checkpw(os.environ["BLOG_PLAIN_PW"].encode(), os.environ["BLOG_STORED_HASH"].encode()); print("密码校验：通过" if ok else "密码校验：失败"); sys.exit(0 if ok else 1)'
fi

echo "现在可以用这个账号登录后台了。"
