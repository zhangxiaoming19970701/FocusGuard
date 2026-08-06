package com.focusguard.app

import android.app.Application
import com.focusguard.app.system.HealthCheckScheduler
import com.focusguard.app.system.NotificationHelper
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FocusGuardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        HealthCheckScheduler.schedule(this)
    }
}
