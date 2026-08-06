package com.focusguard.app.domain

import com.focusguard.app.data.db.ScheduleWindowEntity
import com.focusguard.app.util.ScheduleModes
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object ScheduleMatcher {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun evaluate(
        windows: List<ScheduleWindowEntity>,
        nowWall: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ScheduleEvaluation {
        val enabled = windows.filter { it.enabled }
        if (enabled.isEmpty()) return ScheduleEvaluation(false)

        val now = Instant.ofEpochMilli(nowWall).atZone(zoneId)
        val blocking = enabled.filter { it.mode == ScheduleModes.BLOCK }
        val allowing = enabled.filter { it.mode == ScheduleModes.ALLOW }

        val activeBlock = blocking.firstOrNull { contains(it, now) }
        if (activeBlock != null) {
            val end = activeOccurrenceEnd(activeBlock, now)
            return ScheduleEvaluation(
                blocked = true,
                endWall = end.toInstant().toEpochMilli(),
                reason = "该应用当前处于禁用时段",
                nextAllowedText = "预计 ${end.format(timeFormatter)} 后可用"
            )
        }

        if (allowing.isNotEmpty() && allowing.none { contains(it, now) }) {
            val next = nextStart(allowing, now)
            return ScheduleEvaluation(
                blocked = true,
                endWall = next.toInstant().toEpochMilli(),
                reason = "当前不在允许使用时段",
                nextAllowedText = "下次允许：${formatNext(next, now)}"
            )
        }
        return ScheduleEvaluation(false)
    }

    fun contains(window: ScheduleWindowEntity, now: ZonedDateTime): Boolean {
        val minute = now.hour * 60 + now.minute
        val todayBit = dayBit(now.dayOfWeek)
        val yesterdayBit = dayBit(now.minusDays(1).dayOfWeek)
        return if (window.startMinute < window.endMinute) {
            (window.daysMask and todayBit) != 0 && minute in window.startMinute until window.endMinute
        } else if (window.startMinute > window.endMinute) {
            ((window.daysMask and todayBit) != 0 && minute >= window.startMinute) ||
                ((window.daysMask and yesterdayBit) != 0 && minute < window.endMinute)
        } else {
            (window.daysMask and todayBit) != 0
        }
    }

    private fun activeOccurrenceEnd(window: ScheduleWindowEntity, now: ZonedDateTime): ZonedDateTime {
        val minute = now.hour * 60 + now.minute
        val endHour = window.endMinute / 60
        val endMin = window.endMinute % 60
        return when {
            window.startMinute < window.endMinute -> now.toLocalDate().atTime(endHour, endMin).atZone(now.zone)
            window.startMinute > window.endMinute && minute >= window.startMinute ->
                now.toLocalDate().plusDays(1).atTime(endHour, endMin).atZone(now.zone)
            window.startMinute > window.endMinute ->
                now.toLocalDate().atTime(endHour, endMin).atZone(now.zone)
            else -> now.plusDays(1).toLocalDate().atTime(endHour, endMin).atZone(now.zone)
        }
    }

    private fun nextStart(windows: List<ScheduleWindowEntity>, now: ZonedDateTime): ZonedDateTime {
        var best: ZonedDateTime? = null
        for (offset in 0..7) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            val bit = dayBit(date.dayOfWeek)
            for (window in windows) {
                if ((window.daysMask and bit) == 0) continue
                val candidate = date.atTime(window.startMinute / 60, window.startMinute % 60).atZone(now.zone)
                if (candidate.isAfter(now) && (best == null || candidate.isBefore(best))) best = candidate
            }
        }
        return best ?: now.plusDays(7)
    }

    private fun formatNext(next: ZonedDateTime, now: ZonedDateTime): String =
        if (next.toLocalDate() == now.toLocalDate()) next.format(timeFormatter)
        else "${next.dayOfWeek.chineseName()} ${next.format(timeFormatter)}"

    fun nextBoundaryWall(
        windows: List<ScheduleWindowEntity>,
        nowWall: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long? {
        val now = Instant.ofEpochMilli(nowWall).atZone(zoneId)
        var best: ZonedDateTime? = null
        for (offset in -1..7) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            val bit = dayBit(date.dayOfWeek)
            for (window in windows.filter { it.enabled }) {
                if ((window.daysMask and bit) == 0) continue
                val start = date.atTime(window.startMinute / 60, window.startMinute % 60).atZone(zoneId)
                val endDate = if (window.startMinute >= window.endMinute) date.plusDays(1) else date
                val end = endDate.atTime(window.endMinute / 60, window.endMinute % 60).atZone(zoneId)
                listOf(start, end).forEach { candidate ->
                    if (candidate.isAfter(now) && (best == null || candidate.isBefore(best))) best = candidate
                }
            }
        }
        return best?.toInstant()?.toEpochMilli()
    }

    fun dayBit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    private fun DayOfWeek.chineseName(): String = when (this) {
        DayOfWeek.MONDAY -> "周一"
        DayOfWeek.TUESDAY -> "周二"
        DayOfWeek.WEDNESDAY -> "周三"
        DayOfWeek.THURSDAY -> "周四"
        DayOfWeek.FRIDAY -> "周五"
        DayOfWeek.SATURDAY -> "周六"
        DayOfWeek.SUNDAY -> "周日"
    }
}
