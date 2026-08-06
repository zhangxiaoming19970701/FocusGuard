package com.focusguard.app.system

import android.content.Context
import android.content.Intent
import com.focusguard.app.data.db.LockStateEntity
import com.focusguard.app.data.repo.FocusGuardRepository
import com.focusguard.app.domain.DateBoundary
import com.focusguard.app.domain.RuleDecision
import com.focusguard.app.ui.BlockActivity
import com.focusguard.app.util.LockTypes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlockCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: FocusGuardRepository,
    private val clock: ClockProvider
) {
    private val mutex = Mutex()
    private var lastLaunchElapsed = 0L

    suspend fun block(
        decision: RuleDecision,
        packageName: String,
        appLabel: String,
        resetMinute: Int,
        performHome: () -> Unit
    ) = mutex.withLock {
        val nowWall = clock.wallMillis()
        val nowElapsed = clock.elapsedMillis()
        val lock = when (decision) {
            is RuleDecision.BlockBreak -> LockStateEntity(
                lockType = LockTypes.BREAK,
                triggerPackage = packageName,
                triggerLabel = appLabel,
                startWall = nowWall,
                endWall = nowWall + decision.durationSec * 1000L,
                endElapsed = nowElapsed + decision.durationSec * 1000L,
                bootCount = clock.bootCount(),
                globalScope = decision.global,
                reason = decision.reason
            )
            is RuleDecision.BlockDaily -> {
                val end = DateBoundary.nextResetWall(nowWall, resetMinute)
                LockStateEntity(
                    lockType = LockTypes.DAILY,
                    triggerPackage = packageName,
                    triggerLabel = appLabel,
                    startWall = nowWall,
                    endWall = end,
                    endElapsed = nowElapsed + (end - nowWall),
                    bootCount = clock.bootCount(),
                    globalScope = false,
                    reason = decision.reason,
                    nextAllowedText = "下次重置后可用"
                )
            }
            is RuleDecision.BlockSchedule -> {
                val duration = (decision.endWall - nowWall).coerceAtLeast(1_000L)
                LockStateEntity(
                    lockType = LockTypes.SCHEDULE,
                    triggerPackage = packageName,
                    triggerLabel = appLabel,
                    startWall = nowWall,
                    endWall = decision.endWall,
                    endElapsed = nowElapsed + duration,
                    bootCount = clock.bootCount(),
                    globalScope = false,
                    reason = decision.reason,
                    nextAllowedText = decision.nextAllowedText
                )
            }
            else -> return@withLock
        }
        repository.setLock(lock)
        enforce(lock, performHome)
    }

    suspend fun enforceExisting(lock: LockStateEntity, performHome: () -> Unit) = mutex.withLock {
        enforce(lock, performHome)
    }

    private suspend fun enforce(lock: LockStateEntity, performHome: () -> Unit) {
        withContext(Dispatchers.Main.immediate) { performHome() }
        val now = clock.elapsedMillis()
        if (now - lastLaunchElapsed < 400L) return
        lastLaunchElapsed = now
        delay(80L)
        withContext(Dispatchers.Main.immediate) {
            val intent = Intent(context, BlockActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
                putExtra(BlockActivity.EXTRA_LOCK_TYPE, lock.lockType)
            }
            context.startActivity(intent)
        }
    }
}
