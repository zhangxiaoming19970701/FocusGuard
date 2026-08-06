# 需求—代码追踪矩阵

| 需求族 | 主要实现位置 | 当前状态 |
|---|---|---|
| FR-001/002 初始化与本机管理员 | `ui/MainScreens.kt`, `ui/MainViewModel.kt` | 已实现 |
| FR-010~012 权限引导与健康 | `PermissionSetupScreen`, `PermissionUtils`, `HealthCheckWorker` | 已实现；厂商专用深链待真机补充 |
| FR-020 应用选择 | `AppCatalog`, `AppsScreen` | 已实现 |
| FR-021 应用分组 | — | 建议项，未实现 |
| FR-030~035 连续、每日、时段、休息、提醒 | `RuleEntity`, `RuleEditorScreen`, `RuleEngine`, `ScheduleMatcher` | 已实现 |
| FR-040/041 前台识别与会话计时 | `AccessibilityMonitorService`, `ForegroundOrchestrator` | 已实现 |
| FR-042 双重校核 | `UsageStatsInspector` | 部分实现：重连前台校核，未自动回填历史缺口 |
| FR-050/051 提醒与额度状态 | `NotificationHelper`, `HomeScreen` | 提醒、今日使用与剩余额度已实现；实时连续会话详情页仍需产品化完善 |
| FR-060~065 拦截、倒计时、密码、临时解除 | `BlockCoordinator`, `BlockActivity`, `BlockScreen`, `PinPad`, `PasswordHasher` | 已实现 |
| FR-070~073 恢复与时间变化 | `BootReceiver`, `TimeChangeReceiver`, `HealthCheckWorker`, Room 锁定状态 | 已实现；需真机验证 |
| FR-080/081 白名单与来电 | `SystemWhitelist`, Accessibility 重新评估 | 代码已实现，需多厂商来电测试 |
| FR-090/091 统计与日志 | `StatsScreen`, `AuditEventEntity` | 今日/近7日和最近事件已实现 |
| FR-092 清理/恢复/改密 | `DiagnosticsScreen`, Repository | 已实现 |
| FR-100/101 规则启停与校验 | `RuleEditorScreen`, `RuleEditorViewModel` | 已实现基础校验 |
| FR-110/111 诊断与日志保护 | `DiagnosticsScreen`, `copyDiagnostics` | 已实现 |
| FR-120 本地隐私 | Manifest、Room/DataStore、`PRIVACY.md` | 已实现 |
| FR-130 横竖屏与字体 | Compose 响应式布局、BlockActivity | 代码支持，需真机验收 |


> 状态说明：本矩阵描述代码覆盖范围，不等于真机验收结论。所有标为“已实现”的 Android 系统行为仍须按 `TEST_PLAN.md` 在目标设备验证。
