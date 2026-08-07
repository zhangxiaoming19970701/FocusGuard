package com.focusguard.app.domain

import com.focusguard.app.util.LockTypes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistedLockValidatorTest {
    @Test
    fun `deleted schedule invalidates persisted schedule lock`() {
        assertFalse(valid(lockType = LockTypes.SCHEDULE, scheduleMatchesLock = false))
    }

    @Test
    fun `unchanged active schedule keeps persisted lock`() {
        assertTrue(valid(lockType = LockTypes.SCHEDULE, scheduleMatchesLock = true))
    }

    @Test
    fun `raising daily limit invalidates obsolete daily lock`() {
        assertFalse(
            valid(
                lockType = LockTypes.DAILY,
                dailyEnabled = true,
                dailyUsedSec = 300L,
                dailyLimitSec = 600L
            )
        )
    }

    @Test
    fun `disabling continuous limit invalidates break lock`() {
        assertFalse(valid(lockType = LockTypes.BREAK, continuousEnabled = false))
    }

    @Test
    fun `disabling whole rule invalidates every lock`() {
        assertFalse(valid(lockType = LockTypes.SCHEDULE, ruleEnabled = false, scheduleMatchesLock = true))
    }

    private fun valid(
        lockType: String,
        appEnabled: Boolean = true,
        ruleEnabled: Boolean = true,
        scheduleMatchesLock: Boolean = false,
        dailyEnabled: Boolean = false,
        dailyUsedSec: Long = 0L,
        dailyLimitSec: Long = 0L,
        continuousEnabled: Boolean = true
    ): Boolean = PersistedLockValidator.isValid(
        lockType = lockType,
        appEnabled = appEnabled,
        ruleEnabled = ruleEnabled,
        scheduleMatchesLock = scheduleMatchesLock,
        dailyEnabled = dailyEnabled,
        dailyUsedSec = dailyUsedSec,
        dailyLimitSec = dailyLimitSec,
        continuousEnabled = continuousEnabled
    )
}
