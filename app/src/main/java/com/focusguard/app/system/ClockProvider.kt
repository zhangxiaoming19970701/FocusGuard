package com.focusguard.app.system

import android.content.ContentResolver
import android.os.SystemClock
import android.provider.Settings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClockProvider @Inject constructor(
    private val contentResolver: ContentResolver
) {
    fun wallMillis(): Long = System.currentTimeMillis()
    fun elapsedMillis(): Long = SystemClock.elapsedRealtime()
    fun bootCount(): Int = runCatching {
        Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT)
    }.getOrDefault(-1)
}
