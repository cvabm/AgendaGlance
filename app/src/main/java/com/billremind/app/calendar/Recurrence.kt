package com.billremind.app.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class RecurrenceKind(
    val storage: String,
    val label: String,
    val order: Int
) {
    SUBSCRIPTION("subscription", "订阅", -1),
    MONTHLY("monthly", "每月", 0),
    QUARTERLY("quarterly", "每季度", 1),
    YEARLY("yearly", "每年", 2),
    WEEKLY("weekly", "每周", 3),
    DAILY("daily", "每天", 4),
    OTHER("other", "其他重复", 5),
    ONCE("once", "单次", 6);

    companion object {
        fun fromStorage(value: String?): RecurrenceKind =
            entries.firstOrNull { it.storage == value } ?: ONCE
    }
}

data class EventSeries(
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val calendarName: String,
    val color: Int,
    val hasAlarm: Boolean,
    val kind: RecurrenceKind,
    val ruleLabel: String,
    val next: CalendarEvent,
    val upcomingCount: Int,
    val subscribed: Boolean = false
) {
    fun displayKind(): RecurrenceKind =
        if (subscribed) RecurrenceKind.SUBSCRIPTION else kind

    fun categoryLabel(): String =
        if (subscribed) "订阅" else "生活"

    fun daysUntil(today: LocalDate = LocalDate.now()): Int =
        if (next.occursOn(today)) 0 else ChronoUnit.DAYS.between(today, next.localDate()).toInt()

    fun daysLabel(today: LocalDate = LocalDate.now(), nowMs: Long = System.currentTimeMillis()): String {
        val date = next.localDate()
        if (date.isBefore(today) && next.occursOn(today)) {
            return if (next.allDay || next.endMs > nowMs) "进行中" else "已结束"
        }
        return when (val d = daysUntil(today)) {
            0 -> "今天"
            1 -> "明天"
            else -> when {
                d < 0 -> "已过"
                date.year > today.year -> "明年"
                else -> "${d}天后"
            }
        }
    }
}

object RecurrenceParser {
    fun kind(rrule: String, instances: List<CalendarEvent>): RecurrenceKind {
        val freq = part(rrule, "FREQ")
        val interval = part(rrule, "INTERVAL")?.toIntOrNull() ?: 1
        val fromRule = when (freq) {
            "DAILY" -> if (interval == 1) RecurrenceKind.DAILY else RecurrenceKind.OTHER
            "WEEKLY" -> RecurrenceKind.WEEKLY
            "MONTHLY" -> when (interval) {
                1 -> RecurrenceKind.MONTHLY
                3 -> RecurrenceKind.QUARTERLY
                else -> RecurrenceKind.OTHER
            }
            "YEARLY" -> RecurrenceKind.YEARLY
            else -> null
        }
        if (fromRule != null) return fromRule
        return inferFromGaps(instances)
    }

    fun describe(rrule: String, sample: CalendarEvent, instances: List<CalendarEvent>, kind: RecurrenceKind): String {
        val freq = part(rrule, "FREQ")
        val interval = part(rrule, "INTERVAL")?.toIntOrNull() ?: 1
        val byDay = part(rrule, "BYDAY")
        val byMonthDay = part(rrule, "BYMONTHDAY")
        val byMonth = part(rrule, "BYMONTH")
        val date = sample.localDate()
        // Do not turn unsupported selectors into a misleading sampled fixed date.
        if (listOf("BYYEARDAY", "BYWEEKNO", "BYHOUR", "BYMINUTE", "BYSECOND").any { part(rrule, it) != null }) {
            return "重复（按自定义规则）"
        }
        if (freq.isNullOrBlank()) {
            return when (kind) {
                RecurrenceKind.DAILY -> "每天"
                RecurrenceKind.WEEKLY -> "每周${weekday(date.dayOfWeek)}"
                RecurrenceKind.MONTHLY -> "每月${date.dayOfMonth}日"
                RecurrenceKind.QUARTERLY -> "每季度${date.dayOfMonth}日"
                RecurrenceKind.YEARLY -> "每年${date.monthValue}月${date.dayOfMonth}日"
                RecurrenceKind.OTHER -> "重复"
                RecurrenceKind.ONCE -> onceLabel(sample, date)
                RecurrenceKind.SUBSCRIPTION -> "订阅"
            }
        }
        return when (freq) {
            "DAILY" -> {
                val prefix = if (interval <= 1) "每天" else "每${interval}天"
                val days = formatByDay(byDay)
                if (days != null) "$prefix（周$days）" else prefix
            }
            "WEEKLY" -> {
                val days = formatByDay(byDay)
                when {
                    interval > 1 && days != null -> "每${interval}周$days"
                    interval > 1 -> "每${interval}周"
                    days != null -> "每周$days"
                    else -> "每周${weekday(date.dayOfWeek)}"
                }
            }
            "MONTHLY" -> {
                val dayText = ruleDayText(byMonthDay, byDay, part(rrule, "BYSETPOS"), date.dayOfMonth)
                when (interval) {
                    1 -> "每月$dayText"
                    3 -> "每季度$dayText"
                    else -> "每${interval}个月$dayText"
                }
            }
            "YEARLY" -> {
                // An ordinal BYDAY without BYMONTH is relative to the year, not a sampled month.
                val months = byMonth?.split(',')?.mapNotNull { it.toIntOrNull()?.takeIf { n -> n in 1..12 } }
                val monthText = when {
                    !months.isNullOrEmpty() -> months.joinToString("、") { "${it}月" }
                    byDay != null -> ""
                    else -> "${date.monthValue}月"
                }
                val dayText = ruleDayText(byMonthDay, byDay, part(rrule, "BYSETPOS"), date.dayOfMonth)
                val prefix = if (interval > 1) "每${interval}年" else "每年"
                "$prefix$monthText$dayText"
            }
            else -> if (instances.size > 1) "重复" else onceLabel(sample, date)
        }
    }

