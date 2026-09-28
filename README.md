# Aitor Blog

一个前后端分离的个人博客系统：前台展示文章、画廊和个人简介，后台提供文章、分类、标签的可视化管理。

- 前台：文章列表（分类筛选 + 关键字搜索 + 分页）、文章详情（Markdown 渲染）、画廊、个人简介
- 后台：文章的增删改查与发布 / 撤回、分类管理、标签管理
- 鉴权：JWT（HS256）登录态，密码以 BCrypt 哈希存储，不存明文

## 技术栈

| 层次 | 技术 |
| --- | --- |
| 后端 | Java 21、Spring Boot 4.1.1、MyBatis-Plus 3.5.15、java-jwt 4.4.0、Lombok |
| 前端 | Vue 3.5、TypeScript 6、Vite 8、Element Plus 2.14、axios、vue-router 5、marked + DOMPurify |
| 数据库 | MySQL 8，库名 `blog_db`，字符集 `utf8mb4` / `utf8mb4_unicode_ci` |

## 目录结构

```
Aitor_Blog/
├── backend/                      # Spring Boot 后端，端口 8080
│   ├── Dockerfile                # 多阶段构建：Maven 编译 → JRE 21 运行（非 root）
│   └── src/main/                 # java/com/aitor/blog + resources
│       ├── java/.../auth/        # 登录：AuthController / AuthService / SysUser
│       ├── java/.../article/     # 文章、分类、标签（含 admin 侧接口）
│       ├── java/.../common/      # Result、BusinessException、JwtInterceptor、JwtUtil、PageParam
│       ├── java/.../config/      # SecurityConfig、WebMvcConfig、MybatisPlusConfig、JwtProperties
│       └── resources/
│           ├── application.yml            # 公共配置（端口、上传上限）
│           ├── application-docker.yml     # 容器部署：配置全部读环境变量
│           └── application-local.yml      # 本地开发配置（gitignore，不进仓库/镜像）
├── frontend/                     # Vue 3 前端
│   ├── Dockerfile                # 多阶段构建：Node 构建 → Nginx 托管
│   ├── nginx.conf                # SPA 回退 + /api 反代到后端容器
│   └── src/
│       ├── views/                # 前台页面：Home / Articles / ArticleDetail / Gallery / About / Login
│       ├── views/admin/          # 后台页面：文章、分类、标签、个人管理 + AdminLayout
│       ├── components/DockNav.vue
│       ├── router/index.ts
│       └── utils/request.ts      # axios 实例（baseURL = /api）
├── sql/                          # 建库与账号脚本
    ├── init_database.sql         # 全新部署：建库 + 建表（不含任何用户）
    ├── article_schema.sql        # 已有数据库的增量升级脚本
    ├── user_schema.sql           # 用户表增量升级（avatar / role 两列）
    └── init_account.sh           # 创建 / 重置登录账号（生成 BCrypt 哈希）
├── docker-compose.yml            # 服务器部署编排：MySQL + 后端 + 前端
├── deploy.sh                     # 一键部署 / 运维脚本（init / up / account / backup …）
└── .env.example                  # 部署配置模板（复制成 .env 后使用，.env 不入库）
```

## 环境要求

用 Docker 部署（推荐）：

- Docker Engine 20.10+ 与 `docker compose`（v2 插件；`deploy.sh` 也兼容老的 `docker-compose` 命令）
- 其余什么都不用装，JDK / Node / MySQL 都在容器里

本地开发：

- JDK 21+
- MySQL 8+
- Node.js `^22.18.0 || >=24.12.0`（见 `frontend/package.json` 的 `engines`）
- Maven 3.9+，或者直接用仓库自带的 `backend/mvnw`

## 快速开始

### 1. 初始化数据库

全新部署（创建 `blog_db`、`sys_user` 和文章模块全部表，脚本可重复执行）：

```bash
mysql -uroot -p < sql/init_database.sql
```

