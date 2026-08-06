package com.focusguard.app.system

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageStatsInspector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Low-frequency recovery aid only. Real-time blocking remains AccessibilityService-driven.
     */
    fun mostRecentForegroundPackage(lookbackMillis: Long = 2 * 60_000L): String? {
        if (!PermissionUtils.hasUsageAccess(context)) return null
        val manager = context.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val events = manager.queryEvents(now - lookbackMillis, now)
        val event = UsageEvents.Event()
        var latestPackage: String? = null
        var latestTimestamp = Long.MIN_VALUE
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val foreground = event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            if (foreground && event.timeStamp >= latestTimestamp) {
                latestTimestamp = event.timeStamp
                latestPackage = event.packageName
            }
        }
        return latestPackage
    }
}
