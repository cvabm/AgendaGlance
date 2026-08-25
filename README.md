# 系统日历（BillRemind）

只读取手机**系统日历**（小米日历 / 谷歌日历 / 系统日历）里已有的日程并展示。应用自己**不记账单、不写入日历**。

## 做什么

- 列出未来一年的日程（含明年；重复事件会展开，日期会标「明年」）
- 按日历本筛选
- 点一条日程，用系统日历打开详情
- 下拉刷新

添加、修改、提醒请在系统日历里操作。

## 构建

```
cd BillRemind
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

包名 `com.billremind.app`，权限只有 `READ_CALENDAR`。
