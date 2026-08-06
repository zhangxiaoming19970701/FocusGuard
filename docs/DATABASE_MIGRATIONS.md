# 数据库迁移策略

当前 MVP 使用 Room 数据库版本 1，并开启 schema 导出到 `app/schemas`。

后续任何实体字段或索引变化必须：

1. 提升 `FocusGuardDatabase` 的 `version`；
2. 新增显式 `Migration(old, new)`，不得在正式版本使用破坏性迁移；
3. 将迁移注册到 `Room.databaseBuilder(...).addMigrations(...)`；
4. 保留旧版 schema，并增加 Room migration test；
5. 验证规则、每日累计、未结束休息、临时解除及凭据在升级后保持一致；
6. 在 `CHANGELOG.md` 记录迁移范围和回退限制。

版本 1 尚无历史数据库需要迁移，因此当前构建没有注册迁移对象。
