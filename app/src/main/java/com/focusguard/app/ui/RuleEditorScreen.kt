package com.focusguard.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.app.data.db.RuleEntity
import com.focusguard.app.data.db.ScheduleWindowEntity
import com.focusguard.app.domain.ScheduleMatcher
import com.focusguard.app.util.ScheduleModes
import java.time.DayOfWeek

@Composable
fun RuleEditorScreen(onBack: () -> Unit, viewModel: RuleEditorViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val rule = state.rule
    if (rule == null) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("正在读取规则…")
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("返回") }
                Column {
                    Text(state.app?.appLabel ?: viewModel.packageName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(viewModel.packageName, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            SettingSwitch("规则总开关", "关闭后保留统计和配置", rule.enabled) {
                viewModel.updateRule { current -> current.copy(enabled = it) }
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingSwitch("单次连续使用上限", "达到上限后强制休息", rule.continuousEnabled) {
                        viewModel.updateRule { current -> current.copy(continuousEnabled = it) }
                    }
                    NumericSetting(
                        title = "连续使用",
                        value = (rule.continuousLimitSec / 60).toInt(),
                        unit = "分钟",
                        range = 1..720,
                        step = 5
                    ) { value -> viewModel.updateRule { it.copy(continuousLimitSec = value * 60L) } }
                    NumericSetting(
                        title = "休息时长",
                        value = (rule.breakDurationSec / 60).toInt(),
                        unit = "分钟",
                        range = 1..180,
                        step = 5
                    ) { value -> viewModel.updateRule { it.copy(breakDurationSec = value * 60L) } }
                    NumericSetting(
                        title = "会话合并间隔",
                        value = rule.mergeGapSec.toInt(),
                        unit = "秒",
                        range = 1..60,
                        step = 1
                    ) { value -> viewModel.updateRule { it.copy(mergeGapSec = value.toLong()) } }
                    SettingSwitch("全局休息锁定", "开启后休息期间覆盖桌面和普通应用", rule.globalBreak) {
                        viewModel.updateRule { current -> current.copy(globalBreak = it) }
                    }
                    SettingSwitch(
                        "提前提醒",
                        "默认剩余 5 分钟和 1 分钟提醒",
                        rule.warningFirstSec > 0 || rule.warningSecondSec > 0
                    ) { enabled ->
                        viewModel.updateRule {
                            it.copy(
                                warningFirstSec = if (enabled) 5 * 60L else 0L,
                                warningSecondSec = if (enabled) 60L else 0L
                            )
                        }
                    }
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingSwitch("每日累计额度", "多次使用按有效前台秒数累计", rule.dailyEnabled) {
                        viewModel.updateRule { current -> current.copy(dailyEnabled = it) }
                    }
                    NumericSetting(
                        title = "每日允许",
                        value = (rule.dailyLimitSec / 60).toInt(),
                        unit = "分钟",
                        range = 1..1440,
                        step = 15
                    ) { value -> viewModel.updateRule { it.copy(dailyLimitSec = value * 60L) } }
                }
            }
        }
        item { Text("时间段规则", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (state.schedules.isEmpty()) {
            item { Text("尚未配置。可添加“禁止使用”或“仅在以下时段允许”。") }
        } else {
            items(state.schedules, key = { it.id }) { window ->
                ScheduleCard(window, onDelete = { viewModel.deleteSchedule(window) })
            }
        }
        item { AddScheduleCard(viewModel) }
        state.message?.let { msg -> item { Text(msg, color = MaterialTheme.colorScheme.primary) } }
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun NumericSetting(
    title: String,
    value: Int,
    unit: String,
    range: IntRange,
    step: Int,
    onValue: (Int) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        IconButton(onClick = { onValue((value - step).coerceAtLeast(range.first)) }, enabled = value > range.first) {
            Icon(Icons.Rounded.Remove, contentDescription = "减少")
        }
        Text("$value $unit", modifier = Modifier.width(92.dp), fontWeight = FontWeight.Bold)
        IconButton(onClick = { onValue((value + step).coerceAtMost(range.last)) }, enabled = value < range.last) {
            Icon(Icons.Rounded.Add, contentDescription = "增加")
        }
    }
}

@Composable
private fun ScheduleCard(window: ScheduleWindowEntity, onDelete: () -> Unit) {
    Card {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (window.mode == ScheduleModes.BLOCK) "禁止使用" else "仅允许使用", fontWeight = FontWeight.Bold)
                Text("${formatDays(window.daysMask)}  ${formatMinute(window.startMinute)}-${formatMinute(window.endMinute)}")
                if (window.startMinute >= window.endMinute) Text("跨午夜", style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, contentDescription = "删除时段") }
        }
    }
}

@Composable
private fun AddScheduleCard(viewModel: RuleEditorViewModel) {
    var mode by remember { mutableStateOf(ScheduleModes.BLOCK) }
    var daysMask by remember { mutableIntStateOf(0b0011111) }
    var start by remember { mutableStateOf("19:00") }
    var end by remember { mutableStateOf("21:30") }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("新增时段", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mode == ScheduleModes.BLOCK, onClick = { mode = ScheduleModes.BLOCK }, label = { Text("禁止使用") })
                FilterChip(selected = mode == ScheduleModes.ALLOW, onClick = { mode = ScheduleModes.ALLOW }, label = { Text("仅允许使用") })
            }
            DaySelector(daysMask) { daysMask = it }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(start, { start = it }, label = { Text("开始 HH:mm") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(end, { end = it }, label = { Text("结束 HH:mm") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            Text("开始时间晚于或等于结束时间时按跨午夜处理。", style = MaterialTheme.typography.bodySmall)
            Button(onClick = { viewModel.addSchedule(mode, daysMask, start, end) }, modifier = Modifier.fillMaxWidth()) {
                Text("保存时段")
            }
        }
    }
}

@Composable
private fun DaySelector(mask: Int, onMask: (Int) -> Unit) {
    val days = listOf(
        DayOfWeek.MONDAY to "一", DayOfWeek.TUESDAY to "二", DayOfWeek.WEDNESDAY to "三",
        DayOfWeek.THURSDAY to "四", DayOfWeek.FRIDAY to "五", DayOfWeek.SATURDAY to "六", DayOfWeek.SUNDAY to "日"
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        days.chunked(4).forEach { rowDays ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                rowDays.forEach { (day, label) ->
                    val bit = ScheduleMatcher.dayBit(day)
                    FilterChip(
                        selected = mask and bit != 0,
                        onClick = { onMask(if (mask and bit != 0) mask and bit.inv() else mask or bit) },
                        label = { Text(label) }
                    )
                }
            }
        }
    }
}

private fun formatMinute(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)
private fun formatDays(mask: Int): String {
    if (mask == 0b1111111) return "每天"
    if (mask == 0b0011111) return "周一至周五"
    val names = listOf("一", "二", "三", "四", "五", "六", "日")
    return names.mapIndexedNotNull { i, name -> if (mask and (1 shl i) != 0) "周$name" else null }.joinToString("、")
}
