# 剩余项目缺陷统一验收

日期：2026-10-27（Asia/Shanghai）

**结论：可合并到 develop。** 六项修复已分别提交，集成后的构建、真实 MySQL、HTTP/HTTPS 和浏览器检查通过，没有遗留阻塞项。

起点为 `develop@f1bb8b6`，包含用户已经完成的文章编辑器身份重置与取消发布状态保留修复。本轮没有重新实现这两项，现有回归继续通过。验收业务代码位于临时集成分支 `fix/project-defects-integration@d13dc48`；之后的验收记录提交仅补测试适配和本报告。

## 子任务与独立分支

| 原问题 | 分支 | 最终业务提交 | 结果 |
| --- | --- | --- | --- |
| 3：改密后旧登录仍有效 | `fix/session-revocation` | `4258955` | V10 增加用户会话版本；改密、账号重置递增版本；拒绝历史、无版本及已删除用户令牌；前端重新登录保留返回地址 |
| 4：分享/搜索仍显示 ACove | `fix/public-page-metadata` | `b1af6ce` | Nginx SSI 在响应 HTML 时注入自定义站名、详情标题/摘要、canonical、OG/Twitter；草稿不泄露，缺失/私有页面 noindex |
| 5：同名分类选错 | `fix/article-category-id` | `d4430ed` | 编辑器按 ID 保存，列表按 slug 筛选；旧名称请求兼容，歧义明确拒绝；同名选项显示 slug |
| 6：旧请求覆盖当前结果 | `fix/article-request-order` | `6b1dc19` | 列表、文章详情、图库详情丢弃过期成功/失败响应，卸载后不更新页面 |
| 7：列表返回丢失状态 | `fix/article-list-navigation` | `fac0b4b` | URL 保存搜索、分类、标签和页码；历史导航同步状态；按 URL 恢复滚动，列表先渲染再滚动；相同搜索可重试 |
| 8：登录页品牌写死 | `fix/login-site-branding` | `0adbb45` | 共享站点设置驱动网站名与首字符 Logo，慢请求/失败保留已有品牌 |

列表分支包含追加的 `fc6e74d`（路由滚动归属、相同搜索重试）与 `fac0b4b`（加载占位结束后恢复滚动），均在统一验收发现问题后补充测试并重新验证。

## 自动化与构建

- `python3 sql/tests/test_email_uniqueness.py`：最终集成代码上后端 **97 项通过，0 失败、0 跳过**。覆盖真实 MySQL 8.4、Flyway V1–V10、V6 老库升级、原子改密及令牌版本保护。
- 同一脚本验证两套账号入口（独立脚本、`deploy.sh account`）的创建、重置、邮箱冲突回滚、并发、SQL mode 及 V7/V10 缺失拒绝；全部通过，临时项目自动销毁。
- `npm run build`：TypeScript 与 Vite 生产构建通过。
- 实际 `backend/Dockerfile`、`frontend/Dockerfile` 镜像构建与启动通过；没有使用模拟后端替代生产服务。
- 前端逐文件运行真实 SFC / Vue Router 回归，**29 项全部通过**：

| 测试文件 | 项数 |
| --- | ---: |
| `article-editor-state.test.mjs` | 8 |
| `article-list-navigation.test.mjs` | 6 |
| `article-list-scroll.test.mjs` | 1 |
| `login-site-branding.test.mjs` | 5 |
| `page-metadata.test.mjs` | 1 |
| `public-request-order.test.mjs` | 3 |
| `session-revocation.test.mjs` | 5 |

请求数据测试使用独立 head 副作用替身，元数据另有真实 App/详情/共享状态的 DOM head 回归；滚动专项在浏览器模式下运行真实路由 `scrollBehavior`。

## 真实 API、数据库与 Nginx

在专用 MySQL 数据库 `acove_defect_qa`、独立 Docker 网络及 HTTP 18081 / HTTPS 18443 端口执行。没有修改现有业务数据库或正在运行的其他服务。

随附 `api_regression.py` 的 **25 项检查通过**，包括：

