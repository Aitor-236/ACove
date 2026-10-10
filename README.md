# ACove

一个前后端分离、可直接自托管的博客系统：前台展示文章、画廊和个人简介，后台提供文章、分类、标签的可视化管理。仓库本身不带任何个人化配置，克隆下来按「生产部署」填好 `.env` 就能跑成你自己的站点。

- 前台：文章列表（分类筛选 + 关键字搜索 + 分页）、文章详情（Markdown 渲染）、画廊、个人简介；首页的网站名、头图和中间文字都可在后台改
- 后台：文章的增删改查与发布 / 撤回、分类管理、标签管理、个人管理、网站设置（网站名 / 首页头图 / 首页文字）、访问统计
- 鉴权：JWT（HS256）登录态，密码以 BCrypt 哈希存储，不存明文

## 技术栈

| 层次 | 技术 |
| --- | --- |
| 后端 | Java 21、Spring Boot 4.1.1、MyBatis-Plus 3.5.15、java-jwt 4.4.0、Lombok |
| 前端 | Vue 3.5、TypeScript 6、Vite 8、Element Plus 2.14、axios、vue-router 5、marked + DOMPurify + highlight.js |
| 数据库 | MySQL 8，库名 `blog_db`，字符集 `utf8mb4` / `utf8mb4_unicode_ci` |

## 目录结构

```
ACove/
├── backend/                      # Spring Boot 后端，端口 8080
│   ├── Dockerfile                # 多阶段构建：Maven 编译 → JRE 21 运行（非 root）
│   └── src/main/                 # java/com/acove/blog + resources
│       ├── java/.../auth/        # 登录 + 站点资料：AuthController / SysUser / SiteController（站长、站点设置）
│       ├── java/.../article/     # 文章、分类、标签、首页展示（含 admin 侧接口，表 home_article）
│       ├── java/.../visit/       # 访问统计：页面浏览上报 + 后台 PV / UV 概览与趋势
│       ├── java/.../todo/        # Todo 清单：前台只读列表 + 后台增删改 / 改状态 / 置顶
│       ├── java/.../project/     # 开源项目：前台只读列表 + 后台增删改（表 open_source_project）
│       ├── java/.../common/      # Result、BusinessException、JwtInterceptor、JwtUtil、PageParam
│       ├── java/.../config/      # SecurityConfig、WebMvcConfig、MybatisPlusConfig、JwtProperties
│       └── resources/
│           ├── application.yml            # 公共配置（端口、上传上限、Flyway）
│           ├── application-docker.yml     # 容器部署：配置全部读环境变量
│           ├── application-local.yml      # 本地开发配置（gitignore，不进仓库/镜像）
│           └── db/migration/              # Flyway 迁移脚本（V1__baseline.sql 起，建表的唯一来源）
├── frontend/                     # Vue 3 前端
│   ├── Dockerfile                # 多阶段构建：Node 构建 → Nginx 托管
│   ├── nginx.conf.template       # SPA 回退 + /api 反代（域名走 .env 的 SITE_DOMAIN）
│   └── src/
│       ├── views/                # 前台页面：Home / Articles / ArticleDetail / Gallery / Projects / About / Login
│       ├── views/admin/          # 后台页面：文章、分类、标签、待办清单、开源项目、个人管理、网站设置、访问统计 + AdminLayout
│       ├── components/DockNav.vue
│       ├── router/index.ts
│       └── utils/request.ts      # axios 实例（baseURL = /api）
├── sql/                          # 账号脚本 + 历史存档（表结构已交给 Flyway）
│   ├── init_account.sh           # 创建 / 重置登录账号（生成 BCrypt 哈希）
│   └── legacy/                   # Flyway 之前的建库 / 增量脚本，只读存档，不再被执行
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

表结构由后端启动时的 **Flyway** 自动创建，迁移脚本在 `backend/src/main/resources/db/migration/`，当前基线是 `V1__baseline.sql`。`sql/legacy/` 里是 Flyway 之前的建库 / 增量脚本，只作历史存档，`docker-compose.yml` 与 `deploy.sh` 都不会再执行它们。

- **全新部署（Docker）**：执行 `./deploy.sh up`。MySQL 只负责建出 `blog_db` 空库，后端启动时 Flyway 建出全部表（`sys_user`、文章模块、访问统计、站点设置）。
- **本地开发**：按下面的「启动后端」配好 `application-local.yml`，然后 `./mvnw spring-boot:run`，启动过程中 Flyway 会把表建好。
- **老库升级**：先 `./deploy.sh backup`，再 `./deploy.sh migrate`。库里已有表但没有 `flyway_schema_history` 时，Flyway 先写一条 version 0 的基线记录，再执行幂等的 `V1__baseline.sql`（全是 `CREATE TABLE IF NOT EXISTS` 与 `ON DUPLICATE KEY UPDATE id = id`，不缺表、不覆盖你改过的数据），最后继续 V2+。
- **查看状态**：`./deploy.sh info` 打印已应用 / 未应用的迁移版本。
- **改表结构**：新增 `V2__xxx.sql` 这样的迁移脚本，不要改已经应用过的迁移文件（校验和不匹配会导致启动失败），也不要再去改 `sql/legacy/` 里的老脚本。

以上流程都**不会预置任何账号**，所以下一步必须创建你自己的登录账号。

### 2. 创建登录账号

后端用 `BCryptPasswordEncoder` 校验密码，库里存的是 BCrypt 哈希，因此不能手写 `INSERT`，请用脚本创建：

```bash
./sql/init_account.sh                            # 交互式输入用户名、邮箱、密码
./sql/init_account.sh acove me@example.com       # 用户名和邮箱走参数，密码仍交互输入
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
| `blog.upload.dir` | 同上 | 上传文件（头像、正文配图、首页头图）的落盘目录，默认 `./uploads`；容器里是 `/app/uploads`（数据卷） |
| `/api` 代理目标 | `frontend/vite.config.ts`（开发）、`frontend/nginx.conf.template`（生产） | 后端地址，默认 `http://localhost:8080` / `http://backend:8080` |
| `MYSQL_ROOT_PASSWORD`、`BLOG_JWT_SECRET`、`BLOG_HTTP_PORT` 等 | 仓库根 `.env`（模板 `.env.example`） | 只影响 Docker 部署，`./deploy.sh init` 会自动填入随机密钥 |

