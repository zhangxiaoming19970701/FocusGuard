package com.focusguard.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ManagedAppDao {
    @Query("SELECT * FROM managed_apps ORDER BY appLabel COLLATE NOCASE")
    fun observeAll(): Flow<List<ManagedAppEntity>>

    @Query("SELECT * FROM managed_apps WHERE enabled = 1 ORDER BY appLabel COLLATE NOCASE")
    suspend fun getEnabled(): List<ManagedAppEntity>

    @Query("SELECT * FROM managed_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun get(packageName: String): ManagedAppEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ManagedAppEntity)

    @Query("DELETE FROM managed_apps WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules ORDER BY packageName")
    fun observeAll(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules WHERE packageName = :packageName LIMIT 1")
    suspend fun get(packageName: String): RuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RuleEntity)
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedule_windows WHERE packageName = :packageName ORDER BY mode, id")
    fun observeForPackage(packageName: String): Flow<List<ScheduleWindowEntity>>

    @Query("SELECT * FROM schedule_windows WHERE packageName = :packageName AND enabled = 1")
    suspend fun getEnabledForPackage(packageName: String): List<ScheduleWindowEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ScheduleWindowEntity): Long

    @Query("DELETE FROM schedule_windows WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface DailyUsageDao {
    @Query("SELECT * FROM daily_usage WHERE dateKey = :dateKey ORDER BY usedSec DESC")
    fun observeForDate(dateKey: String): Flow<List<DailyUsageEntity>>

    @Query("SELECT * FROM daily_usage WHERE dateKey >= :minimumDateKey ORDER BY dateKey DESC, usedSec DESC")
    fun observeSince(minimumDateKey: String): Flow<List<DailyUsageEntity>>

    @Query("SELECT * FROM daily_usage WHERE dateKey = :dateKey AND packageName = :packageName LIMIT 1")
    suspend fun get(dateKey: String, packageName: String): DailyUsageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DailyUsageEntity)

    @Query("DELETE FROM daily_usage")
    suspend fun clear()

    @Query("DELETE FROM daily_usage WHERE dateKey < :minimumDateKey")
    suspend fun deleteOlderThan(minimumDateKey: String)
}

@Dao
interface UsageSessionDao {
    @Query("SELECT * FROM usage_sessions WHERE packageName = :packageName AND isOpen = 1 ORDER BY id DESC LIMIT 1")
    suspend fun getOpen(packageName: String): UsageSessionEntity?

    @Query("SELECT * FROM usage_sessions WHERE isOpen = 1")
    suspend fun getAllOpen(): List<UsageSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UsageSessionEntity): Long

    @Query("SELECT * FROM usage_sessions WHERE packageName = :packageName ORDER BY startWall DESC LIMIT :limit")
    suspend fun recentForPackage(packageName: String, limit: Int = 20): List<UsageSessionEntity>
}

@Dao
interface LockStateDao {
    @Query("SELECT * FROM lock_state WHERE id = 1 LIMIT 1")
    fun observe(): Flow<LockStateEntity?>

    @Query("SELECT * FROM lock_state WHERE id = 1 LIMIT 1")
    suspend fun get(): LockStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(entity: LockStateEntity)

    @Query("DELETE FROM lock_state WHERE id = 1")
    suspend fun clear()
}

@Dao
interface OverrideGrantDao {
    @Query("SELECT * FROM override_grants WHERE endWall > :nowWall AND (scopePackage = :packageName OR scopePackage = '*') ORDER BY endWall DESC LIMIT 1")
    suspend fun getValid(packageName: String, nowWall: Long): OverrideGrantEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: OverrideGrantEntity): Long

    @Query("DELETE FROM override_grants WHERE endWall <= :nowWall")
    suspend fun deleteExpired(nowWall: Long)

    @Query("DELETE FROM override_grants")
    suspend fun clearAll()
}

@Dao
interface AdminCredentialDao {
    @Query("SELECT * FROM admin_credential WHERE id = 1 LIMIT 1")
    fun observe(): Flow<AdminCredentialEntity?>

    @Query("SELECT * FROM admin_credential WHERE id = 1 LIMIT 1")
    suspend fun get(): AdminCredentialEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AdminCredentialEntity)
}

@Dao
interface AuditEventDao {
    @Insert
    suspend fun insert(entity: AuditEventEntity): Long

    @Query("SELECT * FROM audit_events ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<AuditEventEntity>>

    @Query("SELECT * FROM audit_events WHERE timestamp >= :fromWall ORDER BY timestamp DESC")
    suspend fun since(fromWall: Long): List<AuditEventEntity>

    @Query("SELECT * FROM audit_events WHERE timestamp >= :fromWall ORDER BY timestamp DESC")
    fun observeSince(fromWall: Long): Flow<List<AuditEventEntity>>

    @Query("DELETE FROM audit_events")
    suspend fun clear()

    @Query("DELETE FROM audit_events WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
