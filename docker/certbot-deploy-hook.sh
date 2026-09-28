#!/bin/sh
# ============================================================
# certbot 续期成功后的 deploy hook：重载前端容器里的 nginx。
#
# 证书目录是以只读方式挂载进容器的（/etc/letsencrypt），certbot 续期时会原地
# 覆盖 live/ 下的软链与 archive/ 下的文件，所以只需要让 nginx reload 重新读取，
# 不必重建容器。
#
# 安装（服务器上执行一次，路径按仓库实际位置调整）：
#   sudo ln -sf "$PWD/docker/certbot-deploy-hook.sh" /etc/letsencrypt/renewal-hooks/deploy/
#   sudo chmod +x "$PWD/docker/certbot-deploy-hook.sh"
#
# 手动验证：
#   sudo certbot renew --dry-run
# ============================================================
set -eu

# 容器不存在（比如正在 down）时不算失败，下次起来自然会读新证书
if ! docker inspect -f '{{.State.Running}}' aitor-blog-frontend >/dev/null 2>&1; then
    echo "certbot hook: aitor-blog-frontend 不在运行，跳过 reload"
    exit 0
fi

if docker exec aitor-blog-frontend nginx -t >/dev/null 2>&1; then
    docker exec aitor-blog-frontend nginx -s reload
    echo "certbot hook: 已重载 aitor-blog-frontend 里的 nginx"
else
    # 新证书有问题导致配置不通过时，退回重启容器（至少让进程重新读一遍）
    echo "certbot hook: nginx -t 未通过，改为重启容器"
    docker restart aitor-blog-frontend >/dev/null
fi
