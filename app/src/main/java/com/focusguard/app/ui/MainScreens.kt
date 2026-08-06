package com.focusguard.app.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.focusguard.app.data.db.ManagedAppEntity
import com.focusguard.app.system.InstalledApp
import com.focusguard.app.system.PermissionHealth
import com.focusguard.app.util.EventCodes
import java.text.DateFormat
import java.util.Date

@Composable
fun FocusGuardRoot(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when {
        !state.settings.disclosureAccepted -> DisclosureScreen(viewModel)
        !state.credentialConfigured -> CreatePinScreen(viewModel)
        !state.settings.setupComplete -> RecoveryCodeScreen(state, viewModel)
        !state.settings.permissionGuideAcknowledged -> PermissionSetupScreen(state, viewModel)
        !state.adminAuthenticated -> AdminGateScreen(state, viewModel)
        else -> AdminApp(state, viewModel)
    }
}

@Composable
private fun DisclosureScreen(viewModel: MainViewModel) {
    var parentMode by remember { mutableStateOf(true) }
    var consent by remember { mutableStateOf(false) }
    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("FocusGuard", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text("家长管理与个人数字自律工具", style = MaterialTheme.typography.titleMedium)
            }
            item {
                Card {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("权限醒目披露", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("FocusGuard 使用 Android 无障碍服务检测当前前台应用的包名和窗口变化，以便按你设置的连续使用、每日额度和时段规则执行提醒或拦截。")
                        Text("应用不会读取或上传聊天内容、网页内容、照片、联系人、短信、麦克风、键盘输入，也不会保存密码明文。所有规则和统计默认仅保存在本机。")
                        Text("普通安装模式不能绝对防止卸载、强制停止、关闭权限或修改系统时间。")
                    }
                }
            }
            item {
                Text("使用模式", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = parentMode, onCheckedChange = { parentMode = true })
                    Text("家长管理")
                    Spacer(Modifier.size(20.dp))
                    Checkbox(checked = !parentMode, onCheckedChange = { parentMode = false })
                    Text("个人自律")
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = consent, onCheckedChange = { consent = it })
                    Text("我已阅读并主动同意上述用途和隐私边界")
                }
            }
            item {
                Button(
                    onClick = { viewModel.acceptDisclosure(if (parentMode) "PARENT" else "SELF") },
                    enabled = consent,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("同意并继续") }
            }
        }
    }
}

