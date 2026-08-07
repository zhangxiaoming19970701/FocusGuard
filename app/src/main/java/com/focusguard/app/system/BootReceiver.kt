package com.focusguard.app.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in SUPPORTED_ACTIONS) return
        HealthCheckScheduler.runOnce(context, HEALTH_CHECK_STARTUP_GRACE_MS)
        HealthCheckScheduler.schedule(context)
    }

    companion object {
        private const val HEALTH_CHECK_STARTUP_GRACE_MS = 60_000L
        private val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_USER_UNLOCKED,
            Intent.ACTION_MY_PACKAGE_REPLACED
        )
    }
}