    fun group(
        events: List<CalendarEvent>,
        onceWithinDays: Long = 366,
        today: LocalDate = LocalDate.now()
    ): List<EventSeries> {
        return events.filter { !it.localDate().isBefore(today) || it.occursOn(today) }
            .groupBy { it.eventId }.mapNotNull { (_, instances) ->
            val sorted = instances.sortedBy { it.beginMs }
            val sample = sorted.first()
            val kind = kind(sample.rrule, sorted)
            if (kind == RecurrenceKind.ONCE) {
                val days = ChronoUnit.DAYS.between(today, sample.localDate())
                if (days > onceWithinDays) return@mapNotNull null
            }
            EventSeries(
                eventId = sample.eventId,
                calendarId = sample.calendarId,
                title = sample.title,
                calendarName = sample.calendarName,
                color = sample.color,
                hasAlarm = sample.hasAlarm,
                kind = kind,
                ruleLabel = describe(sample.rrule, sample, sorted, kind),
                next = sample,
                upcomingCount = sorted.size
            )
        }.sortedWith(compareBy({ it.kind.order }, { it.next.beginMs }, { it.title }))
    }

    private fun onceLabel(sample: CalendarEvent, date: LocalDate): String {
        val text = CalendarLabels.upcomingDate(date)
        return if (sample.allDay) text else "$text ${sample.timeLabel()}"
    }

    private fun inferFromGaps(instances: List<CalendarEvent>): RecurrenceKind {
        val dates = instances.map { it.localDate() }.distinct().sorted()
        if (dates.size < 2) return RecurrenceKind.ONCE
        val gaps = dates.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b) }.sorted()
        val median = gaps[gaps.size / 2]
        return when (median) {
            in 1..1 -> RecurrenceKind.DAILY
            in 6..8 -> RecurrenceKind.WEEKLY
            in 27..33 -> RecurrenceKind.MONTHLY
            in 85..95 -> RecurrenceKind.QUARTERLY
            in 350..380 -> RecurrenceKind.YEARLY
            else -> RecurrenceKind.OTHER
        }
    }

    private fun part(rrule: String, key: String): String? {
        if (rrule.isBlank()) return null
        return rrule.split(';')
            .map { it.trim() }
            .firstOrNull { it.startsWith("$key=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.uppercase()
            ?.ifBlank { null }
    }

    private fun monthDayText(byMonthDay: String?, fallbackDay: Int): String {
        val days = byMonthDay?.split(',')?.mapNotNull { it.toIntOrNull() }.orEmpty()
        if (days.isEmpty()) return "${fallbackDay}日"
        return days.joinToString("、") { raw ->
            when {
                raw == -1 -> "最后一天"
                raw < 0 -> "倒数第${-raw}天"
                else -> "${raw}日"
            }
        }
    }

    private fun ruleDayText(monthDays: String?, weekDays: String?, positions: String?, fallback: Int): String {
        if (monthDays != null && weekDays != null) return "（按自定义规则）"
        if (positions != null && weekDays == null) return "（按自定义规则）"
        if (weekDays == null) return monthDayText(monthDays, fallback)
        val tokens = weekDays.split(',')
        val names = tokens.mapNotNull { token ->
            val name = WEEKDAYS[token.takeLast(2)] ?: return@mapNotNull null
            val ordinal = token.dropLast(2).toIntOrNull()
            if (ordinal == null) "周$name" else "${ordinalText(ordinal)}个周$name"
        }
        if (names.size != tokens.size) return "（按自定义规则）"
        if (positions == null) return names.joinToString("、")
        val pos = positions.split(',').mapNotNull { it.toIntOrNull() }
        // BYSETPOS across several weekdays selects from their combined set.
        if (names.size != 1 || pos.isEmpty() || tokens.single().dropLast(2).isNotEmpty()) {
            return "（按自定义规则）"
        }
        return pos.joinToString("、") { "${ordinalText(it)}个${names.single()}" }
    }

    private fun ordinalText(value: Int): String = when (value) {
        -1 -> "最后一"
        1 -> "第一"
        else -> if (value < 0) "倒数第${-value}" else "第$value"
    }

    private fun formatByDay(byDay: String?): String? {
        if (byDay.isNullOrBlank()) return null
        val names = byDay.split(',')
            .mapNotNull { token ->
                val code = token.takeLast(2).uppercase()
                WEEKDAYS[code]
            }
        if (names.isEmpty()) return null
        return names.joinToString("、")
    }

    private fun weekday(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "一"
        DayOfWeek.TUESDAY -> "二"
        DayOfWeek.WEDNESDAY -> "三"
        DayOfWeek.THURSDAY -> "四"
        DayOfWeek.FRIDAY -> "五"
        DayOfWeek.SATURDAY -> "六"
        DayOfWeek.SUNDAY -> "日"
    }

    private val WEEKDAYS = mapOf(
        "MO" to "一",
        "TU" to "二",
        "WE" to "三",
        "TH" to "四",
        "FR" to "五",
        "SA" to "六",
        "SU" to "日"
    )
}
