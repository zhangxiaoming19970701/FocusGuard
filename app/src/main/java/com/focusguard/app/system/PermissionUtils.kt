package com.focusguard.app.system

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

object PermissionUtils {
    fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, AccessibilityMonitorService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return enabled.split(':')
            .mapNotNull(ComponentName::unflattenFromString)
            .any { it == ComponentName.unflattenFromString(expected) }
    }

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun notificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun batteryOptimizationIgnored(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java)
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun snapshot(context: Context, serviceConnected: Boolean): PermissionHealth = PermissionHealth(
        accessibilityEnabled = isAccessibilityEnabled(context),
        usageAccess = hasUsageAccess(context),
        notificationsEnabled = notificationsEnabled(context),
        batteryOptimizationIgnored = batteryOptimizationIgnored(context),
        serviceConnected = serviceConnected
    )
}

data class PermissionHealth(
    val accessibilityEnabled: Boolean,
    val usageAccess: Boolean,
    val notificationsEnabled: Boolean,
    val batteryOptimizationIgnored: Boolean,
    val serviceConnected: Boolean
) {
    val protectionReady: Boolean get() = accessibilityEnabled && serviceConnected
}
