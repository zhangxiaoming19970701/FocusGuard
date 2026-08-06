package com.focusguard.app.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusguard.app.data.repo.FocusGuardRepository
import com.focusguard.app.util.EventCodes
import com.focusguard.app.util.LockTypes
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TimeChangeReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: FocusGuardRepository

    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.audit(EventCodes.CLOCK_CHANGED, detail = intent.action)
                val lock = repository.getLock()
                if (lock?.lockType == LockTypes.SCHEDULE || lock?.lockType == LockTypes.DAILY) {
                    repository.clearLock("CLOCK_REEVALUATE")
                }
            } finally {
                result.finish()
            }
        }
    }
}
