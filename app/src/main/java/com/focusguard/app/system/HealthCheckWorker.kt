package com.focusguard.app.system

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class HealthCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (!PermissionUtils.isAccessibilityEnabled(applicationContext)) {
            NotificationHelper.showProtectionFailure(
                applicationContext,
                "无障碍监测权限未开启，请点击修复。"
            )
        }
        return Result.success()
    }
}

object HealthCheckScheduler {
    private const val PERIODIC_NAME = "focusguard_health_periodic"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<HealthCheckWorker>(30, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun runOnce(context: Context) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<HealthCheckWorker>().build())
    }
}