## 接口一览

所有接口返回统一结构 `{ code, message, data }`。业务失败时 HTTP 状态码仍是 200，靠 `code` 区分：400 参数或状态错误、401 未登录或密码错误、404 资源不存在、500 服务器异常。唯一例外是 token 校验失败，`JwtInterceptor` 会直接返回 HTTP 401。

公开接口（无需 token）：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/auth/login` | 登录，body 传 `username` 或 `email` + `password`，返回 token |
| GET | `/article/list` | 已发布文章列表，支持 `page` / `size` / `category`（分类 slug）/ `tag`（标签名）/ `keyword`；卡片带 `tags` |
| GET | `/article/home` | 前台首页动态列表：后台「首页展示」挑好的已发布文章按顺序返回（没挑时回退最近三篇），同时返回已发布文章总数 `total` |
| GET | `/article/detail/{id}` | 文章详情，含 Markdown 正文和标签 |
| GET | `/category/list` | 分类列表，`articleCount` 只统计已发布文章 |
| GET | `/tag/list` | 标签列表，只返回至少有一篇已发布文章的标签，`articleCount` 只统计已发布文章 |
| GET | `/site/owner` | 站长的用户名和头像（角色最高的账号，前台首页展示用，不含邮箱） |
| GET | `/site/settings` | 前台站点设置：网站名、首页头图、首页中间的文字（匿名可访问） |
| GET | `/todo/list` | Todo 清单（前台只读）：一次性返回全部，置顶那条最先，其余按创建时间新 → 旧 |
| GET | `/project/list` | 开源项目（前台只读）：一次性返回全部，按创建时间新 → 旧；每条带名字 / 地址 / 介绍 / 日志 / 下一步 |
| POST | `/visit/report` | 前台页面浏览上报，每次切换页面调一次；访客标识由服务端 Cookie（`blog_vid`）维护，前端不用传参 |
| GET | `/uploads/**` | 上传的静态资源（头像 `/uploads/avatar/`、正文配图 `/uploads/article/`、首页头图 `/uploads/hero/`），由后端直接托管；`<img>` 请求不带 token，所以这条路径在 JwtInterceptor 白名单里 |

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
| GET | `/admin/home-article/list` | 首页展示的已选文章，数组顺序就是首页顺序；含当前是草稿的（带 `status`，草稿不会显示在首页） |
| POST | `/admin/home-article/save` | 整体覆盖首页展示选择，body 传 `articleIds` 数组（顺序即首页顺序），空数组表示清空 |
| GET / POST | `/admin/category/list`、`/create`、`/update`、`/delete` | 分类管理 |
| GET / POST | `/admin/tag/list`、`/create`、`/update`、`/delete` | 标签管理 |
| GET | `/admin/user/profile` | 个人管理：当前登录用户的用户名 / 邮箱 / 头像 |
| POST | `/admin/user/profile/update` | 修改用户名或邮箱，只更新传入的字段，重名或格式错误返回 400 |
| POST | `/admin/user/avatar` | 上传头像（multipart，字段名 `file`，≤ 5MB，png / jpg / webp / gif） |
| GET | `/admin/site/settings` | 网站设置：读取网站名 / 首页头图 / 首页中间的文字 |
| POST | `/admin/site/settings/update` | 保存网站名或首页文字（只更新传入的字段）；`heroImage` 传空串表示清除头图 |
| POST | `/admin/site/hero-image` | 上传首页头图（multipart，字段名 `file`，≤ 5MB，png / jpg / webp / gif） |
| POST | `/admin/upload/image` | 上传正文配图（multipart，字段名 `file`，≤ 5MB，png / jpg / webp / gif），返回 `url` 供编辑器写进 Markdown |
| GET | `/admin/visit/overview` | 访问统计概览：今日 / 昨日的 PV、UV，以及绝对增量与增幅百分比（昨日为 0 时增幅返回 `null`） |
| GET | `/admin/visit/trend` | 最近若干天的每日 PV / UV，`days` 默认 30、上限 90，按日期升序返回（没有数据的日子补 0） |
| GET | `/admin/todo/list` | Todo 清单（后台），和前台同一份数据，带置顶标记 |
| POST | `/admin/todo/create` | 新建 Todo：`title` 必填，`description` / `status` 可选（状态缺省 `todo`），新建的永远不是置顶 |
| POST | `/admin/todo/update` | 局部更新（body 带 `id`），只改传入的字段；置顶不走这里 |
| POST | `/admin/todo/delete` | 物理删除（query `id`），不存在返回 404 |
| POST | `/admin/todo/status` | 改状态（query `id` + `status`）：`todo`-酝酿中 / `doing`-打磨中 / `done`-已完成 |
| POST | `/admin/todo/pin` | 置顶（query `id`）：同一事务里先取消旧置顶，保证全表只有一条置顶 |
| POST | `/admin/todo/unpin` | 取消置顶（query `id`） |
| GET | `/admin/project/list` | 开源项目（后台），和前台同一份数据 |
| POST | `/admin/project/create` | 新建开源项目：`name` 必填，`url` / `description` / `updateLog` / `nextStep` 可选 |
| POST | `/admin/project/update` | 局部更新（body 带 `id`），只改传入的字段 |
| POST | `/admin/project/delete` | 物理删除（query `id`），不存在返回 404 |

写操作统一使用 POST（项目里没有使用 PUT / DELETE 动词）。

## 数据库表

| 表 | 说明 |
| --- | --- |
| `sys_user` | 登录用户：`id` / `username`（唯一）/ `email` / `password`（BCrypt）/ `avatar`（头像地址，默认空）/ `role`（owner-站长、admin-管理员、user-普通用户）/ `create_time` |
| `article_category` | 文章分类：`name` / `slug`（唯一）/ `sort_order`，迁移脚本 `V1__baseline.sql` 预置前端、后端、绘画、生活四条 |
| `tag` | 标签：`name`（唯一） |
| `article` | 文章主表：`title` / `summary` / `content_markdown` / `status`(draft, published) / `published_at` / `reading_minutes` |
| `article_tag` | 文章与标签的关联表，复合主键，级联删除 |
| `visit_daily_stat` | 每日访问汇总：`stat_date`（主键）/ `pv` / `uv`，一天一行，后台统计页的数据来源 |
| `visit_visitor` | 每日访客去重：`stat_date` + `visitor_key`（唯一键），访客当天第一次出现才让 UV 加一；`visitor_key` 是 Cookie 里 IP + User-Agent 的哈希，不存原始 IP |
| `site_setting` | 站点设置（单行，主键固定为 1）：`site_name`（网站名）/ `hero_image`（首页头图相对地址，空表示用默认底色）/ `hero_text`（首页中间文字，空表示回退成网站名） |
| `todo` | Todo 清单（站长个人一份）：`title` / `description`（可空）/ `status`(todo-酝酿中, doing-打磨中, done-已完成) / `is_pinned` / `created_at` / `updated_at`；辅助列 `pinned_flag` 是生成列，配合唯一索引 `uk_todo_pinned` 保证全表最多一条 `is_pinned = 1`（迁移脚本 `V2__create_todo.sql`） |
| `open_source_project` | 开源项目：`name` / `url`（项目地址）/ `description`（介绍）/ `update_log`（日志·最新更新内容）/ `next_step`（下一步·todo）/ `preview_image`（预览图相对地址，**暂未启用**，默认空串、前台不展示）/ `created_at` / `updated_at`（迁移脚本 `V3__create_open_source_project.sql`） |
| `home_article` | 首页展示文章：`article_id`（主键，关联 `article.id`，级联删除）/ `sort_order`（越小越靠前）/ `created_at`；后台「首页展示」页维护，前台 `GET /article/home` 按它排序（迁移脚本 `V4__create_home_article.sql`） |

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
./deploy.sh migrate         # 重建后端触发 Flyway 迁移，并打印迁移记录
./deploy.sh info            # 查看已应用 / 未应用的迁移版本
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
| `frontend/nginx.conf.template` | SPA history 回退 + `/api` 反代到后端容器（去掉 `/api` 前缀）；启动时由 nginx 的 envsubst 把 `${SITE_DOMAIN}` 替换成 `.env` 里的域名 |
| `docker/certbot-deploy-hook.sh` | certbot 续期钩子：证书更新后重载前端容器里的 nginx |
| `docker/mysql-client.cnf` | 挂进 MySQL 容器的客户端配置，把 `mysql` / `mysqldump` 的字符集固定成 utf8mb4（不加会把中文种子数据写成乱码） |
| `deploy.sh` | 服务器一键脚本：`init` / `up` / `account` / `backup` / `logs` / `update` …；`up` 会等到所有容器健康检查通过才返回 |
| `.env.example` | 部署配置模板，复制成 `.env` 使用（`.env` 已被 gitignore 忽略） |

#### 1. 生成配置

```bash
git clone <你的仓库地址> ACove && cd ACove
./deploy.sh init        # 生成 .env：随机 MySQL 密码 + 随机 JWT 密钥，权限 600
```

`.env` 里通常只需要按需调整端口：

```ini
MYSQL_ROOT_PASSWORD=<自动生成的随机串>
BLOG_JWT_SECRET=<自动生成的随机串>
BLOG_HTTP_PORT=80        # 站点对外端口，80 被占用就改成 8081 之类
BLOG_DB_PORT=13306       # MySQL 映射到宿主机的端口（只绑 127.0.0.1）
BLOG_DB_NAME=blog_db
SITE_DOMAIN=acove.top    # 站点主域名：nginx 的 server_name / HTTPS 跳转 / 证书路径都用它
```

#### 2. 启动服务

```bash
./deploy.sh up          # 等价于 docker compose up -d --build
```

首次启动时 MySQL 建出 `.env` 里 `BLOG_DB_NAME` 指定的空库，随后后端启动、由 Flyway 应用 `db/migration` 里的迁移把表建出来（不预置账号）。想看迁移状态用 `./deploy.sh info`，想手动触发一次用 `./deploy.sh migrate`。起来之后：

- 前台：`http://<服务器IP>/`（改了端口就是 `http://<服务器IP>:8081/`）
- 后台：`http://<服务器IP>/login`

#### 3. 创建后台登录账号

```bash
./deploy.sh account     # 交互式输入用户名 / 邮箱 / 密码
```

BCrypt 哈希在宿主机生成（依赖 `python3` + `bcrypt` 或 `apache2-utils` 的 `htpasswd`），容器里只执行一次 `INSERT`，明文密码不会进容器、也不进日志。把 `BLOG_ACCOUNT_USERNAME` / `BLOG_ACCOUNT_EMAIL` / `BLOG_ACCOUNT_PASSWORD` 写进 `.env` 后，这个命令可以全自动执行，适合放进部署流水线。

#### 4. 数据、备份与恢复

- 数据都在两个命名卷里：`acove_mysql-data`（数据库）和 `acove_uploads-data`（头像、正文配图、首页头图）。`./deploy.sh down` 只删容器不动卷；只有 `docker compose down -v` 才会连数据一起删。
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
  -d "$SITE_DOMAIN" -d "www.$SITE_DOMAIN" \
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

- 域名只在一处配置：`.env` 里的 `SITE_DOMAIN`。前端 nginx 的配置文件是一份模板（`frontend/nginx.conf.template`），容器启动时由 nginx 官方镜像的 entrypoint 用 `envsubst` 把 `${SITE_DOMAIN}` 替换成你的域名，所以换域名只要改 `.env` 再 `./deploy.sh up`，不用再动 nginx 配置。
- 只有证书路径 `/etc/letsencrypt/live/<域名>/` 变了才需要动配置；续期本身只更新文件内容，`nginx -s reload` 即可生效。
- 证书和校验目录都是只读挂载，容器里的 nginx 不会去改它们。
- 前端资源路径和 `/api` 都是同源相对路径，换域名或协议不需要重新构建镜像。
- 想省掉 80 端口的跳转、让 HTTP 也能直接打开站点，把 `frontend/nginx.conf.template` 里 80 段的 `location /` 换成和 443 段一样的站点配置即可（不推荐，HTTPS 应该是唯一入口）。

#### 6. 常见问题

| 现象 | 原因与处理 |
| --- | --- |
| `bind: address already in use` | 80 端口被别的服务占用，改 `.env` 里的 `BLOG_HTTP_PORT` 后 `./deploy.sh up` |
| 页面能开，接口 502 | 后端还没起来或启动失败：`./deploy.sh logs backend`（常见是 `.env` 里密码/密钥没配） |
| 登录报"用户名或密码错误" | 还没建账号，先跑 `./deploy.sh account` |
| 老库升级后缺表 / 缺列 | 表结构归 Flyway 管：`./deploy.sh migrate` 会把迁移脚本里的内容补齐；要加东西就新增 `backend/src/main/resources/db/migration/Vn__xxx.sql`，别手改库 |
| 后端容器反复重启、迁移失败 | `./deploy.sh logs backend` 看 Flyway 报错，修好迁移脚本后重跑 `./deploy.sh migrate`；若 `flyway_schema_history` 里留下 `success = 0` 的记录，先按下面「清掉失败的迁移记录」删掉再重跑 |
| 启动报 `Migration checksum mismatch` | 已经应用过的迁移文件被改过：把文件恢复原样，或新增一个迁移脚本去写这次想做的变更。`clean` 已在 `application.yml` 里禁用，不要清库重来 |
| 分类名 / 标签名显示成 `å‰ç«¯` 这类乱码 | 建表已由 JDBC（`characterEncoding=utf-8`）负责，出乱码的通常是 `./deploy.sh account` 或手工导入 SQL：确认 `docker/mysql-client.cnf` 已按 compose 挂进 `/etc/mysql/conf.d/`，已有乱码数据要用 `SET NAMES utf8mb4` 的导出重灌 |
| 构建时卡在 `docker.io/docker/dockerfile` 超时 | 国内网络拉不到 Docker Hub 的 frontend 镜像：本项目已刻意不写 `# syntax=` 指令；若你自己新写的 Dockerfile 加了，删掉或给守护进程配镜像加速 / 代理 |

清掉失败的迁移记录（只在 `./deploy.sh info` 里看到某条 `success = 0` 时用；库名不是 `blog_db` 就换成你自己的）：

```bash
docker compose exec -T mysql sh -c \
  'exec mysql --default-character-set=utf8mb4 --user=root --password="$MYSQL_ROOT_PASSWORD" --database=blog_db -e "DELETE FROM flyway_schema_history WHERE success = 0;"'
```

### 不用 Docker 的手动部署

1. 前端 `npm run build`，把 `frontend/dist/` 交给 Nginx 托管；后端 `./mvnw clean package` 得到可执行 jar，用 `java -jar` 运行。
2. 生产环境要把 `/api` 反代到后端并去掉 `/api` 前缀（可直接参考 `frontend/nginx.conf.template`），否则前端请求会全部 404。
3. `jwt.secret-key`、MySQL 密码不要沿用开发环境的值，`application-local.yml` 也不建议打进镜像（`backend/.dockerignore` 已排除）。
4. 前端是 history 模式的 SPA，Nginx 需要配置回退（找不到文件时返回 `index.html`），否则直接刷新 `/articles/1` 这类地址会 404。
5. 上传目录要可写并持久化，否则图片会在重装服务后丢失。

## 说明与待办

- 画廊页（`/gallery`）和个人简介页（`/about`）目前是页面内静态数据，等后端接口就绪后再替换。开源项目页（`/projects`）已由后端驱动：数据来自 `open_source_project` 表，后台「开源项目 → 项目列表」维护，每个项目一张卡片。
- 首页动态列表由后台「内容管理 → 首页展示」维护（表 `home_article`）：挑中的已发布文章按拖拽顺序展示，数量不限；一篇都没挑时回退到最近三篇已发布文章。选择里会保留被取消发布的文章（前台自动隐藏），重新发布后回到原来的位置。
- 开源项目的预览图（`preview_image` 列）**暂时留空、不启用**：表里先建好列，前台和后台都还没给它留位置，等后期做预览图上传时再补 UI。
- `sys_user.role` 目前只用来决定前台首页展示谁：优先级 `owner > admin > user`，同优先级取 `id` 最小的（最早注册的账号）。权限还没做，后台接口仍然只校验"是否登录"，任何登录用户都能进后台。升/降站长直接改这一列即可，例如 `UPDATE sys_user SET role = 'owner' WHERE username = 'xxx';`。
- `article.author_id` 对齐 `sys_user.id` 使用**有符号** BIGINT，文章模块其余主键是 BIGINT UNSIGNED，新增外键列时注意类型不要写错。
- 头像、正文配图和首页头图都存放在 `blog.upload.dir`（默认 `backend/uploads/avatar/`、`backend/uploads/article/` 与 `backend/uploads/hero/`，已加入 `.gitignore`），数据库只存 `/uploads/xxx/yyy.png` 这样的相对地址：头像和头图由前端加 `/api` 前缀访问，正文里的图片由 `frontend/src/utils/markdown.ts` 在渲染时补上 `/api` 前缀（正文里手写 `/uploads/...` 也能正常显示）；部署时该目录要可写并且要持久化，否则图片会在重建容器后丢失（Docker 部署已由 `acove_uploads-data` 数据卷处理）。
- 数据库 schema 的唯一来源是 `backend/src/main/resources/db/migration/`：`V1__baseline.sql` 是接入 Flyway 时的基线（幂等，已有表的老库首次启动会重跑一遍空操作），`V2__create_todo.sql` 建 Todo 表、`V3__create_open_source_project.sql` 建开源项目表、`V4__create_home_article.sql` 建首页展示表，以后改表结构只需新增下一个 `Vn__xxx.sql`。老库连上 Flyway 后会多出一张 `flyway_schema_history` 记录表，`./deploy.sh info` 与 `./deploy.sh migrate` 就是围绕它工作的。
- `V1__baseline.sql` 的种子数据（四个分类 + `site_setting` 默认行）用 `ON DUPLICATE KEY UPDATE id = id` 写成幂等且不覆盖已有值，所以老库重新执行它不会改掉你在后台改过的分类名、排序或站点设置。
- 网站名 / 首页头图 / 首页中间文字都存在 `site_setting` 单行表里，后台「站点设置 → 网站设置」页维护；字段长度等约束与 `V1__baseline.sql` 的列定义保持一致。
- 后台「文章管理 → 编辑文章」的正文编辑器是 Typodown（`@vemonet/typodown`，CodeMirror 6 实时预览：光标所在结构显示原始标记、移开即渲染），包在 `frontend/src/components/MarkdownEditor.vue` 里，主题写在 `frontend/src/styles/typodown.css`；它只有 0.0.x、单人维护、也没有给外部加扩展的入口，`::: code-group` 在编辑器里只能按源码文本显示。**待办**：以后自己用 CodeMirror 6 写一套替换它（含把 `::: code-group` 在编辑器里也渲染成标签页），新项目占位在 `~/Desktop/AEditor`（技术栈待定）。
- 文章编辑页是「先看后改」：打开已有文章先是只读的渲染预览（正文走前台那套 Markdown），点右上角「编辑」才进入可写状态。**编辑态下只有标题 / 摘要 / 正文会自动保存**（停下来约 1s 防抖落库，失败自动重试 2 次，顶部显示「正在保存 / 已保存 15:32 / 保存失败 · 重试」），**分类和标签不自动保存**，改完要点「保存」；「完成」回到预览时会先把待保存的文本 flush 掉。首页展示页同理：增删 / 拖拽约 300ms 后自动整体保存，没有保存按钮。两处共用 `frontend/src/composables/useAutoSave.ts`（防抖、单飞串行、重试、离开前 flush），自动保存的请求带 `silent: true`，失败不弹 toast。
