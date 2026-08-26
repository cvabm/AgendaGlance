package com.billremind.app.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.ZoneId

class DeviceCalendar(private val context: Context) {

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun listCalendars(): List<CalendarInfo> {
        if (!hasPermission()) return emptyList()
        val out = mutableListOf<CalendarInfo>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.VISIBLE
        )
        try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    out += CalendarInfo(
                        id = cursor.longOf(CalendarContract.Calendars._ID),
                        name = cursor.stringOf(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME),
                        account = cursor.stringOf(CalendarContract.Calendars.ACCOUNT_NAME),
                        accountType = cursor.stringOf(CalendarContract.Calendars.ACCOUNT_TYPE),
                        primary = cursor.intOf(CalendarContract.Calendars.IS_PRIMARY) == 1,
                        color = cursor.intOf(CalendarContract.Calendars.CALENDAR_COLOR),
                        visible = cursor.intOf(CalendarContract.Calendars.VISIBLE) != 0
                    )
                }
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return out.sortedWith(compareByDescending<CalendarInfo> { it.primary }.thenBy { it.displayName() })
    }

    /**
     * Expands recurring events via CalendarContract.Instances.
     * Range is [fromMs, toMs), typically today → +1 year (covers next year).
     */
    fun listEvents(fromMs: Long, toMs: Long): List<CalendarEvent> {
        if (!hasPermission()) return emptyList()
        val calendars = listCalendars().associateBy { it.id }
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, fromMs)
        ContentUris.appendId(builder, toMs)
        val projection = arrayOf(
            CalendarContract.Instances._ID,
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.HAS_ALARM,
            CalendarContract.Instances.RRULE
        )
        val out = mutableListOf<CalendarEvent>()
        try {
            context.contentResolver.query(
                builder.build(),
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val calendarId = cursor.longOf(CalendarContract.Instances.CALENDAR_ID)
                    val info = calendars[calendarId]
                    if (info != null && !info.visible) continue
                    val title = cursor.stringOf(CalendarContract.Instances.TITLE).ifBlank { "（无标题）" }
                    val color = cursor.intOf(CalendarContract.Instances.DISPLAY_COLOR).let { c ->
                        if (c != 0) c else info?.color ?: 0
                    }
                    out += CalendarEvent(
                        instanceId = cursor.longOf(CalendarContract.Instances._ID),
                        eventId = cursor.longOf(CalendarContract.Instances.EVENT_ID),
                        calendarId = calendarId,
                        calendarName = info?.name?.ifBlank { info.displayName() } ?: "系统日历",
                        account = info?.account.orEmpty(),
                        title = title,
                        description = cursor.stringOf(CalendarContract.Instances.DESCRIPTION),
                        location = cursor.stringOf(CalendarContract.Instances.EVENT_LOCATION),
                        beginMs = cursor.longOf(CalendarContract.Instances.BEGIN),
                        endMs = cursor.longOf(CalendarContract.Instances.END),
                        allDay = cursor.intOf(CalendarContract.Instances.ALL_DAY) == 1,
                        color = color,
                        hasAlarm = cursor.intOf(CalendarContract.Instances.HAS_ALARM) == 1,
                        rrule = cursor.stringOf(CalendarContract.Instances.RRULE)
                    )
                }
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return out
    }

    fun openEvent(context: Context, event: CalendarEvent): Boolean {
        val begin = event.beginMs
        val end = if (event.endMs > event.beginMs) event.endMs else event.beginMs + 3_600_000L
        val pkg = VendorGuard.systemCalendarPackage(context)
        val attempts = buildList {
            if (pkg != null) {
                add(viewEventIntent(event.eventId, begin, end, event.allDay).setClassName(
                    pkg,
                    "com.android.calendar.event.EventInfoActivity"
                ))
                add(viewEventIntent(event.eventId, begin, end, event.allDay).setPackage(pkg))
            }
            add(viewEventIntent(event.eventId, begin, end, event.allDay))
            if (pkg != null) add(viewDayIntent(begin).setPackage(pkg))
            add(viewDayIntent(begin))
        }
        for (intent in attempts) {
            try {
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
            }
        }
        return VendorGuard.openSystemCalendar(context)
    }

    fun viewEventIntent(
        eventId: Long,
        beginMs: Long,
        endMs: Long,
        allDay: Boolean = false
    ): Intent {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "vnd.android.cursor.item/event")
            putEventTimes(eventId, beginMs, endMs, allDay)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun viewDayIntent(millis: Long = System.currentTimeMillis()): Intent {
        val builder = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
        ContentUris.appendId(builder, millis)
        return Intent(Intent.ACTION_VIEW).apply {
            data = builder.build()
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, millis)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }

    private fun Intent.putEventTimes(
        eventId: Long,
        beginMs: Long,
        endMs: Long,
        allDay: Boolean
    ) {
        putExtra(CalendarContract.EXTRA_EVENT_ID, eventId)
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginMs)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMs)
        putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, allDay)
        putExtra("extra_event_id", eventId)
        putExtra("extra_event_start_millis", beginMs)
        putExtra("extra_event_time", beginMs)
        putExtra("key_event_id", eventId)
        putExtra("key_start_millis", beginMs)
        putExtra("key_end_millis", endMs)
    }

    companion object {
        const val RANGE_DAYS = 366L
        const val ONCE_DAYS = RANGE_DAYS

        fun rangeStartMs(zone: ZoneId = ZoneId.systemDefault()): Long =
            LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()

        fun rangeEndMs(days: Long = RANGE_DAYS, zone: ZoneId = ZoneId.systemDefault()): Long =
            LocalDate.now(zone).plusDays(days).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    private fun Cursor.longOf(column: String): Long {
        val i = getColumnIndex(column)
        return if (i >= 0 && !isNull(i)) getLong(i) else 0L
    }

    private fun Cursor.intOf(column: String): Int {
        val i = getColumnIndex(column)
        return if (i >= 0 && !isNull(i)) getInt(i) else 0
    }

    private fun Cursor.stringOf(column: String): String {
        val i = getColumnIndex(column)
        return if (i >= 0 && !isNull(i)) getString(i) ?: "" else ""
    }
}