如果数据库已存在、只想补文章模块的表：

```bash
mysql -uroot -p < sql/article_schema.sql
```

两个脚本都**不会预置任何账号**，所以下一步必须创建你自己的登录账号。

### 2. 创建登录账号

后端用 `BCryptPasswordEncoder` 校验密码，库里存的是 BCrypt 哈希，因此不能手写 `INSERT`，请用脚本创建：

```bash
./sql/init_account.sh                            # 交互式输入用户名、邮箱、密码
./sql/init_account.sh aitor me@example.com       # 用户名和邮箱走参数，密码仍交互输入
```

连接信息可以用环境变量覆盖：`BLOG_DB_HOST` / `BLOG_DB_PORT` / `BLOG_DB_NAME` / `BLOG_DB_USER` / `BLOG_DB_PASSWORD`；设置 `BLOG_ACCOUNT_PASSWORD` 可以跳过密码交互，方便自动化部署。用户名已存在时会更新它的邮箱和密码，也可以当重置密码用。

脚本依赖 `mysql` 客户端，以及 `python3` + `bcrypt` 或 `apache2-utils`（`htpasswd`）中的任意一个。

### 3. 启动后端

先复制配置模板，再填入自己的真实值：

```bash
cd backend
cp src/main/resources/application-local.yml.template src/main/resources/application-local.yml
```

编辑 `application-local.yml`，至少修改这几项：

```yaml
spring:
    datasource:
        url: jdbc:mysql://localhost:3306/blog_db?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
        username: root              # 改成你的 MySQL 账号
        password: your-password     # 改成你的 MySQL 密码

jwt:
    secret-key: please input your JWT key   # 改成你自己的随机字符串
    expire-time: 86400000
```

`application-local.yml` 已被 `backend/.gitignore` 忽略，不会提交进仓库。启动服务：

```bash
./mvnw spring-boot:run
```

后端跑在 `http://localhost:8080`。

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

Vite 默认跑在 `http://localhost:5173`，并把 `/api` 开头的请求代理到 `http://localhost:8080`（转发时去掉 `/api` 前缀）。浏览器打开后进入 `/login` 用第 2 步创建的账号登录，登录成功即可访问 `/admin` 后台。

## 配置项

| 配置 | 位置 | 说明 |
| --- | --- | --- |
| `spring.datasource.*` | 本地：`backend/src/main/resources/application-local.yml`；Docker：`.env` + `application-docker.yml` | MySQL 连接信息，库名固定 `blog_db` |
| `jwt.secret-key` | 同上 | HS256 签名密钥，生产环境务必替换 |
| `jwt.expire-time` | 同上 | token 有效期（毫秒），模板默认 24 小时 |
| `server.port` | `backend/src/main/resources/application.yml` | 后端端口，默认 8080 |
| `blog.upload.dir` | 同上 | 上传文件（头像、正文配图）的落盘目录，默认 `./uploads`；容器里是 `/app/uploads`（数据卷） |
| `/api` 代理目标 | `frontend/vite.config.ts`（开发）、`frontend/nginx.conf`（生产） | 后端地址，默认 `http://localhost:8080` / `http://backend:8080` |
| `MYSQL_ROOT_PASSWORD`、`BLOG_JWT_SECRET`、`BLOG_HTTP_PORT` 等 | 仓库根 `.env`（模板 `.env.example`） | 只影响 Docker 部署，`./deploy.sh init` 会自动填入随机密钥 |

## 接口一览

所有接口返回统一结构 `{ code, message, data }`。业务失败时 HTTP 状态码仍是 200，靠 `code` 区分：400 参数或状态错误、401 未登录或密码错误、404 资源不存在、500 服务器异常。唯一例外是 token 校验失败，`JwtInterceptor` 会直接返回 HTTP 401。

