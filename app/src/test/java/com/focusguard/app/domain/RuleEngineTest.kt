package com.focusguard.app.domain

import com.focusguard.app.data.db.ManagedAppEntity
import com.focusguard.app.data.db.RuleEntity
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {
    private val engine = RuleEngine()
    private val app = ManagedAppEntity("game.test", "Game")
    private val rule = RuleEntity(
        packageName = "game.test",
        continuousLimitSec = 120,
        dailyLimitSec = 180,
        breakDurationSec = 60
    )

    @Test
    fun overrideHasPriorityOverQuota() {
        val decision = engine.evaluate(
            baseContext(daily = 999, continuous = 999, override = true)
        )
        assertTrue(decision is RuleDecision.Allow)
    }

    @Test
    fun dailyQuotaBlocksBeforeContinuousRule() {
        val decision = engine.evaluate(baseContext(daily = 180, continuous = 120))
        assertTrue(decision is RuleDecision.BlockDaily)
    }

    @Test
    fun continuousLimitStartsBreak() {
        val decision = engine.evaluate(baseContext(daily = 100, continuous = 120))
        assertTrue(decision is RuleDecision.BlockBreak)
    }

    @Test
    fun unmanagedPackageIsAllowed() {
        val decision = engine.evaluate(baseContext(daily = 999, continuous = 999).copy(app = null, rule = null))
        assertTrue(decision is RuleDecision.Allow)
    }

    private fun baseContext(
        daily: Long,
        continuous: Long,
        override: Boolean = false
    ) = RuleContext(
        packageName = "game.test",
        app = app,
        rule = rule,
        schedules = emptyList(),
        nowWall = 1_700_000_000_000L,
        dailyUsedSec = daily,
        continuousUsedSec = continuous,
        currentLock = null,
        hasOverride = override,
        isSystemWhitelist = false
    )
}
