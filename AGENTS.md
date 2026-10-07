# 仓库约定

## 检查与优化

- 不主动做边界测试，不构造罕见日历规则或极端场景来列举问题；仅在用户明确要求时进行。
- 不保留或新增臃肿的测试代码、测试依赖、模拟器或实机自动测试任务。默认只做构建和必要的轻量静态检查；用户明确要求测试时再处理。
- 未经用户针对当前操作明确要求，禁止卸载应用、清除应用数据或缓存，也禁止运行会自动清理安装的实机测试任务（如 `connectedDebugAndroidTest`）。普通更新使用覆盖安装，保留订阅分类、筛选和其他本地设置。
- Bug 检查与优化建议优先依据实际使用场景、已观察到的行为和用户反馈。未核实的推测必须明确说明，不能把构造的样例当作用户日历中的记录或实际问题。
- 列表卡片省去分类标签，以及日历来源/重复规则的整行，不要用“每月26日”等内容重新填回已删除的来源行。

## 发布

- `.github/workflows/android-ci.yml` 是普通 CI，只构建、做轻量静态检查并上传 artifact，不创建 GitHub Release，不运行自动化测试。
- `.github/workflows/release.yml` 负责正式发布。用户说“触发 release”时，先核对版本、已有 tag / Release 和正在运行的任务，再手动运行 Release workflow；不能误触发普通 CI 当作发布。
- Release workflow 支持 `workflow_dispatch`，也支持推送 `v*` tag。手动运行会从 `app/build.gradle.kts` 推导 tag，`versionName = "1.0"` 对应 `v1.0.0`；三段版本直接加 v 前缀。
- 发布新版本前递增 `versionName` 和 `versionCode`，确保 Android 可以覆盖升级。不能只改 Git tag，不更新 APK 内版本。
- 默认手动触发 `main` 上的 Release workflow，不提前创建 tag；GitHub Release action 会为通过验证的提交创建 tag。不要覆盖既有 tag 或 Release。
- release APK 构建、签名/包名/版本校验、SHA256 文件与附件上传均由 Actions 完成；不手动上传本地 APK。
- 以 Actions 最终成功、Release 及 APK / SHA256 附件就绪为完成，不把“已触发”当作“已发布”。

## 范围与安全

- 应用只读取系统日历，维持只读日历权限；发布工作不修改应用功能或权限。
- 签名材料已经在公开仓库中，不能当作秘密，也不要在答复、日志或新增文档中重复口令。没有用户批准时不更换签名、不删除签名文件，以免破坏已有安装的更新兼容性。
- 保留用户改动，排除 APK、Gradle 缓存和本地产物。本地提交使用全局 Git 作者配置，不覆盖仓库级作者。
