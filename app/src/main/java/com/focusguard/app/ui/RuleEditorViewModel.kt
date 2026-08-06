package com.focusguard.app.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.app.data.db.ManagedAppEntity
import com.focusguard.app.data.db.RuleEntity
import com.focusguard.app.data.db.ScheduleWindowEntity
import com.focusguard.app.data.repo.FocusGuardRepository
import com.focusguard.app.util.ScheduleModes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RuleEditorUiState(
    val app: ManagedAppEntity? = null,
    val rule: RuleEntity? = null,
    val schedules: List<ScheduleWindowEntity> = emptyList(),
    val message: String? = null
)

@HiltViewModel
class RuleEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: FocusGuardRepository
) : ViewModel() {
    val packageName: String = requireNotNull(savedStateHandle["packageName"])
    private val message = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    val uiState: StateFlow<RuleEditorUiState> = combine(
        repository.managedApps.map { list -> list.firstOrNull { it.packageName == packageName } },
        repository.rules.map { list -> list.firstOrNull { it.packageName == packageName } },
        repository.schedules(packageName),
        message
    ) { app, rule, schedules, msg ->
        RuleEditorUiState(app, rule ?: RuleEntity(packageName = packageName), schedules, msg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RuleEditorUiState())

    fun updateRule(transform: (RuleEntity) -> RuleEntity) = viewModelScope.launch {
        val current = repository.getRule(packageName) ?: RuleEntity(packageName = packageName)
        repository.upsertRule(transform(current))
    }

    fun addSchedule(mode: String, daysMask: Int, startText: String, endText: String) = viewModelScope.launch {
        val start = parseMinute(startText)
        val end = parseMinute(endText)
        when {
            mode !in setOf(ScheduleModes.BLOCK, ScheduleModes.ALLOW) -> message.value = "无效的时段类型"
            daysMask == 0 -> message.value = "请至少选择一天"
            start == null || end == null -> message.value = "时间格式应为 HH:mm"
            else -> {
                val candidate = ScheduleWindowEntity(
                    packageName = packageName,
                    mode = mode,
                    daysMask = daysMask,
                    startMinute = start,
                    endMinute = end,
                    label = if (mode == ScheduleModes.BLOCK) "禁用时段" else "允许时段"
                )
                val overlaps = repository.getSchedules(packageName).any { schedulesOverlap(it, candidate) }
                if (overlaps) {
                    message.value = "该时段与现有规则重叠，请先调整或删除冲突时段"
                } else {
                    repository.upsertSchedule(candidate)
                    message.value = "时段已保存"
                }
            }
        }
    }

    fun deleteSchedule(window: ScheduleWindowEntity) = viewModelScope.launch {
        repository.deleteSchedule(window)
    }

    fun clearMessage() { message.value = null }

    private fun schedulesOverlap(a: ScheduleWindowEntity, b: ScheduleWindowEntity): Boolean {
        val aIntervals = weekIntervals(a)
        val bIntervals = weekIntervals(b)
        return aIntervals.any { x -> bIntervals.any { y -> x.first < y.second && y.first < x.second } }
    }

    private fun weekIntervals(window: ScheduleWindowEntity): List<Pair<Int, Int>> {
        val week = 7 * 24 * 60
        val result = mutableListOf<Pair<Int, Int>>()
        for (day in 0 until 7) {
            if (window.daysMask and (1 shl day) == 0) continue
            val start = day * 1440 + window.startMinute
            val duration = when {
                window.startMinute < window.endMinute -> window.endMinute - window.startMinute
                window.startMinute > window.endMinute -> 1440 - window.startMinute + window.endMinute
                else -> 1440
            }
            val end = start + duration
            if (end <= week) result += start to end
            else {
                result += start to week
                result += 0 to (end - week)
            }
        }
        return result
    }

    private fun parseMinute(text: String): Int? {
        val match = Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(text.trim()) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }
}
