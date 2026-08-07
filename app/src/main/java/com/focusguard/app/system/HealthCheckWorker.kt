package com.focusguard.app.system

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.focusguard.app.data.prefs.SettingsStore
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

internal const val SERVICE_STALE_AFTER_MS = 25 * 60_000L

internal enum class ServiceHealthStatus {
    RUNTIME_CONNECTED,
    RECENT_HEARTBEAT,
    PERMISSION_DISABLED,
    SERVICE_STALE
}

internal object ServiceHealthEvaluator {
    fun evaluate(
        permissionEnabled: Boolean,
        runtimeConnected: Boolean,
        storedConnected: Boolean,
        lastHeartbeatWall: Long,
        nowWall: Long
    ): ServiceHealthStatus {
        if (!permissionEnabled) return ServiceHealthStatus.PERMISSION_DISABLED
        if (runtimeConnected) return ServiceHealthStatus.RUNTIME_CONNECTED
        val heartbeatAge = nowWall - lastHeartbeatWall
        return if (
            storedConnected &&
            lastHeartbeatWall > 0L &&
            heartbeatAge in 0 until SERVICE_STALE_AFTER_MS
        ) {
            ServiceHealthStatus.RECENT_HEARTBEAT
        } else {
            ServiceHealthStatus.SERVICE_STALE
        }
    }
}

class HealthCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val settingsStore = SettingsStore(applicationContext)
        val settings = settingsStore.settings.first()
        val status = ServiceHealthEvaluator.evaluate(
            permissionEnabled = PermissionUtils.isAccessibilityEnabled(applicationContext),
            runtimeConnected = AccessibilityMonitorService.isRuntimeConnected(),
            storedConnected = settings.serviceConnected,
            lastHeartbeatWall = settings.lastServiceEventWall,
            nowWall = System.currentTimeMillis()
        )

        when (status) {
            ServiceHealthStatus.PERMISSION_DISABLED -> {
                if (settings.serviceConnected) settingsStore.updateServiceState(false)
                NotificationHelper.showProtectionFailure(
                    applicationContext,
                    "无障碍监测权限已关闭，请点击并重新开启 FocusGuard。"
                )
            }
            ServiceHealthStatus.SERVICE_STALE -> {
                if (settings.serviceConnected) settingsStore.updateServiceState(false)
                NotificationHelper.showProtectionFailure(
                    applicationContext,
                    "无障碍权限仍开启，但监测服务长时间无心跳，请点击重新连接。"
                )
            }
            ServiceHealthStatus.RUNTIME_CONNECTED -> {
                if (!settings.serviceConnected ||
                    System.currentTimeMillis() - settings.lastServiceEventWall >= SERVICE_STALE_AFTER_MS
                ) {
                    settingsStore.updateServiceState(true)
                }
                AccessibilityMonitorService.requestForegroundRecheck("HEALTH_CHECK")
                NotificationHelper.clearProtectionFailure(applicationContext)
            }
            ServiceHealthStatus.RECENT_HEARTBEAT -> {
                NotificationHelper.clearProtectionFailure(applicationContext)
            }
        }
        return Result.success()
    }
}

object HealthCheckScheduler {
    private const val PERIODIC_NAME = "focusguard_health_periodic"
    private const val ONE_TIME_NAME = "focusguard_health_once"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<HealthCheckWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun runOnce(context: Context, initialDelayMillis: Long = 0L) {
        val request = OneTimeWorkRequestBuilder<HealthCheckWorker>()
            .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
