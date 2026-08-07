package com.focusguard.app.domain

import com.focusguard.app.util.LockTypes

object PersistedLockValidator {
    fun isValid(
        lockType: String,
        appEnabled: Boolean,
        ruleEnabled: Boolean,
        scheduleMatchesLock: Boolean,
        dailyEnabled: Boolean,
        dailyUsedSec: Long,
        dailyLimitSec: Long,
        continuousEnabled: Boolean
    ): Boolean {
        if (!appEnabled || !ruleEnabled) return false
        return when (lockType) {
            LockTypes.SCHEDULE -> scheduleMatchesLock
            LockTypes.DAILY -> dailyEnabled && dailyUsedSec >= dailyLimitSec
            LockTypes.BREAK -> continuousEnabled
            else -> false
        }
    }
}
