package com.focusguard.app.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.focusguard.app.MainActivity
import com.focusguard.app.R

object NotificationHelper {
    const val CHANNEL_STATUS = "focusguard_status"
    const val CHANNEL_WARNING = "focusguard_warning"
    private const val STATUS_ID = 7001

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_STATUS, "保护状态", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "权限异常和保护失效提醒"
                },
                NotificationChannel(CHANNEL_WARNING, "使用时间提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "受管应用剩余使用时间提醒"
                }
            )
        )
    }

    fun showWarning(context: Context, appLabel: String, remainingSec: Long) {
        if (!PermissionUtils.notificationsEnabled(context)) return
        val minutes = if (remainingSec >= 60) "${remainingSec / 60} 分钟" else "$remainingSec 秒"
        val notification = NotificationCompat.Builder(context, CHANNEL_WARNING)
            .setSmallIcon(R.drawable.ic_focusguard)
            .setContentTitle("$appLabel 使用提醒")
            .setContentText("距离本次使用上限还剩 $minutes")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify((appLabel.hashCode() xor remainingSec.hashCode()), notification)
    }

    fun showProtectionFailure(context: Context, detail: String) {
        if (!PermissionUtils.notificationsEnabled(context)) return
        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val settingsPending = PendingIntent.getActivity(
            context,
            1,
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_focusguard)
            .setContentTitle("FocusGuard 保护失效")
            .setContentText(detail)
            .setContentIntent(pending)
            .addAction(0, "打开无障碍设置", settingsPending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(STATUS_ID, notification)
    }

    fun clearProtectionFailure(context: Context) {
        NotificationManagerCompat.from(context).cancel(STATUS_ID)
    }
}