- 错误旧密码不撤销会话；成功改密后旧 token 返回 HTTP 401，新密码可登录，其他用户会话不受影响；历史无版本、无过期时间、已删除用户令牌拒绝访问。
- 两个同名、不同 slug 的分类通过 API 创建；文章 `category_id`、`author_id` 直接查库核对。按 ID 切换、局部更新保留分类、非法 ID 和歧义名称拒绝、旧 slug 请求兼容。
- 直接 HTTP 获取首页、文章和图库 HTML，**不执行 JavaScript**，仍有自定义标题/摘要；带 `<`、`&`、引号的内容正确转义，只有一个 title。
- 文章分享 canonical 保留原详情路径并去掉跟踪参数；OG 类型、站名与绝对图片地址正确。证明 SSI 子请求获得了原始文章 URI，而不是内部片段 URI。
- 草稿、缺失文章/图库、未知路径、登录页和后台均 noindex；草稿标题与摘要不出现在 HTML 中；内部 SSI location 直接请求返回 404。
- 准备 18 篇真实已发布文章验证第三页搜索和返回行为。

额外通过的部署检查：

- HTTP、HTTPS、主机三份 Nginx 配置均通过 `nginx -t`。
- HTTPS 使用临时自签证书；实际响应保留详情路径、自定义元数据及非默认端口 canonical；HTTP 正确跳转指定 HTTPS 域名与端口。
- 临时后端停止后，HTML 仍返回单个降级标题、noindex 和完整 SPA 脚本；后端已恢复并完成后续检查。

## 真实浏览器

使用独立 Playwright Chromium 会话和生产镜像验证：

1. 登录页展示后台自定义站名和 `Q` Logo，标签标题同步，robots 为 noindex。
2. 搜索 `QA状态恢复` 的第三页进入文章，再点击“返回”：URL 保持 `keyword` / `page=3`，页码为 `第 3 / 3 页`，滚动位置从 **300px 恢复至 300px**。
3. 点击同名分类的文章卡片徽标，URL 精确进入 `category=qa-category-second`；搜索条件保留，页码回到第一页。
4. 后台文章编辑器正确回填第二个分类；下拉显示 `QA同名分类（qa-category-first）` 与 `QA同名分类（qa-category-second）`，后者选中。
5. 临时账号再次改密后，浏览器旧令牌受到 HTTP 401 拒绝，本地 token 清除并进入 `/login?redirect=/admin/profile`，自定义登录品牌仍正确。

真实浏览器最初发现“加载占位限制页面高度导致恢复过早”，修复后重新构建镜像并重测通过。会话撤销用例出现的两条资源 401 控制台记录是预期认证失败，没有未处理的脚本异常。

## 兼容与交付边界

- 部署必须应用 **V10**；历史 JWT 会统一要求重新登录。账号初始化/重置脚本也要求 V10，不能把旧脚本与新后端混用。
- 旧 `categoryName` 请求保留，但同名歧义返回业务 400；新前端使用 `categoryId`。响应新增 `categoryId/categorySlug`，原有字段保留。
- SSI 是生产 Nginx 的能力；Vite 开发/预览服务器通过客户端脚本更新元数据，不承担本次无 JavaScript 分享响应验收。
- 缺失详情/未知页面仍沿用 SPA 的 HTTP 200 外层响应，通过 noindex 防止收录；没有在本轮引入全站服务器渲染或改造错误页状态码。
- 本轮不扩展角色分权。QA 规范已明确本项目目前只有管理员账号。
- 未请求外部搜索引擎或分享平台刷新缓存；本轮验证的是当前服务实际输出的新 HTML。
- 只进行本地合并到 `develop`，不推送、不部署线上，也不合并 `main`。

## 清理与分支保留

已关闭专用浏览器，移除本次四个临时容器、测试网络、验收镜像标签及临时凭据/证书。原有 `napcat`、`astrbot` 服务保持运行。截图/快照/控制台原始产物移至 `/tmp/acove-defect-browser-artifacts-20261027`，仓库仅保留回归脚本和本报告。

所有开发分支保留，未删除用户已有分支。合并后可考虑清理的非活跃本地分支：`fix/article-request-order`、`fix/article-list-navigation`、`fix/public-page-metadata`、`fix/project-defects-integration`；用户先前已合并的 `fix/article-editor-state` 也可单独确认。仍被开发 worktree 使用的分支不纳入直接删除操作。删除任何开发分支前需用户确认；远端分支删除另行确认。