@Composable
private fun CreatePinScreen(viewModel: MainViewModel) {
    var first by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    var localMessage by remember { mutableStateOf<String?>(null) }
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(56.dp))
            Text("设置管理密码", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(if (confirming) "再次输入相同密码" else "请输入 4-8 位数字；默认建议 6 位")
            Spacer(Modifier.height(24.dp))
            PinPad(
                value = if (confirming) confirmation else first,
                maxLength = 8,
                onValueChange = { if (confirming) confirmation = it else first = it },
                onConfirm = {
                    if (!confirming) {
                        if (first.length < 4) localMessage = "至少输入 4 位" else confirming = true
                    } else if (first == confirmation) {
                        viewModel.createCredential(first)
                    } else {
                        localMessage = "两次密码不一致，请重新输入"
                        first = ""; confirmation = ""; confirming = false
                    }
                }
            )
            localMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun RecoveryCodeScreen(state: MainUiState, viewModel: MainViewModel) {
    var pin by remember { mutableStateOf("") }
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("保存恢复码", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            if (state.recoveryCode != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Text(
                        state.recoveryCode,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(24.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("恢复码只显示这一次。请抄写并放在安全位置；应用只保存其带盐哈希。")
                Spacer(Modifier.height(24.dp))
                Button(onClick = viewModel::acknowledgeRecoveryCode) { Text("我已安全保存") }
            } else {
                Text("上次设置过程被中断。输入管理密码后重新生成恢复码。")
                Spacer(Modifier.height(16.dp))
                PinPad(pin, { pin = it }, { viewModel.regenerateRecoveryCode(pin); pin = "" }, maxLength = 8)
            }
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun PermissionSetupScreen(state: MainUiState, viewModel: MainViewModel) {
    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text("完成系统授权", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("必须开启无障碍监测；使用情况访问用于重连后的统计校核。系统设置页面返回后，本页会自动刷新。")
            }
            item { PermissionRow("1. 无障碍监测服务", state.health.accessibilityEnabled, "核心必需：识别前台应用并执行拦截") { openAccessibilitySettings(context) } }
            item { PermissionRow("2. 使用情况访问", state.health.usageAccess, "建议开启：校核统计缺口") { openUsageSettings(context) } }
            item {
                PermissionRow("3. 通知", state.health.notificationsEnabled, "提前提醒和保护异常告警") {
                    if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else openNotificationSettings(context)
                }
            }
            item { PermissionRow("4. 电池后台设置", state.health.batteryOptimizationIgnored, "部分厂商需手动允许后台运行") { openBatterySettings(context) } }
            item {
                Text(
                    if (state.health.accessibilityEnabled) "无障碍权限已开启，正在等待服务连接。" else "请先开启无障碍监测服务。",
                    color = if (state.health.accessibilityEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
            item {
                Button(
                    onClick = viewModel::completePermissionGuide,
                    enabled = state.health.accessibilityEnabled,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("完成必要授权并进入管理界面") }
            }
        }
    }
}

@Composable
private fun AdminGateScreen(state: MainUiState, viewModel: MainViewModel) {
    var pin by remember { mutableStateOf("") }
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ProtectionBadge(state.health)
            Spacer(Modifier.height(20.dp))
            Text("管理员验证", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("验证成功后的 5 分钟内可修改应用、规则和统计。")
            Spacer(Modifier.height(20.dp))
            PinPad(
                value = pin,
                onValueChange = { pin = it },
                onConfirm = { viewModel.verifyAdmin(pin); pin = "" },
                maxLength = 8,
                enabled = !state.busy
            )
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

private data class NavItem(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminApp(state: MainUiState, viewModel: MainViewModel) {
    val navController = rememberNavController()
    val items = listOf(
        NavItem("home", "首页", Icons.Rounded.Home),
        NavItem("apps", "应用", Icons.Rounded.Apps),
        NavItem("stats", "统计", Icons.Rounded.BarChart),
        NavItem("diagnostics", "诊断", Icons.Rounded.HealthAndSafety)
    )
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route.orEmpty()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(routeTitle(route)) },
                actions = {
                    IconButton(onClick = viewModel::lockAdmin) {
                        Icon(Icons.Rounded.Logout, contentDescription = "锁定管理员界面")
                    }
                }
            )
        },
        bottomBar = {
            if (!route.startsWith("rule/")) {
                NavigationBar {
                    items.forEach { item ->
                        NavigationBarItem(
                            selected = route == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.title) },
                            label = { Text(item.title) }
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        NavHost(navController, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { HomeScreen(state, viewModel) { navController.navigate("rule/$it") } }
            composable("apps") { AppsScreen(state, viewModel) { navController.navigate("rule/$it") } }
            composable("stats") { StatsScreen(state) }
            composable("diagnostics") { DiagnosticsScreen(state, viewModel) }
            composable(
                route = "rule/{packageName}",
                arguments = listOf(navArgument("packageName") { nullable = false })
            ) {
                RuleEditorScreen(onBack = { navController.popBackStack() }, viewModel = hiltViewModel())
            }
        }
    }
}

private fun routeTitle(route: String): String = when {
    route.startsWith("rule/") -> "规则设置"
    route == "apps" -> "受管应用"
    route == "stats" -> "使用统计"
    route == "diagnostics" -> "诊断与权限"
    else -> "FocusGuard"
}

@Composable
private fun HomeScreen(state: MainUiState, viewModel: MainViewModel, onEdit: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ProtectionCard(state.health) }
        item {
            Card {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("监测总开关", fontWeight = FontWeight.Bold)
                        Text(if (state.settings.monitoringEnabled) "规则引擎已启用" else "规则引擎已暂停")
                    }
                    Switch(state.settings.monitoringEnabled, viewModel::setMonitoringEnabled)
                }
            }
        }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("管理员临时解除时长", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 15, 30).forEach { minutes ->
                            FilterChip(
                                selected = state.settings.overrideDurationSec == minutes * 60L,
                                onClick = { viewModel.setOverrideMinutes(minutes) },
                                label = { Text("$minutes 分钟") }
                            )
                        }
                    }
                }
            }
        }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("每日额度重置点", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 4).forEach { hour ->
                            FilterChip(
                                selected = state.settings.resetMinute == hour * 60,
                                onClick = { viewModel.setResetHour(hour) },
                                label = { Text("%02d:00".format(hour)) }
                            )
                        }
                    }
                }
            }
        }
        item { Text("今日受管应用", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (state.managedApps.isEmpty()) {
            item { Text("尚未选择受管应用。请进入“应用”页面添加。") }
        } else {
            items(state.managedApps, key = { it.packageName }) { app ->
                val used = state.dailyUsage[app.packageName]?.usedSec ?: 0L
                val rule = state.rules[app.packageName]
                ManagedAppCard(app, used, rule?.dailyLimitSec, onEdit)
            }
        }
    }
}

