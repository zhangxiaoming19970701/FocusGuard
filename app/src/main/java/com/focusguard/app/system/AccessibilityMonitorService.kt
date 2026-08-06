package com.focusguard.app.system

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.app.KeyguardManager
import android.os.PowerManager
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import com.focusguard.app.data.prefs.SettingsStore
import com.focusguard.app.data.repo.FocusGuardRepository
import com.focusguard.app.util.EventCodes
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AccessibilityMonitorService : AccessibilityService() {
    @Inject lateinit var orchestrator: ForegroundOrchestrator
    @Inject lateinit var settingsStore: SettingsStore
    @Inject lateinit var repository: FocusGuardRepository
    @Inject lateinit var usageStatsInspector: UsageStatsInspector

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var screenReceiverRegistered = false
    private var heartbeatJob: Job? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) orchestrator.onScreenOff()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerScreenReceiver()
        heartbeatJob?.cancel()
        heartbeatJob = serviceScope.launch {
            while (true) {
                settingsStore.updateServiceState(true)
                delay(5 * 60_000L)
            }
        }
        serviceScope.launch {
            repository.closeStaleOpenSessions()
            settingsStore.updateServiceState(true)
            repository.audit(EventCodes.SERVICE_CONNECTED)
            NotificationHelper.clearProtectionFailure(this@AccessibilityMonitorService)
            repository.audit(EventCodes.RECOVERY_COMPLETED, detail = "ACCESSIBILITY_CONNECTED")
            usageStatsInspector.mostRecentForegroundPackage()?.let { recovered ->
                orchestrator.onForegroundPackage(recovered) { performGlobalAction(GLOBAL_ACTION_HOME) }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) return
        val power = getSystemService(PowerManager::class.java)
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!power.isInteractive || keyguard.isKeyguardLocked) return
        val pkg = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        orchestrator.onForegroundPackage(pkg) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    override fun onInterrupt() {
        heartbeatJob?.cancel()
        orchestrator.onServiceStopping()
        markDisconnected("INTERRUPTED")
    }

    override fun onDestroy() {
        if (screenReceiverRegistered) runCatching { unregisterReceiver(screenReceiver) }
        heartbeatJob?.cancel()
        orchestrator.onServiceStopping()
        markDisconnected("DESTROYED")
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun markDisconnected(reason: String) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            settingsStore.updateServiceState(false)
            repository.audit(EventCodes.SERVICE_DISCONNECTED, detail = reason)
            NotificationHelper.showProtectionFailure(
                this@AccessibilityMonitorService,
                "前台应用监测服务已断开，请重新开启无障碍权限。"
            )
        }
    }

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(screenReceiver, filter)
        }
        screenReceiverRegistered = true
    }
}
