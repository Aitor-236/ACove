# ACove 缩略图修复验收 · 2026-10-21

结论：本轮缩略图 P1 阻塞已消除，修复工作区通过本轮合并验收，可提交后进入合并流程；不代表生产环境已具备上线条件。

## 状态与改动

- 分支：`fix/image-thumbnails`，从本地 `develop`（`2feb63e`）创建。代码尚未提交；没有合并、推送、生产部署或删除既有分支。
- `main` 为 `c8745f6`，是当前 HEAD 的祖先。`git merge-tree --write-tree main develop` 无冲突，树为 `aac69e56f4266dad55c52853516073abdb93d595`；这是集成基线检查，修复本身以当前工作区构建并运行。没有生成修复提交或实际合并树；后续目标分支若变化，需重新检查。
- WebP 编码改用 OutputStream，避免 Thumbnailator 将实际输出写到另一条 `.tmp.webp` 路径；流关闭后检查非空、WebP 解码、长边尺寸，再同目录原子替换目标，失败清理临时文件。
- 检查已有派生图的格式、完整解码及长边尺寸，修复空文件、截断／损坏文件、错误格式和尺寸；有效文件保持不变。部分成功后失败时，已完成数量仍正确计入汇总。
- 另发现小尺寸索引色 GIF 直接编码 WebP 会变黑，已强制使用保留透明度的 ARGB 缓冲区。首帧、颜色和透明度均有真实编码回归。
- 离线补图排除 `.acove-thumb-` 保留前缀及派生图，先清理历史临时文件与误生成派生图；输出新建、修复、清理和失败数，失败退出码为 1。
- 保留原图字节、480/1600 长边上限、不放大小图及失败回退；上传 API、派生图 URL、数据库迁移不变。README 说明补图须暂停上传、停止后端并备份目录。

## 自动化与构建

- Java 21：`mvn -o -f backend/pom.xml package -Dmaven-surefire-plugin.version=3.5.5`，73 个测试，70 通过，3 个既有 MySQL 显式启用测试跳过，0 失败／错误。新增 17 个测试用例，覆盖真实编码、GIF 首帧／颜色／透明度、坏图修复、重复补图、历史清理、编码器不可用、移动失败、部分成功统计、上传回滚、单图／整库删除及文章转换失败警告。Surefire 配置 headless，避免无可用 DISPLAY 的测试宿主机触发 X11 错误。
- `npm run build`（含 vue-tsc）、`docker compose config --quiet`、`bash -n deploy.sh`、`git diff --check` 通过。
- 使用仓库原有完整 Dockerfile 构建前后端镜像，未用宿主机 jar/dist 替代镜像构建。
- 后端镜像：`acove-backend:thumbnail-qa`，`sha256:7a61a350159498d5ab6cc289be3ebe6118d9b414486e82fa00563134fe73ace2`。
- 前端镜像：`acove-frontend:thumbnail-qa`，`sha256:2e09c60edcfa5d70408901bf12ef9c08fd68039d1251cf1e5eb12c2580c85db1`。

## 容器与 API 证据

使用上述镜像、独立 MySQL 8.4.11、独立网络，经 Nginx 的本机 `28082` 端口访问；默认非 root 后端启动成功，空库 Flyway V1–V8 全部迁移成功。未连接实际业务库。

- 八个匿名读取接口 article/list、article/home、gallery/list、category/list、site/owner、site/settings、todo/list、project/list 均 HTTP 200 / code 200。匿名 admin/gallery/list 为 HTTP 401 / code 401。
- PNG 2400×1800、JPEG 1800×2400、两帧 GIF 100×80、WebP 600×400，分别通过图库和文章上传接口成功写入。
- 每份原图的字节与上传内容相同；共 16 个派生图响应 HTTP 200，Content-Type 为 image/webp，Content-Length 大于 0 且与实际响应体一致。Pillow 独立解码确认格式、长边与颜色正确，GIF 为红色首帧；缓存头含 immutable。
- 图库创建与详情、文章创建／发布／详情／更新通过；数据库确认 article.author_id 为测试用户 ID。
- 向不存在图库上传有效 PNG 返回 HTTP 200 / code 404，原图与两个派生图均回滚，图库目录保持 12 个文件且无临时残留。
- 停止后端后，注入 2 个无效派生图、删除 1 个派生图、添加 4 个旧临时文件；实际镜像补图输出：`8 originals scanned, 1 variants created, 2 variants repaired, 4 temporary files cleaned, 0 failures`。独立校验修复结果、原图及有效派生图内容保持不变。
- 第二次补图：扫描 8，其他计数全部为 0。注入损坏原图后扫描 9、失败 1，进程退出码 1；随后移除故障注入文件。
- 仅为宿主机独立核验，测试目录曾开放读写权限；生产运行权限和代码配置未改动。

