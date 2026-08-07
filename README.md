# FocusGuard Android

FocusGuard 是一款本机运行的 Android 家长管理与个人数字自律工具。本仓库根据《FocusGuard APP 开发功能说明书 V0.1 评审稿》实现首个可构建 MVP。

## 已实现功能

- 家长管理 / 个人自律两种初始化模式与无障碍权限醒目披露。
- 本机管理员密码：4–8 位数字、自绘数字键盘、带盐 PBKDF2 哈希、连续 5 次错误暂停 30 秒。
- 8 位一次性恢复码；修改密码后自动生成新恢复码。
- 从手机已安装的可启动应用中搜索、选择受管应用；系统关键应用不可选。
- 每个应用独立配置：
  - 单次连续使用上限（1–720 分钟）
  - 每日累计额度
  - 强制休息时长（1–180 分钟）
  - 会话合并间隔
  - 全局休息 / 仅触发应用休息
  - 剩余 5 分钟和 1 分钟提醒
  - 多个禁用时段
  - “仅在以下时段允许使用”
  - 跨午夜时段
- AccessibilityService 事件驱动识别前台应用；UsageStats 仅用于服务重连时的低频校核。
- 黑底、红字、白色倒计时的全屏拦截页；返回键不能退出。
- 管理员临时解除、结束本次休息、返回桌面。
- 开机/解锁恢复、时间/时区变化审计、服务断开告警、WorkManager 低频健康检查。
- Room 本机数据库、DataStore 设置、90 天详细事件与 365 天使用统计清理策略。
- 今日/近 7 日统计、结构化事件日志、诊断信息复制、清理统计与恢复出厂设置。
- GitHub Actions 自动执行单元测试并生成 debug APK。

## 技术栈

- Kotlin 2.3.20
- Jetpack Compose + Material 3
- Android Gradle Plugin 8.13.2 / Gradle 8.13 / JDK 17
- minSdk 29（Android 10），compileSdk / targetSdk 36
- Hilt、Room、DataStore、WorkManager

## 在 GitHub 生成 APK

1. 新建一个空 GitHub 仓库。
2. 解压本项目，将**项目根目录内的全部文件和文件夹**上传到仓库根目录。必须保留 `.github/workflows/android.yml`。
3. 打开仓库的 **Actions** 页面，选择 **Android CI**。
4. 点击 **Run workflow**，或向 `main` 分支提交代码自动触发。
5. 构建完成后，在该次运行页面底部的 **Artifacts** 下载 `FocusGuard-debug-apk`。
6. 解压后得到 `app-debug.apk`，复制到 Android 手机安装。

> Debug APK 适合本人测试。公开发布前必须配置正式签名、隐私政策、无障碍用途声明和 Google Play 审核材料。

## Android Studio 本地构建

环境要求：Android Studio、JDK 17、Android SDK 36、Gradle 8.13。

```bash
gradle testDebugUnitTest
gradle assembleDebug
```

输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

也可先生成标准 Gradle Wrapper：

```bash
gradle wrapper --gradle-version 8.13
./gradlew assembleDebug
```

## 首次使用

1. 安装并打开 FocusGuard。
2. 阅读无障碍用途与隐私披露，选择家长管理或个人自律。
3. 设置管理密码并抄写恢复码。
4. 开启无障碍监测服务。
5. 建议开启使用情况访问、通知权限，并按手机厂商设置允许后台运行。
6. 在“应用”页面选择游戏、社交或资讯应用。
7. 打开每个应用的规则设置，配置连续时间、每日额度、休息和时段。
8. 返回首页确认显示“保护正常”。

## 项目结构

```text
app/src/main/java/com/focusguard/app/
├── data/          Room、DataStore、Repository
├── domain/        纯 Kotlin 规则引擎、时间段判断、密码哈希
├── system/        AccessibilityService、拦截协调、恢复与健康检查
├── ui/            Compose 页面、自绘密码键盘、ViewModel
└── util/          固定事件码与状态常量
```

## 关键设计

- **规则引擎不直接操作 Android UI**：它只接收上下文并输出唯一决策。
- **前台监测事件驱动**：不进行常驻 1 秒全量应用轮询。
- **解锁恢复为有界重判**：仅在亮屏、用户解锁和服务重连后短暂重试活动窗口包名，UsageStats 只作降级信号。
- **健康状态交叉校验**：系统权限开关、服务运行时连接和持久化心跳必须一致，失联时提供可操作通知。
- **使用时间只累计有效前台秒数**：锁屏不计时，短暂切换在合并间隔内保留同一连续会话，但切走时间不计入使用。
- **倒计时以固定结束时间为准**：同一开机周期优先使用 `elapsedRealtime`，避免手动调系统时间直接跳过休息。
- **先持久化再显示拦截**：进程重建后可根据数据库恢复锁定。
- **安全白名单**：FocusGuard、本机通话、来电界面、紧急呼叫和 System UI 不被普通规则阻断。

## 测试

仓库包含纯 Kotlin 单元测试：

- 普通禁用时段
- 跨午夜时段
- 允许时段
- 规则优先级
- 连续上限与每日额度
- 00:00 / 04:00 日界线

完整验收仍必须在至少三台不同厂商真机执行，重点测试无障碍服务回收、横屏、分屏/PiP、来电、重启、修改时间及 72 小时稳定性。详见 [docs/TEST_PLAN.md](docs/TEST_PLAN.md)。当前自动检查范围见 [BUILD_VALIDATION.md](BUILD_VALIDATION.md)，数据库升级规则见 [docs/DATABASE_MIGRATIONS.md](docs/DATABASE_MIGRATIONS.md)。

## 重要能力边界

普通安装版不能绝对防止用户卸载、强制停止、关闭无障碍权限或修改系统时间，也不能像 Device Owner/DPC 一样从系统策略层彻底禁止第三方应用。详见 [KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md)。
