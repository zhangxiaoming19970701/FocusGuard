package com.focusguard.app.system

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.telecom.TelecomManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val isCritical: Boolean
)

@Singleton
class AppCatalog @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun launcherApps(): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val critical = SystemWhitelist.nonManageablePackages(context)
        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                val appInfo = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
                InstalledApp(
                    packageName = pkg,
                    label = info.loadLabel(pm)?.toString()?.ifBlank { pkg } ?: pkg,
                    isSystem = appInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) != 0,
                    isCritical = pkg in critical || pkg == context.packageName
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}

object SystemWhitelist {
    private val fixedNonManageable = setOf(
        "com.android.systemui",
        "com.android.settings",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.emergency",
        "com.google.android.dialer",
        "com.android.dialer",
        "com.samsung.android.dialer"
    )

    private val fixedSafety = setOf(
        "com.android.systemui",
        "com.android.emergency",
        "com.google.android.dialer",
        "com.android.dialer",
        "com.samsung.android.dialer"
    )

    fun nonManageablePackages(context: Context): Set<String> {
        val result = fixedNonManageable.toMutableSet()
        result += context.packageName
        runCatching {
            context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
        }.getOrNull()?.let(result::add)
        return result
    }

    fun safetyCriticalPackages(context: Context): Set<String> {
        val result = fixedSafety.toMutableSet()
        result += context.packageName
        runCatching {
            context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
        }.getOrNull()?.let(result::add)
        return result
    }

    fun isSafetyCritical(context: Context, packageName: String): Boolean =
        packageName in safetyCriticalPackages(context) ||
            packageName.contains("incallui", ignoreCase = true) ||
            packageName.contains("emergency", ignoreCase = true)
}
