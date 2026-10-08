# sql/legacy：历史建表脚本（只读存档）

这里的脚本是 Flyway 接入之前用的建库 / 升级方式，**不再被任何流程执行**：

- `docker-compose.yml` 已不再把它们挂进 MySQL 的 `docker-entrypoint-initdb.d`；
- `./deploy.sh init-db` 不再默认导入 `init_database.sql`，只保留「手工导入指定文件」的能力；
- 表结构的唯一来源是 `backend/src/main/resources/db/migration/`（当前基线是 `V1__baseline.sql`），
  后端启动时由 Flyway 自动应用。

保留它们只是为了历史追溯和应急排查，例如某个老库需要临时补一句 SQL：

```bash
./deploy.sh init-db sql/legacy/site_schema.sql
```

注意手工导入不会写进 `flyway_schema_history`，Flyway 也不会因此认为该版本已应用；
正常改表结构请新增 `V2__xxx.sql` 这样的迁移脚本，不要再来改这里的文件。

各文件的原始用途：

| 文件 | 原来干什么 |
| --- | --- |
| `init_database.sql` | 全新部署：建库 + 建出当时的全部表（不含任何用户） |
| `article_schema.sql` | 已有库补文章模块的表（分类 / 标签 / 文章 / 关联表） |
| `user_schema.sql` | 给 `sys_user` 补 `avatar` / `role` 两列，并把最早注册的账号提升为站长 |
| `visit_schema.sql` | 补访问统计的两张表（每日 PV / UV 汇总 + 访客去重） |
| `site_schema.sql` | 补站点设置单行表 `site_setting` 及其默认行 |
