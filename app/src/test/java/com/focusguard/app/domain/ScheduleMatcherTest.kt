package com.focusguard.app.domain

import com.focusguard.app.data.db.ScheduleWindowEntity
import com.focusguard.app.util.ScheduleModes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleMatcherTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun normalBlockedWindow_blocksInsideAndAllowsOutside() {
        val window = ScheduleWindowEntity(
            packageName = "game.test",
            mode = ScheduleModes.BLOCK,
            daysMask = ScheduleMatcher.dayBit(DayOfWeek.MONDAY),
            startMinute = 19 * 60,
            endMinute = 21 * 60 + 30
        )
        val inside = ZonedDateTime.of(2026, 8, 3, 20, 0, 0, 0, zone)
        val outside = ZonedDateTime.of(2026, 8, 3, 22, 0, 0, 0, zone)
        assertTrue(ScheduleMatcher.evaluate(listOf(window), inside.toInstant().toEpochMilli(), zone).blocked)
        assertFalse(ScheduleMatcher.evaluate(listOf(window), outside.toInstant().toEpochMilli(), zone).blocked)
    }

    @Test
    fun crossMidnightWindow_usesPreviousDayForAfterMidnight() {
        val window = ScheduleWindowEntity(
            packageName = "social.test",
            mode = ScheduleModes.BLOCK,
            daysMask = ScheduleMatcher.dayBit(DayOfWeek.MONDAY),
            startMinute = 22 * 60,
            endMinute = 7 * 60
        )
        val mondayLate = ZonedDateTime.of(2026, 8, 3, 23, 0, 0, 0, zone)
        val tuesdayEarly = ZonedDateTime.of(2026, 8, 4, 6, 30, 0, 0, zone)
        val tuesdayLate = ZonedDateTime.of(2026, 8, 4, 8, 0, 0, 0, zone)
        assertTrue(ScheduleMatcher.evaluate(listOf(window), mondayLate.toInstant().toEpochMilli(), zone).blocked)
        assertTrue(ScheduleMatcher.evaluate(listOf(window), tuesdayEarly.toInstant().toEpochMilli(), zone).blocked)
        assertFalse(ScheduleMatcher.evaluate(listOf(window), tuesdayLate.toInstant().toEpochMilli(), zone).blocked)
    }

    @Test
    fun allowWindow_blocksOutsideAllowedPeriod() {
        val window = ScheduleWindowEntity(
            packageName = "game.test",
            mode = ScheduleModes.ALLOW,
            daysMask = ScheduleMatcher.dayBit(DayOfWeek.SATURDAY),
            startMinute = 14 * 60,
            endMinute = 16 * 60
        )
        val inside = ZonedDateTime.of(2026, 8, 8, 15, 0, 0, 0, zone)
        val outside = ZonedDateTime.of(2026, 8, 8, 17, 0, 0, 0, zone)
        assertFalse(ScheduleMatcher.evaluate(listOf(window), inside.toInstant().toEpochMilli(), zone).blocked)
        assertTrue(ScheduleMatcher.evaluate(listOf(window), outside.toInstant().toEpochMilli(), zone).blocked)
    }
}
