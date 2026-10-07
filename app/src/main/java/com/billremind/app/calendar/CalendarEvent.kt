package com.billremind.app.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class CalendarInfo(
    val id: Long,
    val name: String,
    val account: String,
    val accountType: String,
    val primary: Boolean,
    val color: Int,
    val visible: Boolean
) {
    fun titleName(): String = when (name) {
        "calendar_displayname_xiaomi" -> "小米日历"
        "calendar_displayname_local" -> "本地日历"
        else -> name.ifBlank { account.ifBlank { "日历 $id" } }
    }

    fun displayName(): String {
        val n = titleName()
        return if (account.isNotBlank() && account != n) "$n（$account）" else n
    }
}

data class CalendarEvent(
    val instanceId: Long,
    val eventId: Long,
    val calendarId: Long,
    val calendarName: String,
    val account: String,
    val title: String,
    val description: String,
    val location: String,
    val beginMs: Long,
    val endMs: Long,
    val allDay: Boolean,
    val color: Int,
    val hasAlarm: Boolean,
    val rrule: String
) {
    val recurring: Boolean get() = rrule.isNotBlank()

    fun localDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate {
        return if (allDay) {
            Instant.ofEpochMilli(beginMs).atZone(ZoneOffset.UTC).toLocalDate()
        } else {
            Instant.ofEpochMilli(beginMs).atZone(zone).toLocalDate()
        }
    }

    fun timeLabel(zone: ZoneId = ZoneId.systemDefault()): String {
        if (allDay) return "全天"
        val begin = Instant.ofEpochMilli(beginMs).atZone(zone)
        val end = Instant.ofEpochMilli(endMs).atZone(zone)
        val startText = begin.format(HM)
        return if (begin.toLocalDate() == end.toLocalDate()) {
            "$startText–${end.format(HM)}"
        } else {
            "$startText–${end.format(MD_HM)}"
        }
    }

    /** All-day end dates are exclusive and stored in UTC by CalendarProvider. */
    fun occursOn(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (allDay) {
            val endDate = Instant.ofEpochMilli(endMs).atZone(ZoneOffset.UTC).toLocalDate()
            return !localDate(zone).isAfter(date) && date.isBefore(endDate)
        }
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return beginMs < end && (endMs > start || (endMs == beginMs && beginMs >= start))
    }

    fun overlapsRange(fromMs: Long, toMs: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (allDay) {
            val fromDate = Instant.ofEpochMilli(fromMs).atZone(zone).toLocalDate()
            val toDate = Instant.ofEpochMilli(toMs).atZone(zone).toLocalDate()
            val endDate = Instant.ofEpochMilli(endMs).atZone(ZoneOffset.UTC).toLocalDate()
            return localDate(zone).isBefore(toDate) && endDate.isAfter(fromDate)
        }
        return beginMs < toMs && (endMs > fromMs || (endMs == beginMs && beginMs >= fromMs))
    }

    fun dateTimeLabel(today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): String {
        val start = localDate(zone)
        val date = CalendarLabels.upcomingDate(start, today)
        if (!allDay) return "$date ${timeLabel(zone)}"
        val last = Instant.ofEpochMilli(endMs).atZone(ZoneOffset.UTC).toLocalDate().minusDays(1)
        return if (last.isAfter(start)) {
            "$date–${CalendarLabels.upcomingDate(last, today)} · 全天"
        } else "$date · 全天"
    }

    companion object {
        private val HM = DateTimeFormatter.ofPattern("HH:mm")
        private val MD_HM = DateTimeFormatter.ofPattern("M月d日 HH:mm")
    }
}

sealed class CalendarRow {
    data class Section(val kind: RecurrenceKind, val title: String, val count: Int) : CalendarRow()
    data class Series(
        val item: EventSeries,
        val renderedOn: LocalDate = LocalDate.now(),
        val zone: ZoneId = ZoneId.systemDefault(),
        val dayLabel: String = item.daysLabel(renderedOn)
    ) : CalendarRow()
}

object CalendarLabels {
    private val DATE = DateTimeFormatter.ofPattern("M月d日")
    private val locale = Locale.CHINA

    fun upcomingDate(date: LocalDate, today: LocalDate = LocalDate.now()): String {
        val body = date.format(DATE)
        return when (date.year - today.year) {
            0 -> body
            1 -> "明年$body"
            2 -> "后年$body"
            else -> if (date.year > today.year) "${date.year}年$body" else body
        }
    }

    fun dayHeader(date: LocalDate, today: LocalDate = LocalDate.now()): String {
        val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        val prefix = when (date) {
            today -> "今天"
            today.plusDays(1) -> "明天"
            today.minusDays(1) -> "昨天"
            else -> null
        }
        val body = "${upcomingDate(date, today)} $weekday"
        return if (prefix == null) body else "$prefix · $body"
    }
}