@Composable
private fun ProtectionCard(health: PermissionHealth) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (health.protectionReady) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (health.protectionReady) "保护正常" else "保护异常",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(if (health.protectionReady) "无障碍监测服务已连接" else "请到“诊断”页面修复关键权限")
        }
    }
}

@Composable
private fun ProtectionBadge(health: PermissionHealth) {
    Text(
        if (health.protectionReady) "● 保护正常" else "● 保护异常",
        color = if (health.protectionReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun ManagedAppCard(app: ManagedAppEntity, usedSec: Long, dailyLimitSec: Long?, onEdit: (String) -> Unit) {
    Card(onClick = { onEdit(app.packageName) }) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.packageName, Modifier.size(44.dp))
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.appLabel, fontWeight = FontWeight.Bold)
                Text(app.packageName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val limit = dailyLimitSec?.let { formatDuration(it) } ?: "未配置"
                Text("今日 ${formatDuration(usedSec)} / $limit")
            }
            Text("设置")
        }
    }
}

@Composable
private fun AppsScreen(state: MainUiState, viewModel: MainViewModel, onEdit: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val selected = remember(state.managedApps) { state.managedApps.associateBy { it.packageName } }
    val filtered = remember(state.installedApps, query) {
        state.installedApps.filter {
            query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true)
        }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("搜索应用名称或包名") },
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )
        LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
            items(filtered, key = { it.packageName }) { app ->
                val checked = app.packageName in selected
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(app.packageName, Modifier.size(42.dp))
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, fontWeight = FontWeight.Medium)
                        Text(app.packageName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (app.isCritical) Text("系统安全白名单", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                    if (checked) TextButton(onClick = { onEdit(app.packageName) }) { Text("规则") }
                    Switch(
                        checked = checked,
                        enabled = !app.isCritical,
                        onCheckedChange = { viewModel.setManaged(app, it) }
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun StatsScreen(state: MainUiState) {
    val sevenDayTotals = remember(state.recentUsage) {
        state.recentUsage.groupBy { it.packageName }.mapValues { (_, rows) -> rows.sumOf { it.usedSec } }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Text("今日与近 7 日使用", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(state.managedApps.sortedByDescending { sevenDayTotals[it.packageName] ?: 0L }) { app ->
            val today = state.dailyUsage[app.packageName]?.usedSec ?: 0L
            val seven = sevenDayTotals[app.packageName] ?: 0L
            val appEvents = state.sevenDayAudit.filter { it.packageName == app.packageName }
            val triggerCount = appEvents.count {
                it.eventType == EventCodes.BLOCK_CONTINUOUS ||
                    it.eventType == EventCodes.BLOCK_DAILY ||
                    it.eventType == EventCodes.BLOCK_SCHEDULE
            }
            val overrideCount = appEvents.count { it.eventType == EventCodes.ADMIN_OVERRIDE }
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(app.appLabel, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("今日 ${formatDuration(today)}")
                        Text("近 7 日 ${formatDuration(seven)}", fontWeight = FontWeight.Medium)
                    }
                    Text("近 7 日触发 $triggerCount 次，管理员解除 $overrideCount 次", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Text("最近事件", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(state.auditEvents.take(30)) { event ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(event.eventType, fontWeight = FontWeight.Medium)
                Text(
                    "${DateFormat.getDateTimeInstance().format(Date(event.timestamp))}  ${event.packageName.orEmpty()} ${event.detailCode.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun DiagnosticsScreen(state: MainUiState, viewModel: MainViewModel) {
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    var confirmFactory by remember { mutableStateOf(false) }
    var changePassword by remember { mutableStateOf(false) }
    val health = state.health
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { PermissionRow("无障碍监测服务", health.accessibilityEnabled && health.serviceConnected, "核心必需") { openAccessibilitySettings(context) } }
        item { PermissionRow("使用情况访问", health.usageAccess, "用于统计校核") { openUsageSettings(context) } }
        item { PermissionRow("通知", health.notificationsEnabled, "用于提醒和保护异常") { openNotificationSettings(context) } }
        item { PermissionRow("忽略电池优化", health.batteryOptimizationIgnored, "部分厂商建议开启") { openBatterySettings(context) } }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("设备与版本", fontWeight = FontWeight.Bold)
                    Text("Android ${android.os.Build.VERSION.RELEASE} / API ${android.os.Build.VERSION.SDK_INT}")
                    Text("厂商 ${android.os.Build.MANUFACTURER}，型号 ${android.os.Build.MODEL}")
                    Text("服务最近事件：${if (state.settings.lastServiceEventWall > 0) DateFormat.getDateTimeInstance().format(Date(state.settings.lastServiceEventWall)) else "无"}")
                    OutlinedButton(onClick = { copyDiagnostics(context, state) }) { Text("复制诊断信息") }
                }
            }
        }
        item { OutlinedButton(onClick = { changePassword = true }, modifier = Modifier.fillMaxWidth()) { Text("修改管理密码") } }
        item { OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) { Text("清理本机统计和事件日志") } }
        item { OutlinedButton(onClick = { confirmFactory = true }, modifier = Modifier.fillMaxWidth()) { Text("恢复出厂设置") } }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("确认清理？") },
            text = { Text("受管应用和规则会保留，但使用统计与事件日志将删除。") },
            confirmButton = { TextButton(onClick = { confirmClear = false; viewModel.clearStatistics() }) { Text("清理") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } }
        )
    }
    if (confirmFactory) {
        AlertDialog(
            onDismissRequest = { confirmFactory = false },
            title = { Text("恢复出厂设置？") },
            text = { Text("将删除管理密码、恢复码、全部规则、应用选择、统计和日志。此操作不可撤销。") },
            confirmButton = { TextButton(onClick = { confirmFactory = false; viewModel.factoryReset() }) { Text("全部删除") } },
            dismissButton = { TextButton(onClick = { confirmFactory = false }) { Text("取消") } }
        )
    }
    if (changePassword) {
        ChangePasswordDialog(
            onDismiss = { changePassword = false },
            onSubmit = { old, new, confirm ->
                changePassword = false
                viewModel.changeCredential(old, new, confirm)
            }
        )
    }
    state.recoveryCode?.let { code ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text("新的恢复码") },
            text = { Column { Text("请立即抄写并安全保存："); Text(code, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) } },
            confirmButton = { TextButton(onClick = viewModel::acknowledgeRecoveryCode) { Text("我已保存") } }
        )
    }
}

@Composable
private fun ChangePasswordDialog(onDismiss: () -> Unit, onSubmit: (String, String, String) -> Unit) {
    var step by remember { mutableStateOf(0) }
    var old by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val value = when (step) { 0 -> old; 1 -> newPin; else -> confirm }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(when (step) { 0 -> "输入旧密码或恢复码"; 1 -> "输入新密码"; else -> "再次输入新密码" }) },
        text = {
            PinPad(
                value = value,
                onValueChange = { v -> when (step) { 0 -> old = v; 1 -> newPin = v; else -> confirm = v } },
                onConfirm = { if (step < 2) step++ else onSubmit(old, newPin, confirm) },
                maxLength = 8
            )
        },
        confirmButton = { },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun PermissionRow(title: String, ok: Boolean, detail: String, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
            Text(if (ok) "正常" else "去设置", color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }
    if (bitmap != null) Image(bitmap, contentDescription = null, modifier = modifier)
    else Box(modifier, contentAlignment = Alignment.Center) { Text(packageName.take(1).uppercase()) }
}

private fun openAccessibilitySettings(context: Context) = context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
private fun openUsageSettings(context: Context) = context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
private fun openNotificationSettings(context: Context) = context.startActivity(
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
)
private fun openBatterySettings(context: Context) = context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

private fun copyDiagnostics(context: Context, state: MainUiState) {
    val text = buildString {
        appendLine("FocusGuard 0.1.0")
        appendLine("Android ${android.os.Build.VERSION.RELEASE} API ${android.os.Build.VERSION.SDK_INT}")
        appendLine("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        appendLine("accessibility=${state.health.accessibilityEnabled}")
        appendLine("serviceConnected=${state.health.serviceConnected}")
        appendLine("usageAccess=${state.health.usageAccess}")
        appendLine("notifications=${state.health.notificationsEnabled}")
        appendLine("batteryIgnored=${state.health.batteryOptimizationIgnored}")
        state.auditEvents.take(20).forEach { appendLine("${it.timestamp}|${it.eventType}|${it.packageName.orEmpty()}|${it.detailCode.orEmpty()}") }
    }
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("FocusGuard diagnostics", text))
}

fun formatDuration(seconds: Long): String {
    val totalMinutes = seconds.coerceAtLeast(0L) / 60L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0) "${hours}小时${minutes}分" else "${minutes}分钟"
}
