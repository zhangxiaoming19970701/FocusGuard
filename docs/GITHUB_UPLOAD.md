# GitHub 网页上传注意事项

1. 解压 ZIP 后进入 `FocusGuard` 文件夹。
2. 选中该文件夹里面的所有内容，而不是把外层文件夹作为单个文件上传。
3. `.github` 是项目根目录下的真实文件夹，里面必须包含 `workflows/android.yml`。
4. GitHub 网页拖拽上传时应保持目录层级；上传后仓库首页应看到：`.github`、`app`、`docs`、`build.gradle.kts`、`settings.gradle.kts`。
5. 第一次 Actions 构建会下载 Android/Gradle 依赖，通常比后续构建慢。
6. 构建成功后 APK 在 Actions 运行页面的 Artifacts，不会自动出现在仓库文件列表。
