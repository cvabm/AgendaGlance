package com.billremind.app.calendar

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.CalendarContract

object VendorGuard {
    fun manufacturer(): String = (Build.MANUFACTURER + " " + Build.BRAND).trim()

    fun openSystemCalendar(context: Context): Boolean {
        val packages = listOf(
            "com.xiaomi.calendar",
            "com.android.calendar",
            "com.google.android.calendar",
            "com.samsung.android.calendar"
        )
        for (pkg in packages) {
            val launch = context.packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
                return true
            }
        }
        val selector = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(selector)
            true
        } catch (_: Exception) {
            try {
                val builder = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
                android.content.ContentUris.appendId(builder, System.currentTimeMillis())
                context.startActivity(Intent(Intent.ACTION_VIEW).setData(builder.build()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
