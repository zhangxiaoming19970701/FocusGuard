package com.focusguard.app.domain

import com.focusguard.app.util.LockTypes
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuleEngine @Inject constructor() {
    fun evaluate(context: RuleContext): RuleDecision {
        if (context.isSystemWhitelist) return RuleDecision.Allow
        if (context.hasOverride) return RuleDecision.Allow

        val lock = context.currentLock
        if (lock != null &&
            lock.lockType == LockTypes.BREAK &&
            (lock.globalScope || lock.triggerPackage == context.packageName) &&
            lock.endWall > context.nowWall
        ) {
            return RuleDecision.BlockBreak(
                durationSec = ((lock.endWall - context.nowWall) / 1000L).coerceAtLeast(1L),
                global = lock.globalScope,
                reason = lock.reason
            )
        }

        val app = context.app ?: return RuleDecision.Allow
        val rule = context.rule ?: return RuleDecision.Allow
        if (!app.enabled || !rule.enabled) return RuleDecision.Allow

        val schedule = ScheduleMatcher.evaluate(context.schedules, context.nowWall)
        if (schedule.blocked) {
            return RuleDecision.BlockSchedule(
                endWall = schedule.endWall,
                reason = schedule.reason,
                nextAllowedText = schedule.nextAllowedText
            )
        }

        if (rule.dailyEnabled && context.dailyUsedSec >= rule.dailyLimitSec) {
            return RuleDecision.BlockDaily(
                endWall = Long.MAX_VALUE,
                reason = "今日使用时间已用完"
            )
        }

        if (rule.continuousEnabled && context.continuousUsedSec >= rule.continuousLimitSec) {
            return RuleDecision.BlockBreak(
                durationSec = rule.breakDurationSec,
                global = rule.globalBreak,
                reason = "已连续使用 ${rule.continuousLimitSec / 60} 分钟"
            )
        }

        if (rule.continuousEnabled) {
            val remaining = rule.continuousLimitSec - context.continuousUsedSec
            if (remaining == rule.warningFirstSec || remaining == rule.warningSecondSec) {
                return RuleDecision.Warn(remaining)
            }
        }
        return RuleDecision.Allow
    }
}
