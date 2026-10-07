package com.billremind.app.calendar

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.CalendarContract

object VendorGuard {
    private val CALENDAR_PACKAGES = listOf(
        "com.xiaomi.calendar",
        "com.android.calendar",
        "com.google.android.calendar",
        "com.samsung.android.calendar"
    )

    fun manufacturer(): String = (Build.MANUFACTURER + " " + Build.BRAND).trim()

    fun systemCalendarPackage(context: Context): String? =
        CALENDAR_PACKAGES.firstOrNull { pkg ->
            context.packageManager.getLaunchIntentForPackage(pkg) != null
        }

    fun openSystemCalendar(context: Context): Boolean {
        for (pkg in CALENDAR_PACKAGES) {
            val launch = context.packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(launch)
                    return true
                } catch (_: Exception) {
                    // A disabled or unavailable vendor app should not stop the fallbacks.
                }
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
