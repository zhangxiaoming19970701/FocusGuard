package com.focusguard.app.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        HealthCheckScheduler.runOnce(context)
        HealthCheckScheduler.schedule(context)
    }
}