明细：[API 日志](/home/aitor/.codex/visualizations/2026/10/21/01a15d79-5adb-7ca0-8447-1e9160199555/acove-thumbnail-qa-artifacts/logs/api.log)、[首次补图](/home/aitor/.codex/visualizations/2026/10/21/01a15d79-5adb-7ca0-8447-1e9160199555/acove-thumbnail-qa-artifacts/logs/backfill-first.log)、[重复补图](/home/aitor/.codex/visualizations/2026/10/21/01a15d79-5adb-7ca0-8447-1e9160199555/acove-thumbnail-qa-artifacts/logs/backfill-second.log)、[故障补图](/home/aitor/.codex/visualizations/2026/10/21/01a15d79-5adb-7ca0-8447-1e9160199555/acove-thumbnail-qa-artifacts/logs/backfill-failure.log)、[清理记录](/home/aitor/.codex/visualizations/2026/10/21/01a15d79-5adb-7ca0-8447-1e9160199555/acove-thumbnail-qa-artifacts/logs/cleanup.log)、[单测计数](/home/aitor/.codex/visualizations/2026/10/21/01a15d79-5adb-7ca0-8447-1e9160199555/acove-thumbnail-qa-artifacts/logs/test-summary.json)。

## 浏览器与清理

Playwright Chromium 实测：

- 图库列表封面、图库详情加载 480 档 WebP；文章前台使用响应式 WebP。四种格式均加载成功。
- 图库详情打开原图（PNG 2400 宽），ArrowRight 切到 JPEG 原图（1800×2400），Escape 关闭。
- 文章前台、后台只读预览和编辑器分屏实时预览打开原图成功；实时预览支持 Enter 打开、Escape 关闭。
- 后台图库显示四种格式缩略图，原图查看器成功打开 JPEG 原图。
- 对派生图请求模拟 404，文章四图均移除 srcset 并显示原图，图库详情四图及图库封面也成功回退原图；故障模拟已撤销。
- 后端重启期间首次导航有预期的临时 502，等待启动后刷新通过；故障模拟期间的 404 属于主动注入。稳定运行的页面未发现额外脚本错误。
- [图库原图查看截图](/home/aitor/.codex/visualizations/2026/10/21/01a15d79-5adb-7ca0-8447-1e9160199555/acove-thumbnail-qa-artifacts/browser/gallery.png)、[后台图库截图](/home/aitor/.codex/visualizations/2026/10/21/01a15d79-5adb-7ca0-8447-1e9160199555/acove-thumbnail-qa-artifacts/browser/admin-gallery.png)，完整页面快照保存在本地归档的 `browser/cli` 文件夹。

单图删除、整库删除、文章取消发布／物理删除均 HTTP 200 / code 200；gallery_album、gallery_image、article 恢复 `0/0/0`。图库原图、派生图及临时文件全部清空；文章上传文件按其独立生命周期由验收脚本显式清理。测试容器、数据库与网络已移除，浏览器会话已关闭。验收日志、截图及快照已移出 Git 仓库，本地归档位置为 `/home/aitor/.codex/visualizations/2026/10/21/01a15d79-5adb-7ca0-8447-1e9160199555/acove-thumbnail-qa-artifacts`；测试镜像保留供复核。归档文件不随仓库分发。

当前无可清理的既有本地业务分支候选（仅 main、develop 和当前修复分支）；未删除分支。

## 边界

本轮通过的是修复后的图片链路及关联文章／图库验收，未声称所有后台模块、跨浏览器、移动端、生产 HTTPS 的全面回归通过。3 个既有 MySQL 专项单测未启用；实际隔离库迁移与本轮 CRUD 已实测。

未连接生产服务器，未核验生产备份、磁盘、域名或证书。正式发布前仍须备份，并在暂停上传和停止后端后执行存量补图；功能此前未发布，因此本轮保留 URL，无需处理历史线上空响应缓存。
