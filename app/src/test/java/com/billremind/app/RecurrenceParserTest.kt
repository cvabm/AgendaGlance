package com.billremind.app

import com.billremind.app.calendar.CalendarEvent
import com.billremind.app.calendar.CalendarLabels
import com.billremind.app.calendar.EventSeries
import com.billremind.app.calendar.RecurrenceKind
import com.billremind.app.calendar.RecurrenceParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZoneId

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

    @Test
    fun monthlyOrdinalWeekdayIsNotDescribedAsFixedDate() {
        val sample = event(today, "月会", "FREQ=MONTHLY;BYDAY=1FR")
        assertEquals("每月第一个周五", RecurrenceParser.group(listOf(sample), today = today).single().ruleLabel)
    }

    @Test
    fun lastWeekdayAndSetPositionAreDescribed() {
        val last = event(today, "月会", "FREQ=MONTHLY;BYDAY=-1FR")
        val second = event(today, "月会", "FREQ=MONTHLY;BYDAY=FR;BYSETPOS=2")
        assertEquals("每月最后一个周五", RecurrenceParser.group(listOf(last), today = today).single().ruleLabel)
        assertEquals("每月第2个周五", RecurrenceParser.group(listOf(second), today = today).single().ruleLabel)
    }

    @Test
    fun multipleMonthDaysAndNegativeDaysAreKept() {
        val sample = event(today, "提醒", "FREQ=MONTHLY;BYMONTHDAY=7,21,-1")
        assertEquals("每月7日、21日、最后一天", RecurrenceParser.group(listOf(sample), today = today).single().ruleLabel)
    }

    @Test
    fun yearlyOrdinalWeekdayAndMultipleMonthsAreKept() {
        val ordinal = event(today, "年会", "FREQ=YEARLY;BYMONTH=8;BYDAY=3FR")
        val multi = event(today, "提醒", "FREQ=YEARLY;BYMONTH=1,8;BYMONTHDAY=7,21")
        assertEquals("每年8月第3个周五", RecurrenceParser.group(listOf(ordinal), today = today).single().ruleLabel)
        assertEquals("每年1月、8月7日、21日", RecurrenceParser.group(listOf(multi), today = today).single().ruleLabel)
    }

    @Test
    fun combinedWeekdaySetPositionUsesSafeCustomDescription() {
        val sample = event(today, "工作日", "FREQ=MONTHLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=-1")
        assertEquals("每月（按自定义规则）", RecurrenceParser.group(listOf(sample), today = today).single().ruleLabel)
    }

    @Test
    fun lowercaseRuleIsAccepted() {
        val sample = event(today, "提醒", "freq=monthly;byday=1fr")
        val series = RecurrenceParser.group(listOf(sample), today = today).single()
        assertEquals(RecurrenceKind.MONTHLY, series.kind)
        assertEquals("每月第一个周五", series.ruleLabel)
    }

    @Test
    fun ongoingAllDayEventCountsAsToday() {
        val sample = event(today.minusDays(1), "旅行").copy(
            endMs = today.plusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        val series = RecurrenceParser.group(listOf(sample), today = today).single()
        assertEquals(0, series.daysUntil(today))
        assertEquals("进行中", series.daysLabel(today))
        assertTrue(sample.occursOn(today))
        assertEquals("8月20日–8月22日 · 全天", sample.dateTimeLabel(today))
    }

    @Test
    fun allDayExclusiveEndIsNotCountedAsToday() {
        val ended = event(today.minusDays(1), "昨天")
        assertFalse(ended.occursOn(today))
        assertTrue(RecurrenceParser.group(listOf(ended), today = today).isEmpty())
    }

    @Test
    fun allDayRangeUsesCalendarDatesInBothTimezones() {
        for (zone in listOf(ZoneId.of("Asia/Shanghai"), ZoneId.of("America/Los_Angeles"))) {
            val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            assertTrue(event(today, "今天").overlapsRange(from, to, zone))
            assertFalse(event(today.minusDays(1), "昨天").overlapsRange(from, to, zone))
            assertFalse(event(today.plusDays(1), "明天").overlapsRange(from, to, zone))
        }
    }

    @Test
    fun timedRangeAndMidnightEndAreExclusive() {
        val zone = ZoneOffset.UTC
        val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val ended = event(today.minusDays(1), "昨天").copy(allDay = false)
        val future = event(today.plusDays(1), "明天").copy(allDay = false)
        val spanning = ended.copy(endMs = from + 3_600_000)
        assertFalse(ended.overlapsRange(from, to, zone))
        assertFalse(ended.occursOn(today, zone))
        assertFalse(future.overlapsRange(from, to, zone))
        assertTrue(spanning.overlapsRange(from, to, zone))
        assertTrue(spanning.occursOn(today, zone))
    }

    @Test
    fun differentNextYearDatesRemainVisibleInDetails() {
        val january = event(LocalDate.of(2027, 1, 15), "年费")
        val september = event(LocalDate.of(2027, 9, 15), "年费")
        assertEquals("明年1月15日 · 全天", january.dateTimeLabel(today))
        assertEquals("明年9月15日 · 全天", september.dateTimeLabel(today))
    }

    @Test
    fun unsupportedYearDayIsNotInventedAsFixedMonthDay() {
        val sample = event(today, "年会", "FREQ=YEARLY;BYYEARDAY=233")
        assertEquals("重复（按自定义规则）", RecurrenceParser.group(listOf(sample), today = today).single().ruleLabel)
    }

    @Test
    fun dailyWeekdaySelectorIsShown() {
        val sample = event(today, "工作日", "FREQ=DAILY;BYDAY=MO,TU,WE,TH,FR")
        assertEquals("每天（周一、二、三、四、五）", RecurrenceParser.group(listOf(sample), today = today).single().ruleLabel)
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
