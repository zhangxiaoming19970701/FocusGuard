package com.focusguard.app.domain

import com.focusguard.app.data.db.LockStateEntity
import com.focusguard.app.data.db.ManagedAppEntity
import com.focusguard.app.data.db.RuleEntity
import com.focusguard.app.data.db.ScheduleWindowEntity

sealed interface RuleDecision {
    data object Allow : RuleDecision
    data class Warn(val remainingSec: Long) : RuleDecision
    data class BlockBreak(val durationSec: Long, val global: Boolean, val reason: String) : RuleDecision
    data class BlockDaily(val endWall: Long, val reason: String) : RuleDecision
    data class BlockSchedule(val endWall: Long, val reason: String, val nextAllowedText: String) : RuleDecision
}

data class RuleContext(
    val packageName: String,
    val app: ManagedAppEntity?,
    val rule: RuleEntity?,
    val schedules: List<ScheduleWindowEntity>,
    val nowWall: Long,
    val dailyUsedSec: Long,
    val continuousUsedSec: Long,
    val currentLock: LockStateEntity?,
    val hasOverride: Boolean,
    val isSystemWhitelist: Boolean
)

data class ScheduleEvaluation(
    val blocked: Boolean,
    val endWall: Long = 0L,
    val reason: String = "",
    val nextAllowedText: String = ""
)
