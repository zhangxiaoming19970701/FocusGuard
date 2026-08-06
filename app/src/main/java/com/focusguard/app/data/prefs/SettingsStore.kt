package com.focusguard.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.focusGuardDataStore by preferencesDataStore(name = "focusguard_settings")

data class AppSettings(
    val disclosureAccepted: Boolean = false,
    val setupComplete: Boolean = false,
    val userMode: String = "PARENT",
    val resetMinute: Int = 0,
    val overrideDurationSec: Long = 15 * 60L,
    val monitoringEnabled: Boolean = true,
    val serviceConnected: Boolean = false,
    val lastServiceEventWall: Long = 0L,
    val recoveryCodeAcknowledged: Boolean = false,
    val permissionGuideAcknowledged: Boolean = false
)

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val disclosureAccepted = booleanPreferencesKey("disclosure_accepted")
        val setupComplete = booleanPreferencesKey("setup_complete")
        val userMode = stringPreferencesKey("user_mode")
        val resetMinute = intPreferencesKey("reset_minute")
        val overrideDurationSec = longPreferencesKey("override_duration_sec")
        val monitoringEnabled = booleanPreferencesKey("monitoring_enabled")
        val serviceConnected = booleanPreferencesKey("service_connected")
        val lastServiceEventWall = longPreferencesKey("last_service_event_wall")
        val recoveryCodeAcknowledged = booleanPreferencesKey("recovery_code_acknowledged")
        val permissionGuideAcknowledged = booleanPreferencesKey("permission_guide_acknowledged")
    }

    val settings: Flow<AppSettings> = context.focusGuardDataStore.data.map { p ->
        AppSettings(
            disclosureAccepted = p[Keys.disclosureAccepted] ?: false,
            setupComplete = p[Keys.setupComplete] ?: false,
            userMode = p[Keys.userMode] ?: "PARENT",
            resetMinute = p[Keys.resetMinute] ?: 0,
            overrideDurationSec = p[Keys.overrideDurationSec] ?: 15 * 60L,
            monitoringEnabled = p[Keys.monitoringEnabled] ?: true,
            serviceConnected = p[Keys.serviceConnected] ?: false,
            lastServiceEventWall = p[Keys.lastServiceEventWall] ?: 0L,
            recoveryCodeAcknowledged = p[Keys.recoveryCodeAcknowledged] ?: false,
            permissionGuideAcknowledged = p[Keys.permissionGuideAcknowledged] ?: false
        )
    }

    suspend fun acceptDisclosure(mode: String) {
        context.focusGuardDataStore.edit {
            it[Keys.disclosureAccepted] = true
            it[Keys.userMode] = mode
        }
    }

    suspend fun completeSetup() {
        context.focusGuardDataStore.edit {
            it[Keys.setupComplete] = true
            it[Keys.recoveryCodeAcknowledged] = true
        }
    }

    suspend fun completePermissionGuide() {
        context.focusGuardDataStore.edit { it[Keys.permissionGuideAcknowledged] = true }
    }

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        context.focusGuardDataStore.edit { it[Keys.monitoringEnabled] = enabled }
    }

    suspend fun setResetMinute(minute: Int) {
        require(minute in 0..1439)
        context.focusGuardDataStore.edit { it[Keys.resetMinute] = minute }
    }

    suspend fun setOverrideDurationSec(seconds: Long) {
        context.focusGuardDataStore.edit { it[Keys.overrideDurationSec] = seconds.coerceIn(60L, 180L * 60L) }
    }

    suspend fun resetAll() {
        context.focusGuardDataStore.edit { it.clear() }
    }

    suspend fun updateServiceState(connected: Boolean) {
        context.focusGuardDataStore.edit {
            it[Keys.serviceConnected] = connected
            it[Keys.lastServiceEventWall] = System.currentTimeMillis()
        }
    }
}
