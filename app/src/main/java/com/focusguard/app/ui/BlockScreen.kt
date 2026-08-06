package com.focusguard.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.app.util.LockTypes
import kotlinx.coroutines.flow.collectLatest

@Composable
fun BlockScreen(viewModel: BlockViewModel, onHome: () -> Unit, onFinish: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(enabled = true) { }
    LaunchedEffect(Unit) {
        viewModel.navigation.collectLatest {
            when (it) {
                BlockNavigation.HOME -> onHome()
                BlockNavigation.FINISH -> onFinish()
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        val lock = state.lock
        if (lock != null) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    when (lock.lockType) {
                        LockTypes.BREAK -> "请休息一下"
                        LockTypes.DAILY -> "今日使用时间已用完"
                        LockTypes.SCHEDULE -> "当前无法使用"
                        else -> "保护状态异常"
                    },
                    color = Color(0xFFFF3B30),
                    fontSize = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(18.dp))
                Text("剩余时间", color = Color.White, fontSize = 18.sp)
                Text(
                    formatCountdown(state.remainingMillis),
                    color = Color.White,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))
                Text(lock.reason, color = Color.White, fontSize = 18.sp, textAlign = TextAlign.Center)
                lock.triggerLabel?.let { Text("触发应用：$it", color = Color.LightGray) }
                lock.nextAllowedText?.let { Text(it, color = Color.LightGray) }
                Spacer(Modifier.height(32.dp))

                when {
                    state.adminMenuVisible -> AdminActions(viewModel, state.overrideDurationSec)
                    state.pinVisible -> {
                        Text("输入管理员密码", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        PinPad(
                            value = state.pinValue,
                            onValueChange = viewModel::setPin,
                            onConfirm = viewModel::verifyPin,
                            maxLength = 8,
                            enabled = state.inputEnabled,
                            dark = true
                        )
                        state.message?.let { Text(it, color = Color(0xFFFF6B64)) }
                        TextButton(onClick = viewModel::hidePin) { Text("取消", color = Color.White) }
                    }
                    else -> {
                        TextButton(onClick = viewModel::showPin) {
                            Text("管理员", color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminActions(viewModel: BlockViewModel, overrideDurationSec: Long) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = viewModel::temporaryOverride,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF155A8A))
        ) { Text("临时解除 ${overrideDurationSec / 60} 分钟") }
        OutlinedButton(onClick = viewModel::endCurrentLock, modifier = Modifier.fillMaxWidth()) {
            Text("结束本次休息", color = Color.White)
        }
        TextButton(onClick = viewModel::returnHome, modifier = Modifier.fillMaxWidth()) {
            Text("返回桌面", color = Color.White)
        }
    }
}

private fun formatCountdown(millis: Long): String {
    val total = ((millis + 999L) / 1000L).coerceAtLeast(0L)
    val hours = total / 3600L
    val minutes = (total % 3600L) / 60L
    val seconds = total % 60L
    return if (hours > 0) "%02d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}
