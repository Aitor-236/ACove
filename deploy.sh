#!/usr/bin/env bash
# ============================================================
# ACove：Docker 部署脚本（在服务器的仓库根目录执行）
#
# 最简流程：
#   ./deploy.sh init       # 生成 .env（随机 MySQL 密码 + JWT 密钥）
#   ./deploy.sh up         # 构建镜像并启动 MySQL / 后端 / 前端
#   ./deploy.sh account    # 创建后台登录账号
#   ./deploy.sh status     # 查看容器状态与访问地址
#
# 全部命令（./deploy.sh help 可随时查看）：
#   init [--force]            生成 .env，已存在时不覆盖（--force 覆盖）
#   build                     只构建镜像，不启动
#   up | start                构建并启动全部服务
#   down                      停止并删除容器（数据卷保留，数据不丢）
#   restart [服务名...]       重启服务，默认全部
#   logs [服务名...]          跟踪日志，默认全部服务
#   status | ps               查看容器状态
#   init-db [sql文件...]      导入 SQL，默认 sql/init_database.sql（脚本幂等）
#   account                   创建 / 重置登录账号
#   backup                    备份数据库与上传文件到 backups/<时间戳>/
#   update                    拉取最新代码后重建并启动
# ============================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

ENV_FILE="$ROOT_DIR/.env"
ENV_EXAMPLE="$ROOT_DIR/.env.example"
BACKUP_DIR="$ROOT_DIR/backups"

# ---------- 输出助手 ----------
log()  { printf '\033[1;32m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[警告]\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31m[错误]\033[0m %s\n' "$*" >&2; exit 1; }

# ---------- 基础检查 ----------
require_docker() {
    command -v docker >/dev/null 2>&1 \
        || die "找不到 docker 命令，请先安装 Docker（https://docs.docker.com/engine/install/）"
    docker info >/dev/null 2>&1 \
        || die "docker 守护进程不可用：确认服务已启动，且当前用户在 docker 组里（新加组需重新登录）"
    "${COMPOSE[@]}" version >/dev/null 2>&1 \
        || die "docker compose 不可用，请安装 compose 插件（docker-compose-plugin）"
}

# 优先用 docker compose 插件，兼容老的 docker-compose 命令
if docker compose version >/dev/null 2>&1; then
    COMPOSE=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
    COMPOSE=(docker-compose)
else
    COMPOSE=(docker compose)
fi

# 读取 .env，但**已经存在的环境变量优先**（和 docker compose 的规则一致）：
# 直接 `set -a; . .env` 会把命令行传入的变量覆盖成 .env 里的空值，
# 导致 `BLOG_ACCOUNT_PASSWORD=xxx ./deploy.sh account` 这类用法失效。
load_env() {
    [ -f "$ENV_FILE" ] || return 0
    local line key value
    while IFS= read -r line || [ -n "$line" ]; do
        case "$line" in
            ''|\#*) continue ;;
            *=*) ;;
            *) continue ;;
        esac
        key="${line%%=*}"
        value="${line#*=}"
        key="${key//[[:space:]]/}"
        value="${value%$'\r'}"
        # 去掉成对的引号（.env.example 里 JAVA_OPTS 那种带空格的值会加引号）
        case "$value" in
            \"*\") value="${value#\"}"; value="${value%\"}" ;;
            \'*\') value="${value#\'}"; value="${value%\'}" ;;
        esac
        [ -n "$key" ] || continue
        if [ -z "${!key:-}" ]; then
            export "$key=$value"
        fi
    done < "$ENV_FILE"
}

gen_secret() {
    if command -v openssl >/dev/null 2>&1; then
        openssl rand -hex 32
    else
        head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n'
    fi
}

db_name() { printf '%s' "${BLOG_DB_NAME:-blog_db}"; }

wait_for_mysql() {
    log "等待 MySQL 就绪（首次启动要初始化数据目录，可能需要 1 分钟）"
    local i
    for i in $(seq 1 90); do
        if "${COMPOSE[@]}" exec -T mysql \
            mysqladmin ping -h 127.0.0.1 --protocol=tcp --silent >/dev/null 2>&1; then
            return 0
        fi
        sleep 2
    done
    die "等待 MySQL 超时，用 './deploy.sh logs mysql' 查看日志"
}

