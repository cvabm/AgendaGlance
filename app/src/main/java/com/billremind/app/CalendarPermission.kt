package com.billremind.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class CalendarPermission(private val activity: AppCompatActivity, private val launch: () -> Unit) {
    private val prefs = activity.getSharedPreferences("permissions", AppCompatActivity.MODE_PRIVATE)

    fun wasRequested(): Boolean = prefs.getBoolean("calendar_requested", false)

    fun permanentlyDenied(): Boolean = wasRequested() &&
        !activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)

    fun request() {
        when {
            permanentlyDenied() -> MaterialAlertDialogBuilder(activity)
                .setTitle("开启日历读取权限")
                .setMessage("系统已停止显示授权弹窗。可以在应用设置的权限页面开启日历读取权限。")
                .setPositiveButton("打开设置") { _, _ ->
                    activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${activity.packageName}")))
                }
                .setNegativeButton("取消", null)
                .show()
            activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR) ->
                MaterialAlertDialogBuilder(activity)
                    .setTitle("读取系统日历")
                    .setMessage("需要读取日历权限才能展示已有日程。应用只读取，不修改日历。")
                    .setPositiveButton("继续授权") { _, _ -> launchRequest() }
                    .setNegativeButton("取消", null)
                    .show()
            else -> launchRequest()
        }
    }

    private fun launchRequest() {
        prefs.edit().putBoolean("calendar_requested", true).apply()
        launch()
    }
}
