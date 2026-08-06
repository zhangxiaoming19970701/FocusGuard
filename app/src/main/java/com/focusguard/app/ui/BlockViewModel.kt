package com.focusguard.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.app.data.db.LockStateEntity
import com.focusguard.app.data.prefs.SettingsStore
import com.focusguard.app.data.repo.FocusGuardRepository
import com.focusguard.app.system.ClockProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BlockUiState(
    val lock: LockStateEntity? = null,
    val remainingMillis: Long = 0L,
    val pinVisible: Boolean = false,
    val pinValue: String = "",
    val adminMenuVisible: Boolean = false,
    val message: String? = null,
    val inputEnabled: Boolean = true,
    val overrideDurationSec: Long = 15 * 60L
)

enum class BlockNavigation { HOME, FINISH }

@HiltViewModel
class BlockViewModel @Inject constructor(
    private val repository: FocusGuardRepository,
    private val settingsStore: SettingsStore,
    private val clock: ClockProvider
) : ViewModel() {
    private val remainingMillis = MutableStateFlow(0L)
    private val pinVisible = MutableStateFlow(false)
    private val pinValue = MutableStateFlow("")
    private val adminMenuVisible = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val inputEnabled = MutableStateFlow(true)
    private val _navigation = MutableSharedFlow<BlockNavigation>(replay = 1, extraBufferCapacity = 2)
    private val overrideDurationSec = settingsStore.settings.map { it.overrideDurationSec }
    val navigation = _navigation.asSharedFlow()

    private data class PinState(
        val visible: Boolean,
        val value: String,
        val menuVisible: Boolean,
        val message: String?,
        val enabled: Boolean
    )

    private val pinState = combine(
        pinVisible,
        pinValue,
        adminMenuVisible,
        message,
        inputEnabled
    ) { visible, value, menu, msg, enabled ->
        PinState(visible, value, menu, msg, enabled)
    }

    val uiState: StateFlow<BlockUiState> = combine(
        repository.lockState,
        remainingMillis,
        pinState,
        overrideDurationSec
    ) { lock, remaining, pin, overrideSec ->
        BlockUiState(lock, remaining, pin.visible, pin.value, pin.menuVisible, pin.message, pin.enabled, overrideSec)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BlockUiState())

    init {
        viewModelScope.launch {
            repository.lockState.collectLatest { lock ->
                if (lock == null) {
                    remainingMillis.value = 0L
                    _navigation.emit(BlockNavigation.FINISH)
                    return@collectLatest
                }
                while (true) {
                    val remain = repository.remainingLockMillis(lock)
                    remainingMillis.value = remain
                    if (remain <= 0L) {
                        repository.clearLock("NATURAL_END")
                        _navigation.tryEmit(BlockNavigation.HOME)
                        break
                    }
                    delay(500L)
                }
            }
        }
    }

    fun showPin() {
        pinVisible.value = true
        adminMenuVisible.value = false
        message.value = null
        pinValue.value = ""
    }

    fun hidePin() {
        pinVisible.value = false
        pinValue.value = ""
    }

    fun setPin(value: String) { pinValue.value = value.take(8) }

    fun verifyPin() = viewModelScope.launch {
        inputEnabled.value = false
        val result = repository.verifySecret(pinValue.value, allowRecovery = true)
        pinValue.value = ""
        when (result.first) {
            FocusGuardRepository.VerifyResult.SUCCESS -> {
                pinVisible.value = false
                adminMenuVisible.value = true
                message.value = null
            }
            FocusGuardRepository.VerifyResult.LOCKED -> {
                val seconds = ((result.second - clock.wallMillis()) / 1000L).coerceAtLeast(1L)
                message.value = "连续输入错误，暂停 $seconds 秒"
                delay((result.second - clock.wallMillis()).coerceAtLeast(1_000L))
            }
            FocusGuardRepository.VerifyResult.FAILURE -> message.value = "密码错误，请重试"
            FocusGuardRepository.VerifyResult.NOT_CONFIGURED -> message.value = "管理密码未配置"
        }
        inputEnabled.value = true
    }

    fun temporaryOverride() = viewModelScope.launch {
        val lock = repository.getLock() ?: return@launch
        val settings = settingsStore.settings.first()
        repository.grantOverride(
            scopePackage = if (lock.globalScope) "*" else lock.triggerPackage.orEmpty(),
            durationSec = settings.overrideDurationSec,
            reason = "BLOCK_SCREEN_TEMP_OVERRIDE",
            auditPackage = lock.triggerPackage
        )
        _navigation.emit(BlockNavigation.HOME)
    }

    fun endCurrentLock() = viewModelScope.launch {
        repository.clearLock("ADMIN_END")
        _navigation.emit(BlockNavigation.HOME)
    }

    fun returnHome() = viewModelScope.launch {
        val lock = repository.getLock()
        if (lock?.globalScope == true) {
            repository.grantOverride("*", 60L, "ADMIN_HOME_GRACE", auditPackage = lock.triggerPackage)
        }
        _navigation.emit(BlockNavigation.HOME)
    }
}
