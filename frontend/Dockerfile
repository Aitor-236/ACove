# ============================================================
# ACove 前端镜像：多阶段构建
#   1. builder  —— Node 里跑 npm ci + npm run build（含 vue-tsc 类型检查）
#   2. runtime  —— Nginx 托管 dist/，并把 /api 反代到后端容器
# 构建（在仓库根目录执行）：
#   docker build -t acove-frontend ./frontend
# 注意：刻意不写 `# syntax=docker/dockerfile:1`，理由见 backend/Dockerfile。
# ============================================================

# ---------- 1. 构建阶段 ----------
FROM node:22-alpine AS builder

WORKDIR /build

# 先装依赖：package.json / lock 没变时这层命中缓存
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---------- 2. 运行阶段 ----------
FROM nginx:alpine AS runtime

# nginx:alpine 的 entrypoint 会跑 /docker-entrypoint.d/10-listen-on-ipv6-by-default.sh，
# 它用 `apk manifest nginx` 拉官方 APKINDEX 来比对 default.conf 校验和；国内 ECS 访问
# dl-cdn.alpinelinux.org（Fastly）时 community 索引拉不下来，apk 又没超时，会让 entrypoint
# 永久阻塞、nginx 从不启动、容器一直 unhealthy。我们的 nginx 配置不依赖它，
# 不需要这个脚本；顺手把 apk 源换成阿里云镜像，避免以后 apk add 再卡住。
RUN rm -f /docker-entrypoint.d/10-listen-on-ipv6-by-default.sh \
    && sed -i 's|dl-cdn.alpinelinux.org|mirrors.aliyun.com|' /etc/apk/repositories

# 站点配置模板（SPA history 回退 + /api 反向代理）：
# 放进 nginx 镜像的 templates 目录，entrypoint 启动时用 envsubst 把 ${SITE_DOMAIN}
# 替换后生成 /etc/nginx/conf.d/default.conf，所以换域名只要改 .env 的 SITE_DOMAIN。
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=builder /build/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
