package com.focusguard.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "managed_apps")
data class ManagedAppEntity(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val enabled: Boolean = true,
    val isSystem: Boolean = false,
    val isWhitelist: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey val packageName: String,
    val enabled: Boolean = true,
    val continuousEnabled: Boolean = true,
    val continuousLimitSec: Long = 30 * 60L,
    val dailyEnabled: Boolean = true,
    val dailyLimitSec: Long = 60 * 60L,
    val breakDurationSec: Long = 20 * 60L,
    val mergeGapSec: Long = 10L,
    val globalBreak: Boolean = true,
    val warningFirstSec: Long = 5 * 60L,
    val warningSecondSec: Long = 60L,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "schedule_windows",
    indices = [Index("packageName")]
)
data class ScheduleWindowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val mode: String,
    val daysMask: Int,
    val startMinute: Int,
    val endMinute: Int,
    val enabled: Boolean = true,
    val label: String = ""
)

@Entity(tableName = "daily_usage", primaryKeys = ["dateKey", "packageName"])
data class DailyUsageEntity(
    val dateKey: String,
    val packageName: String,
    val usedSec: Long,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "usage_sessions",
    indices = [Index("packageName"), Index("isOpen")]
)
data class UsageSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val bootCount: Int,
    val startElapsed: Long,
    val startWall: Long,
    val accumulatedSec: Long = 0,
    val lastCheckpointElapsed: Long,
    val lastSeenWall: Long,
    val isOpen: Boolean = true,
    val endWall: Long? = null,
    val endReason: String? = null
)

@Entity(tableName = "lock_state")
data class LockStateEntity(
    @PrimaryKey val id: Int = 1,
    val lockType: String,
    val triggerPackage: String?,
    val triggerLabel: String?,
    val startWall: Long,
    val endWall: Long,
    val endElapsed: Long,
    val bootCount: Int,
    val globalScope: Boolean,
    val reason: String,
    val nextAllowedText: String? = null
)

@Entity(tableName = "override_grants", indices = [Index("endWall")])
data class OverrideGrantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scopePackage: String,
    val startWall: Long,
    val endWall: Long,
    val reason: String
)

@Entity(tableName = "admin_credential")
data class AdminCredentialEntity(
    @PrimaryKey val id: Int = 1,
    val saltBase64: String,
    val passwordHashBase64: String,
    val recoverySaltBase64: String,
    val recoveryHashBase64: String,
    val failedAttempts: Int = 0,
    val lockoutUntilWall: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "audit_events", indices = [Index("timestamp"), Index("packageName")])
data class AuditEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventType: String,
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String? = null,
    val ruleKey: String? = null,
    val detailCode: String? = null
)
