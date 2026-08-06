package com.focusguard.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class DateBoundaryTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun fourAmBoundary_assignsEarlyMorningToPreviousDate() {
        val wall = ZonedDateTime.of(2026, 8, 6, 2, 30, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("2026-08-05", DateBoundary.dateKey(wall, 4 * 60, zone))
    }

    @Test
    fun midnightBoundary_usesCalendarDate() {
        val wall = ZonedDateTime.of(2026, 8, 6, 2, 30, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("2026-08-06", DateBoundary.dateKey(wall, 0, zone))
    }
}
