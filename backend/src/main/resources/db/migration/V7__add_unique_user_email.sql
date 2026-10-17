-- 升级前按 README 检查重复邮箱，并由管理员按账号 ID 确认和修正归属。
-- 保留邮箱列现有排序规则；若仍有重复值，本迁移失败且不改写账号数据。
ALTER TABLE sys_user ADD UNIQUE KEY uk_email (email);
