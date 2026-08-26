package com.billremind.app

import com.billremind.app.calendar.CalendarEvent
import com.billremind.app.calendar.CalendarLabels
import com.billremind.app.calendar.EventSeries
import com.billremind.app.calendar.RecurrenceKind
import com.billremind.app.calendar.RecurrenceParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class RecurrenceParserTest {

    private val today = LocalDate.of(2026, 8, 21)

    @Test
    fun upcomingDateMarksNextYear() {
        assertEquals("8月21日", CalendarLabels.upcomingDate(today, today))
        assertEquals("明年1月15日", CalendarLabels.upcomingDate(LocalDate.of(2027, 1, 15), today))
        assertEquals("后年3月1日", CalendarLabels.upcomingDate(LocalDate.of(2028, 3, 1), today))
    }

    @Test
    fun onceEventNextYearIsKeptWithinOneYearWindow() {
        val nextYear = event(date = LocalDate.of(2027, 1, 15), title = "车险")
        val grouped = RecurrenceParser.group(listOf(nextYear), onceWithinDays = 366, today = today)
        assertEquals(1, grouped.size)
        assertEquals("车险", grouped[0].title)
        assertEquals(RecurrenceKind.ONCE, grouped[0].kind)
    }

    @Test
    fun onceEventBeyondWindowIsDropped() {
        val far = event(date = LocalDate.of(2028, 1, 15), title = "太远")
        val grouped = RecurrenceParser.group(listOf(far), onceWithinDays = 366, today = today)
        assertTrue(grouped.isEmpty())
    }

    @Test
    fun yearlyRuleNextYearIsKept() {
        val yearly = event(
            date = LocalDate.of(2027, 1, 15),
            title = "年费",
            rrule = "FREQ=YEARLY;BYMONTH=1;BYMONTHDAY=15"
        )
        val grouped = RecurrenceParser.group(listOf(yearly), onceWithinDays = 90, today = today)
        assertEquals(1, grouped.size)
        assertEquals(RecurrenceKind.YEARLY, grouped[0].kind)
        assertEquals("每年1月15日", grouped[0].ruleLabel)
    }

    @Test
    fun daysLabelSaysNextYear() {
        val series = EventSeries(
            eventId = 1,
            calendarId = 1,
            title = "年费",
            calendarName = "小米日历",
            color = 0,
            hasAlarm = false,
            kind = RecurrenceKind.YEARLY,
            ruleLabel = "每年1月15日",
            next = event(date = LocalDate.of(2027, 1, 15), title = "年费"),
            upcomingCount = 1
        )
        assertEquals("明年", series.daysLabel(today))
        assertEquals("今天", series.daysLabel(LocalDate.of(2027, 1, 15)))
        assertEquals("明天", series.daysLabel(LocalDate.of(2027, 1, 14)))
    }

    @Test
    fun subscribedItemMovesToSubscriptionCategory() {
        val series = EventSeries(
            eventId = 1,
            calendarId = 1,
            title = "视频会员",
            calendarName = "小米日历",
            color = 0,
            hasAlarm = false,
            kind = RecurrenceKind.MONTHLY,
            ruleLabel = "每月15日",
            next = event(date = LocalDate.of(2026, 9, 15), title = "视频会员"),
            upcomingCount = 1,
            subscribed = true
        )
        assertEquals(RecurrenceKind.SUBSCRIPTION, series.displayKind())
        assertEquals("订阅", series.displayKind().label)
    }

    private fun event(
        date: LocalDate,
        title: String,
        rrule: String = "",
        id: Long = 1
    ): CalendarEvent {
        val beginMs = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        return CalendarEvent(
            instanceId = id,
            eventId = id,
            calendarId = 1,
            calendarName = "小米日历",
            account = "",
            title = title,
            description = "",
            location = "",
            beginMs = beginMs,
            endMs = beginMs + 86_400_000,
            allDay = true,
            color = 0,
            hasAlarm = false,
            rrule = rrule
        )
    }
}