# 等所有服务通过健康检查再返回：`compose up -d` 返回时容器只是 start 了，
# 前端 Nginx / 后端 Tomcat 还要几秒才真正可用，脚本这里等一下就绪状态，
# 免得部署完立刻访问拿到 502 或 connection reset。
wait_for_healthy() {
    local timeout="${1:-180}"
    local deadline=$((SECONDS + timeout))
    log "等待容器就绪（最多 ${timeout}s）"
    while [ "$SECONDS" -lt "$deadline" ]; do
        local ids pending=0 id status
        ids="$("${COMPOSE[@]}" ps -q 2>/dev/null || true)"
        if [ -z "$ids" ]; then
            sleep 2
            continue
        fi
        for id in $ids; do
            status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$id" 2>/dev/null || echo unknown)"
            case "$status" in
                healthy|running) ;;
                starting|created|unknown) pending=1 ;;
                *) die "有容器处于 $status 状态，用 './deploy.sh logs' 看日志" ;;
            esac
        done
        if [ "$pending" -eq 0 ]; then
            log "全部容器已就绪"
            return 0
        fi
        sleep 2
    done
    warn "等待 ${timeout}s 后仍有容器未就绪，继续往下走，请用 './deploy.sh logs' 检查"
    return 1
}

# 把 SQL 通过容器内的 mysql 客户端执行，宿主机不需要装 MySQL 客户端
mysql_exec() {
    local database="$1"
    local sql="$2"
    "${COMPOSE[@]}" exec -T mysql sh -c \
        'exec mysql --default-character-set=utf8mb4 --user=root --password="$MYSQL_ROOT_PASSWORD" --database="$1"' _ "$database" <<<"$sql"
}

# 转义 SQL 字符串里的反斜杠和单引号
sql_escape() {
    printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e "s/'/''/g"
}

# ---------- 命令实现 ----------
cmd_init() {
    local force="${1:-}"
    if [ -f "$ENV_FILE" ] && [ "$force" != "--force" ]; then
        log ".env 已存在，未做修改（要重新生成：./deploy.sh init --force）"
        return 0
    fi
    [ -f "$ENV_EXAMPLE" ] || die "找不到 .env.example，脚本可能不在仓库根目录"

    local mysql_password jwt_secret
    mysql_password="$(gen_secret)"
    jwt_secret="$(gen_secret)"

    sed -e "s|^MYSQL_ROOT_PASSWORD=.*|MYSQL_ROOT_PASSWORD=${mysql_password}|" \
        -e "s|^BLOG_JWT_SECRET=.*|BLOG_JWT_SECRET=${jwt_secret}|" \
        "$ENV_EXAMPLE" > "$ENV_FILE"
    chmod 600 "$ENV_FILE"

    log "已生成 .env：MySQL root 密码与 JWT 密钥都是随机值（权限 600）"
    warn "请按需修改 .env 里的 SITE_DOMAIN（站点域名）、BLOG_HTTP_PORT（默认 80）等信息"
}

cmd_build() {
    require_docker
    load_env
    log "构建镜像（首次会下载基础镜像和依赖，耗时较长）"
    "${COMPOSE[@]}" build
}

cmd_up() {
    [ -f "$ENV_FILE" ] || { warn ".env 不存在，先按 .env.example 生成一份"; cmd_init; }
    require_docker
    load_env
    log "构建镜像并启动服务"
    "${COMPOSE[@]}" up -d --build --remove-orphans
    wait_for_healthy
    cmd_status
}

cmd_down() {
    require_docker
    load_env
    log "停止并删除容器（数据卷 mysql-data / uploads-data 保留）"
    "${COMPOSE[@]}" down --remove-orphans
}

cmd_restart() {
    require_docker
    load_env
    "${COMPOSE[@]}" restart "$@"
    wait_for_healthy
    cmd_status
}

cmd_logs() {
    require_docker
    load_env
    "${COMPOSE[@]}" logs -f --tail=200 "$@"
}

cmd_status() {
    require_docker
    load_env
    "${COMPOSE[@]}" ps
    local port="${BLOG_HTTP_PORT:-80}"
    local base="http://<服务器IP>"
    [ "$port" = "80" ] || base="http://<服务器IP>:$port"
    printf '\n访问地址：%s/   后台：%s/login\n' "$base" "$base"
}

import_sql_file() {
    # 支持三种写法：绝对路径、仓库根相对路径（sql/x.sql）、文件名（x.sql）
    local file="$1"
    if [ ! -f "$file" ] && [ -f "$ROOT_DIR/$1" ]; then file="$ROOT_DIR/$1"; fi
    if [ ! -f "$file" ] && [ -f "$ROOT_DIR/sql/$1" ]; then file="$ROOT_DIR/sql/$1"; fi
    [ -f "$file" ] || die "找不到 SQL 文件：$1"
    log "导入 $(basename "$file")"
    "${COMPOSE[@]}" exec -T mysql \
        sh -c 'exec mysql --default-character-set=utf8mb4 --user=root --password="$MYSQL_ROOT_PASSWORD"' < "$file"
}

cmd_init_db() {
    require_docker
    load_env
    wait_for_mysql
    if [ "$#" -eq 0 ]; then
        import_sql_file "$ROOT_DIR/sql/init_database.sql"
    else
        local file
        for file in "$@"; do
            import_sql_file "$file"
        done
    fi
    log "SQL 导入完成"
}

