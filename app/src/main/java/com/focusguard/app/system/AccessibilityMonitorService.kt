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
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
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
    private var foregroundRecoveryJob: Job? = null
    private var shutdownHandled = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    foregroundRecoveryJob?.cancel()
                    orchestrator.onScreenOff()
                }
                Intent.ACTION_SCREEN_ON -> {
                    if (isDeviceReadyForMonitoring()) {
                        scheduleForegroundRecovery("SCREEN_ON")
                    }
                }
                Intent.ACTION_USER_PRESENT -> scheduleForegroundRecovery("USER_PRESENT")
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        shutdownHandled = false
        connectedInstance = WeakReference(this)
        registerScreenReceiver()
        heartbeatJob?.cancel()
        heartbeatJob = serviceScope.launch {
            while (true) {
                settingsStore.updateServiceState(true)
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
        serviceScope.launch {
            repository.closeStaleOpenSessions()
            settingsStore.updateServiceState(true)
            repository.audit(EventCodes.SERVICE_CONNECTED)
            NotificationHelper.clearProtectionFailure(this@AccessibilityMonitorService)
            scheduleForegroundRecovery("ACCESSIBILITY_CONNECTED")
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
        val pkg = foregroundPackageFrom(event) ?: return
        if (pkg != SYSTEM_UI_PACKAGE) foregroundRecoveryJob?.cancel()
        orchestrator.onForegroundPackage(pkg) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    override fun onInterrupt() {
        // This callback only asks an accessibility service to stop current feedback. It is not
        // a lifecycle disconnection signal and the service may keep receiving events afterwards.
        serviceScope.launch {
            repository.audit(EventCodes.SERVICE_INTERRUPTED)
        }
    }

    override fun onDestroy() {
        stopMonitoring("DESTROYED")
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stopMonitoring("UNBOUND")
        return super.onUnbind(intent)
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
            addAction(Intent.ACTION_SCREEN_ON)
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

    private fun stopMonitoring(reason: String) {
        if (shutdownHandled) return
        shutdownHandled = true
        if (screenReceiverRegistered) {
            runCatching { unregisterReceiver(screenReceiver) }
            screenReceiverRegistered = false
        }
        foregroundRecoveryJob?.cancel()
        heartbeatJob?.cancel()
        orchestrator.onServiceStopping()
        if (connectedInstance?.get() === this) connectedInstance = null
        markDisconnected(reason)
    }

    private fun foregroundPackageFrom(event: AccessibilityEvent): String? {
        val eventPackage = event.packageName?.toString()?.takeIf { it.isNotBlank() }
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return eventPackage
        return activeWindowPackage() ?: eventPackage
    }

    @Suppress("DEPRECATION")
    private fun activeWindowPackage(): String? {
        val root = (if (Build.VERSION.SDK_INT >= 33) getRootInActiveWindow(0) else rootInActiveWindow)
            ?: return null
        return try {
            root.packageName?.toString()?.takeIf { it.isNotBlank() }
        } finally {
            if (Build.VERSION.SDK_INT < 33) root.recycle()
        }
    }

    private fun scheduleForegroundRecovery(reason: String) {
        foregroundRecoveryJob?.cancel()
        foregroundRecoveryJob = serviceScope.launch {
            RECOVERY_DELAYS_MS.forEachIndexed { attempt, waitMillis ->
                if (waitMillis > 0L) delay(waitMillis)
                if (!isDeviceReadyForMonitoring()) return@forEachIndexed

                val activeWindowPackage = withContext(Dispatchers.Main.immediate) {
                    activeWindowPackage()
                }
                val recoveredPackage = activeWindowPackage
                    ?.takeUnless { it == SYSTEM_UI_PACKAGE }
                    ?: usageStatsInspector.mostRecentForegroundPackage(RECOVERY_LOOKBACK_MS)
                        ?.takeUnless { it == SYSTEM_UI_PACKAGE }

                if (recoveredPackage != null) {
                    orchestrator.onForegroundPackage(recoveredPackage) {
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    }
                    repository.audit(
                        EventCodes.RECOVERY_COMPLETED,
                        packageName = recoveredPackage,
                        detail = "$reason:${attempt + 1}"
                    )
                    return@launch
                }
            }
        }
    }

    private fun isDeviceReadyForMonitoring(): Boolean {
        val power = getSystemService(PowerManager::class.java)
        val keyguard = getSystemService(KeyguardManager::class.java)
        return power.isInteractive && !keyguard.isKeyguardLocked
    }

    companion object {
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val HEARTBEAT_INTERVAL_MS = 10 * 60_000L
        private const val RECOVERY_LOOKBACK_MS = 5 * 60_000L
        private val RECOVERY_DELAYS_MS = longArrayOf(0L, 250L, 1_000L, 2_500L)

        @Volatile
        private var connectedInstance: WeakReference<AccessibilityMonitorService>? = null

        fun isRuntimeConnected(): Boolean = connectedInstance?.get() != null

        fun requestForegroundRecheck(reason: String): Boolean {
            val service = connectedInstance?.get() ?: return false
            service.scheduleForegroundRecovery(reason)
            return true
        }
    }
}
