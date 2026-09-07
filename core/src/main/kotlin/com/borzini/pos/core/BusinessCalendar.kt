package com.borzini.pos.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

data class DateRange(val startInclusive: Instant, val endExclusive: Instant)

enum class StatsPeriodPreset { TODAY, YESTERDAY, THIS_WEEK, THIS_MONTH, CUSTOM }

/**
 * All "which day/week/month is this" decisions go through one place, always anchored to the
 * coffee shop's configured [ZoneId] (not the device's default zone, and never using default
 * Locale week rules, since the spec requires the week to run Monday -> Sunday everywhere).
 */
object BusinessCalendar {

    fun today(zone: ZoneId, now: Instant = Instant.now()): LocalDate = LocalDate.ofInstant(now, zone)

    fun dayRange(date: LocalDate, zone: ZoneId): DateRange {
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        return DateRange(start, end)
    }

    /** Monday..Sunday (inclusive) week containing [date], regardless of device Locale. */
    fun weekDates(date: LocalDate): List<LocalDate> {
        val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (0..6).map { monday.plusDays(it.toLong()) }
    }

    fun weekRange(date: LocalDate, zone: ZoneId): DateRange {
        val days = weekDates(date)
        val start = days.first().atStartOfDay(zone).toInstant()
        val end = days.last().plusDays(1).atStartOfDay(zone).toInstant()
        return DateRange(start, end)
    }

    fun monthRange(yearMonth: YearMonth, zone: ZoneId): DateRange {
        val start = yearMonth.atDay(1).atStartOfDay(zone).toInstant()
        val end = yearMonth.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()
        return DateRange(start, end)
    }

    fun customRange(startInclusive: LocalDate, endInclusive: LocalDate, zone: ZoneId): DateRange {
        require(!endInclusive.isBefore(startInclusive)) { "end before start" }
        val start = startInclusive.atStartOfDay(zone).toInstant()
        val end = endInclusive.plusDays(1).atStartOfDay(zone).toInstant()
        return DateRange(start, end)
    }

    fun resolve(
        preset: StatsPeriodPreset,
        zone: ZoneId,
        now: Instant = Instant.now(),
        customStart: LocalDate? = null,
        customEnd: LocalDate? = null,
    ): DateRange {
        val todayDate = today(zone, now)
        return when (preset) {
            StatsPeriodPreset.TODAY -> dayRange(todayDate, zone)
            StatsPeriodPreset.YESTERDAY -> dayRange(todayDate.minusDays(1), zone)
            StatsPeriodPreset.THIS_WEEK -> weekRange(todayDate, zone)
            StatsPeriodPreset.THIS_MONTH -> monthRange(YearMonth.from(todayDate), zone)
            StatsPeriodPreset.CUSTOM -> {
                requireNotNull(customStart) { "customStart required" }
                requireNotNull(customEnd) { "customEnd required" }
                customRange(customStart, customEnd, zone)
            }
        }
    }

    fun instantToLocalDate(instant: Instant, zone: ZoneId): LocalDate =
        ZonedDateTime.ofInstant(instant, zone).toLocalDate()
}
