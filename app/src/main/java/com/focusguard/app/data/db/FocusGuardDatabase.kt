package com.focusguard.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ManagedAppEntity::class,
        RuleEntity::class,
        ScheduleWindowEntity::class,
        DailyUsageEntity::class,
        UsageSessionEntity::class,
        LockStateEntity::class,
        OverrideGrantEntity::class,
        AdminCredentialEntity::class,
        AuditEventEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class FocusGuardDatabase : RoomDatabase() {
    abstract fun managedAppDao(): ManagedAppDao
    abstract fun ruleDao(): RuleDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun dailyUsageDao(): DailyUsageDao
    abstract fun usageSessionDao(): UsageSessionDao
    abstract fun lockStateDao(): LockStateDao
    abstract fun overrideGrantDao(): OverrideGrantDao
    abstract fun adminCredentialDao(): AdminCredentialDao
    abstract fun auditEventDao(): AuditEventDao
}
