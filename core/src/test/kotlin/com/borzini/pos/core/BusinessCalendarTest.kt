package com.borzini.pos.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class BusinessCalendarTest {

    private val moscow = ZoneId.of("Europe/Moscow")

    @Test
    fun `scenario 12 - week always starts Monday and ends Sunday regardless of the date inside it`() {
        val wednesday = LocalDate.of(2026, 9, 9) // a Wednesday
        val days = BusinessCalendar.weekDates(wednesday)
        assertEquals(7, days.size)
        assertEquals(DayOfWeek.MONDAY, days.first().dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, days.last().dayOfWeek)
        assertEquals(LocalDate.of(2026, 9, 7), days.first())
        assertEquals(LocalDate.of(2026, 9, 13), days.last())
    }

    @Test
    fun `a day near midnight is assigned to the coffee shop's configured timezone, not UTC`() {
        // 2026-09-07 23:30 Moscow time (UTC+3) is 2026-09-07 20:30 UTC - same calendar day only in Moscow's zone.
        val instant = Instant.parse("2026-09-07T20:30:00Z")
        assertEquals(LocalDate.of(2026, 9, 7), BusinessCalendar.instantToLocalDate(instant, moscow))
        assertEquals(LocalDate.of(2026, 9, 7), BusinessCalendar.instantToLocalDate(instant, ZoneOffset.UTC))

        // But 2026-09-07 22:30 UTC is already 2026-09-08 01:30 in Moscow - the day boundary differs by zone.
        val lateInstant = Instant.parse("2026-09-07T22:30:00Z")
        assertEquals(LocalDate.of(2026, 9, 8), BusinessCalendar.instantToLocalDate(lateInstant, moscow))
        assertEquals(LocalDate.of(2026, 9, 7), BusinessCalendar.instantToLocalDate(lateInstant, ZoneOffset.UTC))
    }

    @Test
    fun `week range spans exactly Monday 00-00 to the following Monday 00-00 in the configured zone`() {
        val range = BusinessCalendar.weekRange(LocalDate.of(2026, 9, 9), moscow)
        assertEquals(LocalDate.of(2026, 9, 7).atStartOfDay(moscow).toInstant(), range.startInclusive)
        assertEquals(LocalDate.of(2026, 9, 14).atStartOfDay(moscow).toInstant(), range.endExclusive)
    }
}
