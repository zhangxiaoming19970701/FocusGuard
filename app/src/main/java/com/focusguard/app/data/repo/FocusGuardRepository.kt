package com.focusguard.app.data.repo

import androidx.room.withTransaction
import com.focusguard.app.data.db.AdminCredentialEntity
import com.focusguard.app.data.db.AuditEventEntity
import com.focusguard.app.data.db.DailyUsageEntity
import com.focusguard.app.data.db.FocusGuardDatabase
import com.focusguard.app.data.db.LockStateEntity
import com.focusguard.app.data.db.ManagedAppEntity
import com.focusguard.app.data.db.OverrideGrantEntity
import com.focusguard.app.data.db.RuleEntity
import com.focusguard.app.data.db.ScheduleWindowEntity
import com.focusguard.app.data.db.UsageSessionEntity
import com.focusguard.app.domain.DateBoundary
import com.focusguard.app.domain.PasswordHasher
import com.focusguard.app.system.ClockProvider
import com.focusguard.app.util.EventCodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FocusGuardRepository @Inject constructor(
    private val db: FocusGuardDatabase,
    private val hasher: PasswordHasher,
    private val clock: ClockProvider
) {
    private val _configurationChanges = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val configurationChanges: SharedFlow<String> = _configurationChanges.asSharedFlow()

    val managedApps: Flow<List<ManagedAppEntity>> = db.managedAppDao().observeAll()
    val rules: Flow<List<RuleEntity>> = db.ruleDao().observeAll()
    val lockState: Flow<LockStateEntity?> = db.lockStateDao().observe()
    val credential: Flow<AdminCredentialEntity?> = db.adminCredentialDao().observe()
    val recentAudit: Flow<List<AuditEventEntity>> = db.auditEventDao().observeRecent(150)
    fun auditSince(fromWall: Long): Flow<List<AuditEventEntity>> = db.auditEventDao().observeSince(fromWall)

    fun dailyUsage(dateKey: String): Flow<List<DailyUsageEntity>> = db.dailyUsageDao().observeForDate(dateKey)
    fun usageSince(minimumDateKey: String): Flow<List<DailyUsageEntity>> = db.dailyUsageDao().observeSince(minimumDateKey)
    fun schedules(packageName: String): Flow<List<ScheduleWindowEntity>> = db.scheduleDao().observeForPackage(packageName)

    suspend fun getManagedApp(packageName: String) = db.managedAppDao().get(packageName)
    suspend fun getRule(packageName: String) = db.ruleDao().get(packageName)
    suspend fun getSchedules(packageName: String) = db.scheduleDao().getEnabledForPackage(packageName)
    suspend fun getLock() = db.lockStateDao().get()
    suspend fun getOpenSession(packageName: String) = db.usageSessionDao().getOpen(packageName)

    suspend fun setManagedApp(app: ManagedAppEntity, selected: Boolean) {
        db.withTransaction {
            if (selected) {
                db.managedAppDao().upsert(app.copy(enabled = true, updatedAt = clock.wallMillis()))
                if (db.ruleDao().get(app.packageName) == null) {
                    db.ruleDao().upsert(RuleEntity(packageName = app.packageName))
                }
            } else {
                db.managedAppDao().delete(app.packageName)
            }
            db.auditEventDao().insert(
                AuditEventEntity(
                    eventType = EventCodes.RULE_CHANGED,
                    packageName = app.packageName,
                    detailCode = if (selected) "APP_MANAGED" else "APP_UNMANAGED"
                )
            )
        }
        _configurationChanges.emit(app.packageName)
    }

    suspend fun upsertRule(rule: RuleEntity) {
        db.withTransaction {
            db.ruleDao().upsert(rule.copy(updatedAt = clock.wallMillis()))
            db.auditEventDao().insert(
                AuditEventEntity(eventType = EventCodes.RULE_CHANGED, packageName = rule.packageName, detailCode = "RULE_UPSERT")
            )
        }
        _configurationChanges.emit(rule.packageName)
    }

    suspend fun upsertSchedule(window: ScheduleWindowEntity): Long {
        val id = db.withTransaction {
            val generated = db.scheduleDao().upsert(window)
            db.auditEventDao().insert(
                AuditEventEntity(eventType = EventCodes.RULE_CHANGED, packageName = window.packageName, detailCode = "SCHEDULE_UPSERT")
            )
            generated
        }
        _configurationChanges.emit(window.packageName)
        return id
    }

    suspend fun deleteSchedule(window: ScheduleWindowEntity) {
        db.withTransaction {
            db.scheduleDao().delete(window.id)
            db.auditEventDao().insert(
                AuditEventEntity(eventType = EventCodes.RULE_CHANGED, packageName = window.packageName, detailCode = "SCHEDULE_DELETE")
            )
        }
        _configurationChanges.emit(window.packageName)
    }

    suspend fun dailyUsed(packageName: String, wall: Long, resetMinute: Int): Long {
        val key = DateBoundary.dateKey(wall, resetMinute)
        return db.dailyUsageDao().get(key, packageName)?.usedSec ?: 0L
    }

    suspend fun addDailyUsage(packageName: String, deltaSec: Long, wall: Long, resetMinute: Int): Long {
        if (deltaSec <= 0) return dailyUsed(packageName, wall, resetMinute)
        return db.withTransaction {
            val key = DateBoundary.dateKey(wall, resetMinute)
            val current = db.dailyUsageDao().get(key, packageName)?.usedSec ?: 0L
            val updated = current + deltaSec
            db.dailyUsageDao().upsert(DailyUsageEntity(key, packageName, updated, clock.wallMillis()))
            updated
        }
    }

    suspend fun startSession(packageName: String): UsageSessionEntity {
        val nowElapsed = clock.elapsedMillis()
        val entity = UsageSessionEntity(
            packageName = packageName,
            bootCount = clock.bootCount(),
            startElapsed = nowElapsed,
            startWall = clock.wallMillis(),
            lastCheckpointElapsed = nowElapsed,
            lastSeenWall = clock.wallMillis()
        )
        val id = db.withTransaction {
            val generated = db.usageSessionDao().upsert(entity)
            db.auditEventDao().insert(AuditEventEntity(eventType = EventCodes.SESSION_STARTED, packageName = packageName))
            generated
        }
        return entity.copy(id = id)
    }

    suspend fun checkpointSession(
        session: UsageSessionEntity,
        segmentSec: Long,
        resetMinute: Int,
        close: Boolean = false,
        endReason: String? = null,
        accountingWall: Long? = null
    ): UsageSessionEntity {
        val nowElapsed = clock.elapsedMillis()
        val nowWall = clock.wallMillis()
        val updated = session.copy(
            accumulatedSec = session.accumulatedSec + segmentSec.coerceAtLeast(0L),
            lastCheckpointElapsed = nowElapsed,
            lastSeenWall = nowWall,
            isOpen = !close,
            endWall = if (close) nowWall else null,
            endReason = if (close) endReason else null
        )
        db.withTransaction {
            db.usageSessionDao().upsert(updated)
            if (segmentSec > 0) addDailyUsageInternal(
                session.packageName,
                segmentSec,
                accountingWall ?: nowWall,
                resetMinute
            )
            if (close) {
                db.auditEventDao().insert(
                    AuditEventEntity(eventType = EventCodes.SESSION_ENDED, packageName = session.packageName, detailCode = endReason)
                )
            }
        }
        return updated
    }

    private suspend fun addDailyUsageInternal(packageName: String, deltaSec: Long, wall: Long, resetMinute: Int) {
        val key = DateBoundary.dateKey(wall, resetMinute)
        val current = db.dailyUsageDao().get(key, packageName)?.usedSec ?: 0L
        db.dailyUsageDao().upsert(DailyUsageEntity(key, packageName, current + deltaSec, clock.wallMillis()))
    }

    suspend fun closeStaleOpenSessions() {
        val now = clock.wallMillis()
        db.withTransaction {
            db.usageSessionDao().getAllOpen().forEach { open ->
                db.usageSessionDao().upsert(
                    open.copy(isOpen = false, endWall = open.lastSeenWall.coerceAtMost(now), endReason = "STALE_RECOVERY")
                )
                db.auditEventDao().insert(
                    AuditEventEntity(eventType = EventCodes.SESSION_ENDED, packageName = open.packageName, detailCode = "STALE_RECOVERY")
                )
            }
        }
    }

    suspend fun setLock(lock: LockStateEntity) {
        db.withTransaction {
            db.lockStateDao().set(lock)
            val triggerEvent = when (lock.lockType) {
                "BREAK" -> EventCodes.BLOCK_CONTINUOUS
                "DAILY" -> EventCodes.BLOCK_DAILY
                else -> EventCodes.BLOCK_SCHEDULE
            }
            db.auditEventDao().insert(
                AuditEventEntity(
                    eventType = triggerEvent,
                    packageName = lock.triggerPackage,
                    detailCode = lock.reason
                )
            )
            if (lock.lockType == "BREAK") {
                db.auditEventDao().insert(
                    AuditEventEntity(
                        eventType = EventCodes.BREAK_STARTED,
                        packageName = lock.triggerPackage,
                        detailCode = lock.reason
                    )
                )
            }
        }
    }

    suspend fun clearLock(detail: String = "CLEARED") {
        db.withTransaction {
            val current = db.lockStateDao().get()
            db.lockStateDao().clear()
            if (current != null) {
                db.auditEventDao().insert(
                    AuditEventEntity(eventType = EventCodes.BREAK_ENDED, packageName = current.triggerPackage, detailCode = detail)
                )
            }
        }
    }

    suspend fun remainingLockMillis(lock: LockStateEntity): Long {
        val remaining = if (lock.bootCount >= 0 && lock.bootCount == clock.bootCount() && lock.endElapsed > 0) {
            lock.endElapsed - clock.elapsedMillis()
        } else {
            lock.endWall - clock.wallMillis()
        }
        return remaining.coerceAtLeast(0L)
    }

    suspend fun validOverride(packageName: String): Boolean = validOverrideEnd(packageName) != null

    suspend fun validOverrideEnd(packageName: String): Long? {
        val now = clock.wallMillis()
        db.overrideGrantDao().deleteExpired(now)
        return db.overrideGrantDao().getValid(packageName, now)?.endWall
    }

    suspend fun grantOverride(
        scopePackage: String,
        durationSec: Long,
        reason: String,
        auditPackage: String? = scopePackage
    ) {
        val now = clock.wallMillis()
        db.withTransaction {
            db.overrideGrantDao().insert(
                OverrideGrantEntity(
                    scopePackage = scopePackage,
                    startWall = now,
                    endWall = now + durationSec * 1000L,
                    reason = reason
                )
            )
            db.auditEventDao().insert(
                AuditEventEntity(eventType = EventCodes.ADMIN_OVERRIDE, packageName = auditPackage, detailCode = reason)
            )
        }
    }

    suspend fun createCredential(pin: String, recoveryCode: String) {
        require(pin.length in 4..8 && pin.all(Char::isDigit))
        val pinHash = hasher.hash(pin.toCharArray())
        val recoveryHash = hasher.hash(recoveryCode.toCharArray())
        db.withTransaction {
            db.adminCredentialDao().upsert(
                AdminCredentialEntity(
                    saltBase64 = pinHash.saltBase64,
                    passwordHashBase64 = pinHash.hashBase64,
                    recoverySaltBase64 = recoveryHash.saltBase64,
                    recoveryHashBase64 = recoveryHash.hashBase64
                )
            )
            db.auditEventDao().insert(AuditEventEntity(eventType = EventCodes.CREDENTIAL_CHANGED, detailCode = "CREATED"))
        }
    }

    enum class VerifyResult { SUCCESS, FAILURE, LOCKED, NOT_CONFIGURED }

    suspend fun verifySecret(secret: String, allowRecovery: Boolean = false): Pair<VerifyResult, Long> {
        val credential = db.adminCredentialDao().get() ?: return VerifyResult.NOT_CONFIGURED to 0L
        val now = clock.wallMillis()
        if (credential.lockoutUntilWall > now) return VerifyResult.LOCKED to credential.lockoutUntilWall
        val validPin = hasher.verify(secret.toCharArray(), credential.saltBase64, credential.passwordHashBase64)
        val validRecovery = allowRecovery && hasher.verify(
            secret.toCharArray(), credential.recoverySaltBase64, credential.recoveryHashBase64
        )
        if (validPin || validRecovery) {
            db.adminCredentialDao().upsert(credential.copy(failedAttempts = 0, lockoutUntilWall = 0L, updatedAt = now))
            return VerifyResult.SUCCESS to 0L
        }
        val attempts = credential.failedAttempts + 1
        val lockedUntil = if (attempts >= 5) now + 30_000L else 0L
        db.adminCredentialDao().upsert(
            credential.copy(
                failedAttempts = if (attempts >= 5) 0 else attempts,
                lockoutUntilWall = lockedUntil,
                updatedAt = now
            )
        )
        return if (lockedUntil > 0) VerifyResult.LOCKED to lockedUntil else VerifyResult.FAILURE to attempts.toLong()
    }

    suspend fun audit(eventType: String, packageName: String? = null, detail: String? = null) {
        db.auditEventDao().insert(AuditEventEntity(eventType = eventType, packageName = packageName, detailCode = detail))
    }

    suspend fun clearStatistics() {
        db.withTransaction {
            db.dailyUsageDao().clear()
            db.auditEventDao().clear()
        }
    }

    suspend fun factoryReset() {
        withContext(Dispatchers.IO) { db.clearAllTables() }
    }

    suspend fun prune() {
        val detailedCutoff = clock.wallMillis() - 90L * 24 * 60 * 60 * 1000
        db.auditEventDao().deleteOlderThan(detailedCutoff)
        db.dailyUsageDao().deleteOlderThan(DateBoundary.dateKeyDaysAgo(365))
    }
}
