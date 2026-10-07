# 日程一览

只读取手机**系统日历**（小米日历 / 谷歌日历 / 系统日历）里已有的日程并展示。应用自己**不记账单、不写入日历**。

## 做什么

- 列出今天及未来一年的日程（含明年；重复日程只显示最近一次）
- 卡片展示标题、具体日期时间和距离提示，省去分类标签及来源/重复规则行；跨天日程按实际结束日期判断
- 在右上角菜单里按日历本筛选，支持多选并保存选择
- 记住上次查看的分类，以及两个分类各自的滚动位置
- 长按日程可在「生活」和「订阅」之间切换，支持撤销
- 点一条日程，用系统日历打开详情
- 下拉刷新
- 日历变化或前台跨过午夜时自动刷新；返回前台复用未变化的结果，状态变化时更新显示；读取失败保留上次结果并支持重试

订阅分类和日历筛选使用本机日历 ID，换机时不会迁移，以免匹配到错误日程。

添加、修改、提醒请在系统日历里操作。

## 构建

```
cd AgendaGlance
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

包名 `com.billremind.app`，权限只有 `READ_CALENDAR`。

## 下载与发布

安装包见 [GitHub Releases](https://github.com/cvabm/AgendaGlance/releases)，每版包含已校验签名的 APK 与 `SHA256SUMS.txt`。正式发布由 [Release workflow](https://github.com/cvabm/AgendaGlance/actions/workflows/release.yml) 自动构建并上传，不需要手动上传本地 APK。

在 Actions 中选择 **Release → Run workflow → main** 即可发布当前应用版本；也支持推送与应用版本匹配的 `v*` tag。`app/build.gradle.kts` 中的 `versionName = "1.0"` 对应 `v1.0.0`，再次发新版必须同时递增 `versionName` 和 `versionCode`，不能复用既有 Release。普通 Android CI 只负责构建和轻量静态检查，不会发布。

目前沿用仓库已有签名以保持安装兼容；签名材料已公开，不应作为生产环境秘密使用。调整签名需要另行确认，并评估旧版用户的升级方式。
