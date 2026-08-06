package com.focusguard.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object DateBoundary {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun dateKey(wallMillis: Long, resetMinute: Int, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val zdt = Instant.ofEpochMilli(wallMillis).atZone(zoneId).minusMinutes(resetMinute.toLong())
        return zdt.toLocalDate().format(formatter)
    }

    fun nextResetWall(wallMillis: Long, resetMinute: Int, zoneId: ZoneId = ZoneId.systemDefault()): Long {
        val now = Instant.ofEpochMilli(wallMillis).atZone(zoneId)
        val resetHour = resetMinute / 60
        val resetMin = resetMinute % 60
        var candidate = now.toLocalDate().atTime(resetHour, resetMin).atZone(zoneId)
        if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
        return candidate.toInstant().toEpochMilli()
    }

    fun dateKeyDaysAgo(days: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        LocalDate.now(zoneId).minusDays(days).format(formatter)

    fun wallAt(dateTime: ZonedDateTime): Long = dateTime.toInstant().toEpochMilli()
}
