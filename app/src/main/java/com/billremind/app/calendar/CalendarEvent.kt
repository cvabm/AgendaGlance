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
    fun displayName(): String {
        val n = name.ifBlank { account.ifBlank { "日历 $id" } }
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

    fun subtitle(): String {
        val bits = mutableListOf<String>()
        bits += calendarName.ifBlank { account }.ifBlank { "系统日历" }
        if (location.isNotBlank()) bits += location
        else if (description.isNotBlank()) bits += description.lineSequence().first().trim()
        if (recurring) bits += "重复"
        if (hasAlarm) bits += "有提醒"
        return bits.joinToString(" · ")
    }

    companion object {
        private val HM = DateTimeFormatter.ofPattern("HH:mm")
        private val MD_HM = DateTimeFormatter.ofPattern("M月d日 HH:mm")
    }
}

sealed class CalendarRow {
    data class Section(val kind: RecurrenceKind, val title: String, val count: Int) : CalendarRow()
    data class Series(val item: EventSeries) : CalendarRow()
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
