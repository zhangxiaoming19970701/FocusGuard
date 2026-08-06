package com.focusguard.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.app.data.db.AuditEventEntity
import com.focusguard.app.data.db.DailyUsageEntity
import com.focusguard.app.data.db.ManagedAppEntity
import com.focusguard.app.data.db.RuleEntity
import com.focusguard.app.data.prefs.AppSettings
import com.focusguard.app.data.prefs.SettingsStore
import com.focusguard.app.data.repo.FocusGuardRepository
import com.focusguard.app.domain.DateBoundary
import com.focusguard.app.domain.PasswordHasher
import com.focusguard.app.system.AppCatalog
import com.focusguard.app.system.InstalledApp
import com.focusguard.app.system.PermissionHealth
import com.focusguard.app.system.PermissionUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val settings: AppSettings = AppSettings(),
    val credentialConfigured: Boolean = false,
    val recoveryCode: String? = null,
    val managedApps: List<ManagedAppEntity> = emptyList(),
    val rules: Map<String, RuleEntity> = emptyMap(),
    val dailyUsage: Map<String, DailyUsageEntity> = emptyMap(),
    val recentUsage: List<DailyUsageEntity> = emptyList(),
    val installedApps: List<InstalledApp> = emptyList(),
    val auditEvents: List<AuditEventEntity> = emptyList(),
    val sevenDayAudit: List<AuditEventEntity> = emptyList(),
    val health: PermissionHealth = PermissionHealth(false, false, false, false, false),
    val adminAuthenticated: Boolean = false,
    val message: String? = null,
    val busy: Boolean = false
)

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: FocusGuardRepository,
    private val settingsStore: SettingsStore,
    private val appCatalog: AppCatalog,
    private val passwordHasher: PasswordHasher
) : ViewModel() {
    private val installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val recoveryCode = MutableStateFlow<String?>(null)
    private val adminAuthenticated = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val busy = MutableStateFlow(false)
    private val healthPulse = MutableStateFlow(0L)

    private val dailyFlow = settingsStore.settings.flatMapLatest { settings ->
        repository.dailyUsage(DateBoundary.dateKey(System.currentTimeMillis(), settings.resetMinute))
    }
    private val recentUsageFlow = settingsStore.settings.flatMapLatest {
        repository.usageSince(DateBoundary.dateKeyDaysAgo(6))
    }
    private data class UsageData(
        val today: List<DailyUsageEntity>,
        val recent: List<DailyUsageEntity>
    )
    private val usageData = combine(dailyFlow, recentUsageFlow) { today, recent -> UsageData(today, recent) }

    private data class CoreData(
        val settings: AppSettings,
        val credentialConfigured: Boolean,
        val managed: List<ManagedAppEntity>,
        val rules: List<RuleEntity>,
        val usage: UsageData
    )

    private data class AuditData(
        val recent: List<AuditEventEntity>,
        val sevenDay: List<AuditEventEntity>
    )
    private val auditData = combine(
        repository.recentAudit,
        repository.auditSince(System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000)
    ) { recent, sevenDay -> AuditData(recent, sevenDay) }

    private data class AuxiliaryData(
        val installed: List<InstalledApp>,
        val audit: AuditData,
        val recovery: String?,
        val authenticated: Boolean,
        val message: String?
    )

    private val coreData = combine(
        settingsStore.settings,
        repository.credential,
        repository.managedApps,
        repository.rules,
        usageData
    ) { settings, credential, managed, rules, usage ->
        CoreData(settings, credential != null, managed, rules, usage)
    }

    private val auxiliaryData = combine(
        installedApps,
        auditData,
        recoveryCode,
        adminAuthenticated,
        message
    ) { installed, audit, recovery, authenticated, msg ->
        AuxiliaryData(installed, audit, recovery, authenticated, msg)
    }

    val uiState: StateFlow<MainUiState> = combine(
        coreData,
        auxiliaryData,
        busy,
        healthPulse
    ) { core, aux, isBusy, _ ->
        MainUiState(
            settings = core.settings,
            credentialConfigured = core.credentialConfigured,
            recoveryCode = aux.recovery,
            managedApps = core.managed,
            rules = core.rules.associateBy { it.packageName },
            dailyUsage = core.usage.today.associateBy { it.packageName },
            recentUsage = core.usage.recent,
            installedApps = aux.installed,
            auditEvents = aux.audit.recent,
            sevenDayAudit = aux.audit.sevenDay,
            health = PermissionUtils.snapshot(
                context,
                core.settings.serviceConnected &&
                    core.settings.lastServiceEventWall > 0L &&
                    System.currentTimeMillis() - core.settings.lastServiceEventWall < 10 * 60_000L
            ),
            adminAuthenticated = aux.authenticated,
            message = aux.message,
            busy = isBusy
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    init {
        viewModelScope.launch { repository.prune() }
        viewModelScope.launch {
            installedApps.value = appCatalog.launcherApps()
        }
        viewModelScope.launch {
            while (true) {
                healthPulse.value = System.currentTimeMillis()
                delay(2_000L)
            }
        }
    }

    fun acceptDisclosure(mode: String) = viewModelScope.launch {
        settingsStore.acceptDisclosure(mode)
    }

    fun createCredential(pin: String) = viewModelScope.launch {
        if (pin.length !in 4..8 || pin.any { !it.isDigit() }) {
            message.value = "管理密码必须为 4-8 位数字"
            return@launch
        }
        busy.value = true
        try {
            val code = passwordHasher.generateRecoveryCode()
            repository.createCredential(pin, code)
            recoveryCode.value = code
        } finally {
            busy.value = false
        }
    }

    fun regenerateRecoveryCode(pin: String) = viewModelScope.launch {
        val result = repository.verifySecret(pin).first
        if (result == FocusGuardRepository.VerifyResult.SUCCESS) {
            val code = passwordHasher.generateRecoveryCode()
            repository.createCredential(pin, code)
            recoveryCode.value = code
        } else {
            message.value = "管理密码验证失败"
        }
    }

    fun acknowledgeRecoveryCode() = viewModelScope.launch {
        settingsStore.completeSetup()
        recoveryCode.value = null
    }

    fun completePermissionGuide() = viewModelScope.launch {
        settingsStore.completePermissionGuide()
    }

    fun verifyAdmin(pin: String) = viewModelScope.launch {
        busy.value = true
        try {
            val result = repository.verifySecret(pin, allowRecovery = true)
            when (result.first) {
                FocusGuardRepository.VerifyResult.SUCCESS -> {
                    adminAuthenticated.value = true
                    message.value = null
                    launch {
                        delay(5 * 60_000L)
                        adminAuthenticated.value = false
                    }
                }
                FocusGuardRepository.VerifyResult.LOCKED -> {
                    val sec = ((result.second - System.currentTimeMillis()) / 1000L).coerceAtLeast(1L)
                    message.value = "输入已暂停，请等待 $sec 秒"
                }
                FocusGuardRepository.VerifyResult.FAILURE -> message.value = "密码错误"
                FocusGuardRepository.VerifyResult.NOT_CONFIGURED -> message.value = "尚未设置管理密码"
            }
        } finally {
            busy.value = false
        }
    }

    fun lockAdmin() { adminAuthenticated.value = false }
    fun clearMessage() { message.value = null }

    fun setManaged(installed: InstalledApp, selected: Boolean) = viewModelScope.launch {
        if (installed.isCritical) {
            message.value = "系统关键应用不能被限制"
            return@launch
        }
        repository.setManagedApp(
            ManagedAppEntity(
                packageName = installed.packageName,
                appLabel = installed.label,
                enabled = selected,
                isSystem = installed.isSystem,
                isWhitelist = false
            ),
            selected
        )
    }

    fun setMonitoringEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsStore.setMonitoringEnabled(enabled)
    }

    fun setOverrideMinutes(minutes: Int) = viewModelScope.launch {
        settingsStore.setOverrideDurationSec(minutes.coerceIn(1, 180) * 60L)
    }

    fun setResetHour(hour: Int) = viewModelScope.launch {
        settingsStore.setResetMinute(hour.coerceIn(0, 23) * 60)
    }

    fun changeCredential(oldSecret: String, newPin: String, confirmPin: String) = viewModelScope.launch {
        when {
            newPin.length !in 4..8 || newPin.any { !it.isDigit() } -> message.value = "新密码必须为 4-8 位数字"
            newPin != confirmPin -> message.value = "两次新密码不一致"
            else -> {
                val verified = repository.verifySecret(oldSecret, allowRecovery = true).first
                if (verified != FocusGuardRepository.VerifyResult.SUCCESS) {
                    message.value = "旧密码或恢复码错误"
                } else {
                    val code = passwordHasher.generateRecoveryCode()
                    repository.createCredential(newPin, code)
                    recoveryCode.value = code
                    message.value = "管理密码已修改，请保存新的恢复码"
                }
            }
        }
    }

    fun factoryReset() = viewModelScope.launch {
        repository.factoryReset()
        settingsStore.resetAll()
        recoveryCode.value = null
        adminAuthenticated.value = false
        message.value = null
    }

    fun clearStatistics() = viewModelScope.launch {
        repository.clearStatistics()
        message.value = "本机统计和事件日志已清理"
    }
}
