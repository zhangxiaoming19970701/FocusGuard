package com.focusguard.app.system

import android.content.Context
import com.focusguard.app.data.db.LockStateEntity
import com.focusguard.app.data.db.RuleEntity
import com.focusguard.app.data.db.UsageSessionEntity
import com.focusguard.app.data.prefs.SettingsStore
import com.focusguard.app.data.repo.FocusGuardRepository
import com.focusguard.app.domain.DateBoundary
import com.focusguard.app.domain.PersistedLockValidator
import com.focusguard.app.domain.RuleContext
import com.focusguard.app.domain.ScheduleMatcher
import com.focusguard.app.domain.RuleDecision
import com.focusguard.app.domain.RuleEngine
import com.focusguard.app.util.EndReasons
import com.focusguard.app.util.EventCodes
import com.focusguard.app.util.LockTypes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForegroundOrchestrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: FocusGuardRepository,
    private val settingsStore: SettingsStore,
    private val ruleEngine: RuleEngine,
    private val blockCoordinator: BlockCoordinator,
    private val clock: ClockProvider
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    private data class ActiveUse(
        val packageName: String,
        val appLabel: String,
        val rule: RuleEntity,
        val session: UsageSessionEntity,
        val foregroundStartElapsed: Long,
        val foregroundStartWall: Long,
        val warnedThresholds: Set<Long> = emptySet()
    )

    private data class PendingUse(
        val active: ActiveUse,
        val leftElapsed: Long,
        val closeJob: Job
    )

    private var active: ActiveUse? = null
    private val pending = mutableMapOf<String, PendingUse>()
    private var monitorJob: Job? = null
    private var lastForegroundPackage: String? = null
    private var performHomeAction: () -> Unit = { }

    init {
        scope.launch {
            repository.configurationChanges.collect { packageName ->
                mutex.withLock { handleConfigurationChanged(packageName) }
            }
        }
    }

    fun onForegroundPackage(packageName: String, performHome: () -> Unit) {
        scope.launch {
            mutex.withLock {
                handleForeground(packageName, performHome)
            }
        }
    }

    fun onScreenOff() {
        scope.launch {
            mutex.withLock {
                closeActiveImmediately(EndReasons.SCREEN_OFF)
                closeAllPending(EndReasons.SCREEN_OFF)
            }
        }
    }

    fun onServiceStopping() {
        scope.launch {
            mutex.withLock {
                closeActiveImmediately(EndReasons.SERVICE_STOP)
                closeAllPending(EndReasons.SERVICE_STOP)
            }
        }
    }

    private suspend fun handleForeground(packageName: String, performHome: () -> Unit) {
        performHomeAction = performHome
        if (packageName == lastForegroundPackage && active?.packageName == packageName) return
        lastForegroundPackage = packageName

        if (active?.packageName != packageName) leaveActiveForMerge()
        val settings = settingsStore.settings.first()
        if (!settings.monitoringEnabled) return

        val existingLock = validatedExistingLock()
        if (existingLock != null) {
            if (
                (existingLock.globalScope || existingLock.triggerPackage == packageName) &&
                !SystemWhitelist.isSafetyCritical(context, packageName) &&
                !repository.validOverride(packageName)
            ) {
                blockCoordinator.enforceExisting(existingLock, performHome)
                return
            }
        }

        if (SystemWhitelist.isSafetyCritical(context, packageName)) return
        val app = repository.getManagedApp(packageName) ?: return
        if (!app.enabled || app.isWhitelist) return
        val rule = repository.getRule(packageName) ?: return
        if (!rule.enabled) return

        // Data minimization: only audit foreground entries for apps the administrator chose to manage.
        repository.audit(EventCodes.APP_FOREGROUND, packageName)
        startOrResume(packageName, app.appLabel, rule)
        evaluateActive(performHome)
    }

    private suspend fun validatedExistingLock(): LockStateEntity? {
        val lock = repository.getLock() ?: return null
        if (repository.remainingLockMillis(lock) <= 0L) {
            repository.clearLock("NATURAL_END")
            return null
        }

        val triggerPackage = lock.triggerPackage
        if (triggerPackage.isNullOrBlank()) {
            repository.clearLock("INVALID_TRIGGER")
            return null
        }

        val app = repository.getManagedApp(triggerPackage)
        val rule = repository.getRule(triggerPackage)
        val nowWall = clock.wallMillis()
        val settings = settingsStore.settings.first()
        val scheduleMatchesLock = if (lock.lockType == LockTypes.SCHEDULE) {
            val evaluation = ScheduleMatcher.evaluate(repository.getSchedules(triggerPackage), nowWall)
            evaluation.blocked && evaluation.endWall == lock.endWall
        } else {
            false
        }
        val dailyUsed = if (lock.lockType == LockTypes.DAILY) {
            repository.dailyUsed(triggerPackage, nowWall, settings.resetMinute)
        } else {
            0L
        }
        val valid = PersistedLockValidator.isValid(
            lockType = lock.lockType,
            appEnabled = app?.let { it.enabled && !it.isWhitelist } == true,
            ruleEnabled = rule?.enabled == true,
            scheduleMatchesLock = scheduleMatchesLock,
            dailyEnabled = rule?.dailyEnabled == true,
            dailyUsedSec = dailyUsed,
            dailyLimitSec = rule?.dailyLimitSec ?: Long.MAX_VALUE,
            continuousEnabled = rule?.continuousEnabled == true
        )
        if (!valid) {
            repository.clearLock("RULE_REEVALUATED")
            return null
        }
        return lock
    }

    private suspend fun handleConfigurationChanged(packageName: String) {
        validatedExistingLock()

        pending.remove(packageName)?.let { item ->
            item.closeJob.cancel()
            repository.checkpointSession(
                item.active.session,
                segmentSec = 0,
                resetMinute = settingsStore.settings.first().resetMinute,
                close = true,
                endReason = EndReasons.RULE_CHANGED
            )
        }

        val current = active ?: return
        if (current.packageName != packageName) return
        val app = repository.getManagedApp(packageName)
        val rule = repository.getRule(packageName)
        if (app == null || !app.enabled || app.isWhitelist || rule == null || !rule.enabled) {
            closeActiveImmediately(EndReasons.RULE_CHANGED)
            return
        }
        active = current.copy(appLabel = app.appLabel, rule = rule)
        evaluateActive(performHomeAction)
    }

    private suspend fun startOrResume(packageName: String, label: String, rule: RuleEntity) {
        val nowElapsed = clock.elapsedMillis()
        val nowWall = clock.wallMillis()
        val waiting = pending.remove(packageName)
        if (waiting != null) {
            waiting.closeJob.cancel()
            if (nowElapsed - waiting.leftElapsed <= rule.mergeGapSec * 1000L) {
                active = waiting.active.copy(
                    appLabel = label,
                    rule = rule,
                    foregroundStartElapsed = nowElapsed,
                    foregroundStartWall = nowWall
                )
                scheduleMonitor()
                return
            }
            repository.checkpointSession(
                waiting.active.session,
                segmentSec = 0,
                resetMinute = settingsStore.settings.first().resetMinute,
                close = true,
                endReason = EndReasons.APP_SWITCH
            )
        }
        val session = repository.startSession(packageName)
        active = ActiveUse(packageName, label, rule, session, nowElapsed, nowWall)
        scheduleMonitor()
    }

    private suspend fun leaveActiveForMerge() {
        val current = active ?: return
        monitorJob?.cancel()
        monitorJob = null
        val nowElapsed = clock.elapsedMillis()
        val nowWall = clock.wallMillis()
        val segmentSec = ((nowElapsed - current.foregroundStartElapsed) / 1000L).coerceAtLeast(0L)
        val reset = settingsStore.settings.first().resetMinute
        val updated = repository.checkpointSession(current.session, segmentSec, reset, close = false)
        val paused = current.copy(
            session = updated,
            foregroundStartElapsed = nowElapsed,
            foregroundStartWall = nowWall
        )
        val job = scope.launch {
            delay(current.rule.mergeGapSec * 1000L)
            mutex.withLock {
                val item = pending.remove(current.packageName) ?: return@withLock
                repository.checkpointSession(
                    item.active.session,
                    segmentSec = 0,
                    resetMinute = settingsStore.settings.first().resetMinute,
                    close = true,
                    endReason = EndReasons.APP_SWITCH
                )
            }
        }
        pending[current.packageName] = PendingUse(paused, nowElapsed, job)
        active = null
    }

    private suspend fun closeActiveImmediately(reason: String) {
        val current = active ?: return
        monitorJob?.cancel()
        monitorJob = null
        val segment = ((clock.elapsedMillis() - current.foregroundStartElapsed) / 1000L).coerceAtLeast(0L)
        repository.checkpointSession(
            current.session,
            segment,
            settingsStore.settings.first().resetMinute,
            close = true,
            endReason = reason
        )
        active = null
    }

    private suspend fun closeAllPending(reason: String) {
        val values = pending.values.toList()
        pending.clear()
        values.forEach {
            it.closeJob.cancel()
            repository.checkpointSession(
                it.active.session,
                segmentSec = 0,
                resetMinute = settingsStore.settings.first().resetMinute,
                close = true,
                endReason = reason
            )
        }
    }

    private fun scheduleMonitor() {
        monitorJob?.cancel()
        val snapshot = active ?: return
        monitorJob = scope.launch {
            val delayMs = mutex.withLock { calculateNextDelayMillis(snapshot) }
            delay(delayMs)
            mutex.withLock {
                if (active?.packageName == snapshot.packageName) {
                    // The scheduled monitor is the current coroutine. Clear the reference before
                    // evaluation so the blocking path cannot cancel itself before persisting the
                    // session and launching BlockActivity.
                    monitorJob = null
                    evaluateActive(performHomeAction)
                }
            }
        }
    }

    private suspend fun calculateNextDelayMillis(snapshot: ActiveUse): Long {
        val nowElapsed = clock.elapsedMillis()
        val segmentSec = ((nowElapsed - snapshot.foregroundStartElapsed) / 1000L).coerceAtLeast(0L)
        val continuous = snapshot.session.accumulatedSec + segmentSec
        val settings = settingsStore.settings.first()
        val nowWall = clock.wallMillis()
        val dailyCommitted = repository.dailyUsed(snapshot.packageName, nowWall, settings.resetMinute)
        val daily = dailyCommitted + segmentSec
        val candidates = mutableListOf<Long>(60L - (segmentSec % 60L))
        val scheduleBoundary = ScheduleMatcher.nextBoundaryWall(repository.getSchedules(snapshot.packageName), nowWall)
        if (scheduleBoundary != null) {
            candidates += ((scheduleBoundary - nowWall + 999L) / 1000L).coerceAtLeast(1L)
        }
        val resetBoundary = DateBoundary.nextResetWall(nowWall, settings.resetMinute)
        candidates += ((resetBoundary - nowWall + 999L) / 1000L).coerceAtLeast(1L)
        repository.validOverrideEnd(snapshot.packageName)?.let { overrideEnd ->
            candidates += ((overrideEnd - nowWall + 999L) / 1000L).coerceAtLeast(1L)
        }

        if (snapshot.rule.continuousEnabled) {
            val remain = snapshot.rule.continuousLimitSec - continuous
            if (remain > 0) candidates += remain
            listOf(snapshot.rule.warningFirstSec, snapshot.rule.warningSecondSec).forEach { threshold ->
                if (threshold > 0 && threshold !in snapshot.warnedThresholds && remain > threshold) {
                    candidates += remain - threshold
                }
            }
        }
        if (snapshot.rule.dailyEnabled) {
            val remain = snapshot.rule.dailyLimitSec - daily
            if (remain > 0) candidates += remain
        }
        val seconds = candidates.filter { it > 0 }.minOrNull() ?: 1L
        return (seconds * 1000L).coerceIn(250L, 60_000L)
    }

    private suspend fun evaluateActive(performHome: () -> Unit) {
        var current = active ?: return
        val app = repository.getManagedApp(current.packageName)
        val latestRule = repository.getRule(current.packageName)
        if (app == null || !app.enabled || app.isWhitelist || latestRule == null || !latestRule.enabled) {
            closeActiveImmediately(EndReasons.RULE_CHANGED)
            return
        }
        if (current.rule != latestRule || current.appLabel != app.appLabel) {
            current = current.copy(appLabel = app.appLabel, rule = latestRule)
            active = current
        }
        val nowElapsed = clock.elapsedMillis()
        val nowWall = clock.wallMillis()
        val settings = settingsStore.settings.first()

        if (DateBoundary.dateKey(current.foregroundStartWall, settings.resetMinute) !=
            DateBoundary.dateKey(nowWall, settings.resetMinute)
        ) {
            val preResetSec = ((nowElapsed - current.foregroundStartElapsed) / 1000L).coerceAtLeast(0L)
            val updated = repository.checkpointSession(
                current.session,
                preResetSec,
                settings.resetMinute,
                close = false,
                accountingWall = nowWall - 1_000L
            )
            current = current.copy(
                session = updated,
                foregroundStartElapsed = nowElapsed,
                foregroundStartWall = nowWall
            )
            active = current
        }

        val segmentSec = ((nowElapsed - current.foregroundStartElapsed) / 1000L).coerceAtLeast(0L)
        val continuous = current.session.accumulatedSec + segmentSec
        val dailyCommitted = repository.dailyUsed(current.packageName, nowWall, settings.resetMinute)
        val daily = dailyCommitted + segmentSec

        if (current.rule.continuousEnabled) {
            val remaining = current.rule.continuousLimitSec - continuous
            val newlyWarned = current.warnedThresholds.toMutableSet()
            listOf(current.rule.warningFirstSec, current.rule.warningSecondSec).forEach { threshold ->
                if (threshold > 0 && remaining in 1..threshold && threshold !in newlyWarned) {
                    NotificationHelper.showWarning(context, current.appLabel, remaining)
                    repository.audit(EventCodes.WARNING_SHOWN, current.packageName, "remaining=$remaining")
                    newlyWarned += threshold
                }
            }
            if (newlyWarned != current.warnedThresholds) {
                current = current.copy(warnedThresholds = newlyWarned)
                active = current
            }
        }

        val decision = ruleEngine.evaluate(
            RuleContext(
                packageName = current.packageName,
                app = repository.getManagedApp(current.packageName),
                rule = current.rule,
                schedules = repository.getSchedules(current.packageName),
                nowWall = nowWall,
                dailyUsedSec = daily,
                continuousUsedSec = continuous,
                currentLock = repository.getLock(),
                hasOverride = repository.validOverride(current.packageName),
                isSystemWhitelist = false
            )
        )

        when (decision) {
            RuleDecision.Allow, is RuleDecision.Warn -> {
                if (segmentSec >= 60L) {
                    val updated = repository.checkpointSession(
                        current.session,
                        segmentSec,
                        settings.resetMinute,
                        close = false
                    )
                    active = current.copy(
                        session = updated,
                        foregroundStartElapsed = nowElapsed,
                        foregroundStartWall = nowWall
                    )
                }
                scheduleMonitor()
            }
            is RuleDecision.BlockBreak,
            is RuleDecision.BlockDaily,
            is RuleDecision.BlockSchedule -> {
                monitorJob?.cancel()
                monitorJob = null
                repository.checkpointSession(
                    current.session,
                    segmentSec,
                    settings.resetMinute,
                    close = true,
                    endReason = EndReasons.LIMIT_REACHED
                )
                active = null
                blockCoordinator.block(
                    decision,
                    current.packageName,
                    current.appLabel,
                    settings.resetMinute,
                    performHome
                )
            }
        }
    }
}
