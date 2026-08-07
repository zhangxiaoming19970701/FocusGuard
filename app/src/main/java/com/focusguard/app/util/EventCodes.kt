package com.focusguard.app.util

object EventCodes {
    const val SERVICE_CONNECTED = "EVT_SERVICE_CONNECTED"
    const val SERVICE_DISCONNECTED = "EVT_SERVICE_DISCONNECTED"
    const val SERVICE_INTERRUPTED = "EVT_SERVICE_INTERRUPTED"
    const val APP_FOREGROUND = "EVT_APP_FOREGROUND"
    const val SESSION_STARTED = "EVT_SESSION_STARTED"
    const val SESSION_ENDED = "EVT_SESSION_ENDED"
    const val WARNING_SHOWN = "EVT_WARNING_SHOWN"
    const val BLOCK_CONTINUOUS = "EVT_BLOCK_CONTINUOUS"
    const val BLOCK_DAILY = "EVT_BLOCK_DAILY"
    const val BLOCK_SCHEDULE = "EVT_BLOCK_SCHEDULE"
    const val BREAK_STARTED = "EVT_BREAK_STARTED"
    const val BREAK_ENDED = "EVT_BREAK_ENDED"
    const val ADMIN_OVERRIDE = "EVT_ADMIN_OVERRIDE"
    const val PERMISSION_CHANGED = "EVT_PERMISSION_CHANGED"
    const val CLOCK_CHANGED = "EVT_CLOCK_CHANGED"
    const val RECOVERY_COMPLETED = "EVT_RECOVERY_COMPLETED"
    const val ENGINE_ERROR = "EVT_ENGINE_ERROR"
    const val RULE_CHANGED = "EVT_RULE_CHANGED"
    const val CREDENTIAL_CHANGED = "EVT_CREDENTIAL_CHANGED"
}

object LockTypes {
    const val BREAK = "BREAK"
    const val DAILY = "DAILY"
    const val SCHEDULE = "SCHEDULE"
    const val PERMISSION = "PERMISSION"
}

object ScheduleModes {
    const val BLOCK = "BLOCK"
    const val ALLOW = "ALLOW"
}

object EndReasons {
    const val APP_SWITCH = "APP_SWITCH"
    const val SCREEN_OFF = "SCREEN_OFF"
    const val LIMIT_REACHED = "LIMIT_REACHED"
    const val SERVICE_STOP = "SERVICE_STOP"
    const val STALE_RECOVERY = "STALE_RECOVERY"
    const val RULE_CHANGED = "RULE_CHANGED"
}