公开接口（无需 token）：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/auth/login` | 登录，body 传 `username` 或 `email` + `password`，返回 token |
| GET | `/article/list` | 已发布文章列表，支持 `page` / `size` / `category`（分类 slug）/ `tag`（标签名）/ `keyword`；卡片带 `tags` |
| GET | `/article/detail/{id}` | 文章详情，含 Markdown 正文和标签 |
| GET | `/category/list` | 分类列表，`articleCount` 只统计已发布文章 |
| GET | `/tag/list` | 标签列表，只返回至少有一篇已发布文章的标签，`articleCount` 只统计已发布文章 |
| GET | `/site/owner` | 站长的用户名和头像（角色最高的账号，前台首页展示用，不含邮箱） |
| GET | `/uploads/**` | 上传的静态资源（头像 `/uploads/avatar/`、正文配图 `/uploads/article/`），由后端直接托管，不在鉴权白名单里 |

后台接口（需要 `Authorization: Bearer <token>`）：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/admin/article/list` | 文章列表（含草稿），支持状态 / 分类 / 关键字筛选 |
| GET | `/admin/article/{id}` | 文章详情，供编辑页回显 |
| POST | `/admin/article/create` | 新建文章，保存为草稿；`tags` 传标签名数组，库里没有的标签会自动创建 |
| POST | `/admin/article/update` | 更新文章，局部更新（只改传入的字段）；`tags` 为 null 表示不改标签，传数组则整体覆盖 |
| POST | `/admin/article/delete` | 删除：已发布→撤回为草稿，草稿→物理删除 |
| POST | `/admin/article/publish` | 发布文章 |
| POST | `/admin/article/unpublish` | 撤回为草稿 |
| POST | `/admin/article/force-delete` | 物理删除，仅允许删除草稿 |
| GET / POST | `/admin/category/list`、`/create`、`/update`、`/delete` | 分类管理 |
| GET / POST | `/admin/tag/list`、`/create`、`/update`、`/delete` | 标签管理 |
| GET | `/admin/user/profile` | 个人管理：当前登录用户的用户名 / 邮箱 / 头像 |
| POST | `/admin/user/profile/update` | 修改用户名或邮箱，只更新传入的字段，重名或格式错误返回 400 |
| POST | `/admin/user/avatar` | 上传头像（multipart，字段名 `file`，≤ 5MB，png / jpg / webp / gif） |
| POST | `/admin/upload/image` | 上传正文配图（multipart，字段名 `file`，≤ 5MB，png / jpg / webp / gif），返回 `url` 供编辑器写进 Markdown |

写操作统一使用 POST（项目里没有使用 PUT / DELETE 动词）。

## 数据库表

| 表 | 说明 |
| --- | --- |
| `sys_user` | 登录用户：`id` / `username`（唯一）/ `email` / `password`（BCrypt）/ `avatar`（头像地址，默认空）/ `role`（owner-站长、admin-管理员、user-普通用户）/ `create_time` |
| `article_category` | 文章分类：`name` / `slug`（唯一）/ `sort_order`，脚本预置前端、后端、绘画、生活四条 |
| `tag` | 标签：`name`（唯一） |
| `article` | 文章主表：`title` / `summary` / `content_markdown` / `status`(draft, published) / `published_at` / `reading_minutes` |
| `article_tag` | 文章与标签的关联表，复合主键，级联删除 |

外键约束：`article.author_id → sys_user.id`、`article.category_id → article_category.id` 都是 `ON DELETE RESTRICT`（分类下还有文章就删不掉）；`article_tag` 的两条外键是 `ON DELETE CASCADE`。

## 常用命令

```bash
# 后端
cd backend
./mvnw spring-boot:run      # 启动开发服务
./mvnw test                 # 运行测试
./mvnw clean package        # 打包可执行 jar

# 前端
cd frontend
npm run dev                 # 启动开发服务器
npm run build               # 类型检查 + 打包，产物在 dist/
npm run preview             # 预览打包结果
npm run format              # Prettier 格式化

# Docker（在仓库根目录）
./deploy.sh init            # 生成 .env（随机 MySQL 密码 + JWT 密钥）
./deploy.sh up              # 构建镜像并启动 MySQL / 后端 / 前端
./deploy.sh account         # 创建 / 重置后台登录账号
./deploy.sh status          # 容器状态 + 访问地址
./deploy.sh logs backend    # 跟日志（默认全部服务）
./deploy.sh update          # git pull + 重建 + 重启（数据卷保留）
./deploy.sh backup          # 备份数据库与上传文件
./deploy.sh down            # 停止并删除容器（数据卷保留）
./deploy.sh help            # 查看全部命令
```

## 生产部署

### Docker Compose 部署（推荐）

仓库自带镜像定义和编排文件，服务器只要有 Docker 就能一条龙跑起来：

| 文件 | 作用 |
| --- | --- |
| `docker-compose.yml` | 编排 MySQL 8.4 + 后端 + 前端（Nginx），含健康检查、启动顺序和数据卷 |
| `backend/Dockerfile` | 多阶段构建：Maven 编译 → JRE 21 运行，非 root 用户，配置全部走环境变量 |
| `backend/src/main/resources/application-docker.yml` | `docker` profile：数据库、JWT、上传目录从环境变量读取 |
| `frontend/Dockerfile` | 多阶段构建：Node 构建（含 `vue-tsc` 类型检查）→ Nginx 托管 |
| `frontend/nginx.conf` | SPA history 回退 + `/api` 反代到后端容器（去掉 `/api` 前缀） |
| `docker/certbot-deploy-hook.sh` | certbot 续期钩子：证书更新后重载前端容器里的 nginx |
| `docker/mysql-client.cnf` | 挂进 MySQL 容器的客户端配置，把 `mysql` / `mysqldump` 的字符集固定成 utf8mb4（不加会把中文种子数据写成乱码） |
| `deploy.sh` | 服务器一键脚本：`init` / `up` / `account` / `backup` / `logs` / `update` …；`up` 会等到所有容器健康检查通过才返回 |
| `.env.example` | 部署配置模板，复制成 `.env` 使用（`.env` 已被 gitignore 忽略） |

#### 1. 生成配置

```bash
git clone <你的仓库地址> Aitor_Blog && cd Aitor_Blog
./deploy.sh init        # 生成 .env：随机 MySQL 密码 + 随机 JWT 密钥，权限 600
```

`.env` 里通常只需要按需调整端口：

```ini
MYSQL_ROOT_PASSWORD=<自动生成的随机串>
BLOG_JWT_SECRET=<自动生成的随机串>
BLOG_HTTP_PORT=80        # 站点对外端口，80 被占用就改成 8081 之类
BLOG_DB_PORT=13306       # MySQL 映射到宿主机的端口（只绑 127.0.0.1）
BLOG_DB_NAME=blog_db
```

#### 2. 启动服务

```bash
./deploy.sh up          # 等价于 docker compose up -d --build
```

首次启动时 MySQL 会自动执行 `sql/init_database.sql` 建库建表（只在数据卷为空时执行一次，不预置账号），后端会等 MySQL 健康检查通过再启动。起来之后：

- 前台：`http://<服务器IP>/`（改了端口就是 `http://<服务器IP>:8081/`）
- 后台：`http://<服务器IP>/login`

#### 3. 创建后台登录账号

```bash
./deploy.sh account     # 交互式输入用户名 / 邮箱 / 密码
```

BCrypt 哈希在宿主机生成（依赖 `python3` + `bcrypt` 或 `apache2-utils` 的 `htpasswd`），容器里只执行一次 `INSERT`，明文密码不会进容器、也不进日志。把 `BLOG_ACCOUNT_USERNAME` / `BLOG_ACCOUNT_EMAIL` / `BLOG_ACCOUNT_PASSWORD` 写进 `.env` 后，这个命令可以全自动执行，适合放进部署流水线。

#### 4. 数据、备份与恢复

- 数据都在两个命名卷里：`aitor-blog_mysql-data`（数据库）和 `aitor-blog_uploads-data`（头像、正文配图）。`./deploy.sh down` 只删容器不动卷；只有 `docker compose down -v` 才会连数据一起删。
- `./deploy.sh update` 重建容器（代码更新）不会影响卷，文章、账号、图片都还在。
- `./deploy.sh backup` 在 `backups/<时间戳>/` 生成 `blog_db.sql`（整库导出）和 `uploads.tar.gz`，该目录已被 gitignore。

恢复：

```bash
# 数据库
docker compose exec -T mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD"' \
  < backups/<时间戳>/blog_db.sql

# 上传文件
docker compose run --rm --no-deps -T --user root \
  -v ./backups/<时间戳>:/backup --entrypoint sh backend \
  -c 'tar xzf /backup/uploads.tar.gz -C /app/uploads'
```

#### 5. 域名与 HTTPS

前端容器里的 Nginx 同时监听 80 和 443：**80 只做 ACME 校验和跳转，443 才是站点本体**，证书由宿主机上的 certbot 以 webroot 模式签发（宿主机装 Nginx 去抢 80 端口的 `certbot --nginx` 这条路在 Docker 部署里走不通，80 被容器占着）。

准备：域名 A 记录指向服务器 IP，安全组 / 防火墙放行 **80 和 443**。

##### 已经有证书的机器（日常更新）

```bash
docker compose up -d --build        # 或 ./deploy.sh up
```

证书在 `/etc/letsencrypt`，由 compose 只读挂载进前端容器，重建容器会自动读到。

##### 首次签发（服务器上还没有证书）

注意顺序：证书不存在时 nginx 起不来，所以先用一个临时容器占住 80 端口把证书签出来，再启动整套服务。

```bash
sudo mkdir -p /var/www/certbot

# 1) 临时用 nginx 容器只服务 ACME 校验目录（镜像可换成国内加速地址）
sudo docker run -d --rm --name acme-bootstrap \
  -p 80:80 -v /var/www/certbot:/usr/share/nginx/html:ro nginx:alpine

# 2) 签发（换成自己的域名和邮箱）
sudo certbot certonly --webroot -w /var/www/certbot \
  -d aitor.top -d www.aitor.top \
  --non-interactive --agree-tos --no-eff-email

# 3) 撤掉临时容器，启动整套服务
sudo docker rm -f acme-bootstrap
docker compose up -d --build
```

续期由 certbot 自己的定时任务完成（apt 装的 certbot 会带 `certbot.timer` 和 `/etc/cron.d/certbot`），它沿用同一份 webroot 配置，不需要停容器。装上 deploy hook 后，续期完会自动重载容器里的 nginx：

```bash
sudo chmod +x docker/certbot-deploy-hook.sh
sudo ln -sf "$PWD/docker/certbot-deploy-hook.sh" /etc/letsencrypt/renewal-hooks/deploy/
sudo certbot renew --dry-run        # 验证"签发 + 钩子"整条链
```

几点说明：

- 域名出现在 `frontend/nginx.conf` 的 `server_name`、80 端口的跳转目标和证书路径里，换域名三处一起改。
- 只有证书路径 `/etc/letsencrypt/live/<域名>/` 变了才需要动配置；续期本身只更新文件内容，`nginx -s reload` 即可生效。
- 证书和校验目录都是只读挂载，容器里的 nginx 不会去改它们。
- 前端资源路径和 `/api` 都是同源相对路径，换域名或协议不需要重新构建镜像。
- 想省掉 80 端口的跳转、让 HTTP 也能直接打开站点，把 `frontend/nginx.conf` 里 80 段的 `location /` 换成和 443 段一样的站点配置即可（不推荐，HTTPS 应该是唯一入口）。

#### 6. 常见问题

| 现象 | 原因与处理 |
| --- | --- |
| `bind: address already in use` | 80 端口被别的服务占用，改 `.env` 里的 `BLOG_HTTP_PORT` 后 `./deploy.sh up` |
| 页面能开，接口 502 | 后端还没起来或启动失败：`./deploy.sh logs backend`（常见是 `.env` 里密码/密钥没配） |
| 登录报"用户名或密码错误" | 还没建账号，先跑 `./deploy.sh account` |
| 老库升级后缺新增的列 | `./deploy.sh init-db sql/user_schema.sql`（脚本幂等，可重复执行） |
| 分类名 / 标签名显示成 `å‰ç«¯` 这类乱码 | 客户端字符集不是 utf8mb4：确认 `docker/mysql-client.cnf` 已按 compose 挂进 `/etc/mysql/conf.d/`，然后 `./deploy.sh down -v` + `./deploy.sh up` 重新初始化（已有数据要用 `SET NAMES utf8mb4` 的导出重灌） |
| 构建时卡在 `docker.io/docker/dockerfile` 超时 | 国内网络拉不到 Docker Hub 的 frontend 镜像：本项目已刻意不写 `# syntax=` 指令；若你自己新写的 Dockerfile 加了，删掉或给守护进程配镜像加速 / 代理 |
| CentOS / RHEL 上挂载 SQL 失败 | SELinux 限制：给 `docker-compose.yml` 里 `./sql/init_database.sql` 的挂载加上 `:ro,Z` |

### 不用 Docker 的手动部署

1. 前端 `npm run build`，把 `frontend/dist/` 交给 Nginx 托管；后端 `./mvnw clean package` 得到可执行 jar，用 `java -jar` 运行。
2. 生产环境要把 `/api` 反代到后端并去掉 `/api` 前缀（可直接参考 `frontend/nginx.conf`），否则前端请求会全部 404。
3. `jwt.secret-key`、MySQL 密码不要沿用开发环境的值，`application-local.yml` 也不建议打进镜像（`backend/.dockerignore` 已排除）。
4. 前端是 history 模式的 SPA，Nginx 需要配置回退（找不到文件时返回 `index.html`），否则直接刷新 `/articles/1` 这类地址会 404。
5. 上传目录要可写并持久化，否则图片会在重装服务后丢失。

## 说明与待办

- 画廊页（`/gallery`）和个人简介页（`/about`）目前是页面内静态数据，等后端接口就绪后再替换。
- `sys_user.role` 目前只用来决定前台首页展示谁：优先级 `owner > admin > user`，同优先级取 `id` 最小的（最早注册的账号）。权限还没做，后台接口仍然只校验"是否登录"，任何登录用户都能进后台。升/降站长直接改这一列即可，例如 `UPDATE sys_user SET role = 'owner' WHERE username = 'xxx';`。
- `article.author_id` 对齐 `sys_user.id` 使用**有符号** BIGINT，文章模块其余主键是 BIGINT UNSIGNED，新增外键列时注意类型不要写错。
- 头像和正文配图都存放在 `blog.upload.dir`（默认 `backend/uploads/avatar/` 与 `backend/uploads/article/`，已加入 `.gitignore`），数据库只存 `/uploads/xxx/yyy.png` 这样的相对地址：头像由前端加 `/api` 前缀访问，正文里的图片由 `frontend/src/utils/markdown.ts` 在渲染时补上 `/api` 前缀（正文里手写 `/uploads/...` 也能正常显示）；部署时该目录要可写并且要持久化，否则图片会在重建容器后丢失（Docker 部署已由 `aitor-blog_uploads-data` 数据卷处理）。
- `sql/init_database.sql` 与 `sql/article_schema.sql` 有一部分重复的建表语句（前者面向全新部署，后者面向文章模块的增量升级），修改表结构时两个文件都要同步；用户表所在的登录模块增量升级用 `sql/user_schema.sql`。