cmd_account() {
    require_docker
    load_env
    [ -f "$ROOT_DIR/sql/init_account.sh" ] || die "找不到 sql/init_account.sh"

    local username="${BLOG_ACCOUNT_USERNAME:-}"
    local email="${BLOG_ACCOUNT_EMAIL:-}"
    local password="${BLOG_ACCOUNT_PASSWORD:-}"

    if [ -z "$username" ]; then
        read -r -p "用户名: " username
    fi
    [ -n "$username" ] || die "用户名不能为空"

    if [ -z "$email" ]; then
        read -r -p "邮箱: " email
    fi
    [ -n "$email" ] || die "邮箱不能为空"

    if [ -z "$password" ]; then
        read -r -s -p "密码: " password
        echo
        local confirm
        read -r -s -p "再输入一次密码: " confirm
        echo
        [ "$password" = "$confirm" ] || die "两次输入的密码不一致"
    fi
    [ -n "$password" ] || die "密码不能为空"

    # 哈希复用 sql/init_account.sh 里的实现（python3+bcrypt 或 htpasswd），
    # 保证和手动创建账号时算法一致；明文密码只在宿主机内存里出现。
    local hash
    hash="$(BLOG_ACCOUNT_PASSWORD="$password" bash "$ROOT_DIR/sql/init_account.sh" --print-hash)" \
        || die "生成 BCrypt 哈希失败，请安装 python3 + bcrypt 或 apache2-utils（htpasswd）"
    [ -n "$hash" ] || die "生成 BCrypt 哈希失败"

    wait_for_mysql
    mysql_exec "$(db_name)" "INSERT INTO sys_user (username, email, password)
VALUES ('$(sql_escape "$username")', '$(sql_escape "$email")', '$(sql_escape "$hash")')
ON DUPLICATE KEY UPDATE email = VALUES(email), password = VALUES(password);
SET @owner_exists := (SELECT COUNT(*) FROM sys_user WHERE role = 'owner');
SET @first_user_id := (SELECT MIN(id) FROM sys_user);
UPDATE sys_user SET role = 'owner'
WHERE @owner_exists = 0 AND @first_user_id IS NOT NULL AND id = @first_user_id;" \
        || die "写入账号失败，确认 MySQL 已启动、库表已初始化（./deploy.sh init-db）"

    log "账号已写入 $(db_name).sys_user：$username <$email>"
    local port="${BLOG_HTTP_PORT:-80}"
    if [ "$port" = "80" ]; then
        log "现在可以用它登录 http://<服务器IP>/login"
    else
        log "现在可以用它登录 http://<服务器IP>:$port/login"
    fi
}

cmd_backup() {
    require_docker
    load_env
    wait_for_mysql

    local dir="$BACKUP_DIR/$(date +%Y%m%d-%H%M%S)"
    mkdir -p "$dir"

    log "导出数据库 $(db_name)"
    "${COMPOSE[@]}" exec -T mysql sh -c \
        'exec mysqldump --default-character-set=utf8mb4 --single-transaction --quick --databases "$MYSQL_DATABASE" --user=root --password="$MYSQL_ROOT_PASSWORD"' \
        > "$dir/blog_db.sql"

    log "打包上传目录（头像、正文配图）"
    "${COMPOSE[@]}" run --rm --no-deps -T --user root \
        -v "$dir:/backup" --entrypoint sh backend \
        -c 'tar czf /backup/uploads.tar.gz -C /app/uploads .'

    log "备份完成：$dir"
    log "包含 blog_db.sql（整库导出）与 uploads.tar.gz（上传文件），恢复步骤见 README 生产部署章节"
}

cmd_update() {
    require_docker
    if [ -d "$ROOT_DIR/.git" ]; then
        log "拉取最新代码"
        git -C "$ROOT_DIR" pull --ff-only
    fi
    cmd_up
}

cmd_help() {
    # 打印文件开头的注释块（跳过 shebang，遇到第一行非注释即停止）
    awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "${BASH_SOURCE[0]}"
}

main() {
    local cmd="${1:-up}"
    shift || true
    case "$cmd" in
        init)             cmd_init "$@" ;;
        build)            cmd_build "$@" ;;
        up|start)         cmd_up "$@" ;;
        down|stop)        cmd_down "$@" ;;
        restart)          cmd_restart "$@" ;;
        logs|log)         cmd_logs "$@" ;;
        status|ps)        cmd_status "$@" ;;
        init-db|initdb)   cmd_init_db "$@" ;;
        account)          cmd_account "$@" ;;
        backup)           cmd_backup "$@" ;;
        update)           cmd_update "$@" ;;
        help|-h|--help)   cmd_help ;;
        *)                die "未知命令：$cmd（用 ./deploy.sh help 查看用法）" ;;
    esac
}

main "$@"
