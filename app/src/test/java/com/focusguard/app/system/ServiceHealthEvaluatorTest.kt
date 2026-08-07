package com.focusguard.app.system

import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceHealthEvaluatorTest {
    private val now = 1_000_000_000L

    @Test
    fun `disabled permission always wins over stale stored state`() {
        assertEquals(
            ServiceHealthStatus.PERMISSION_DISABLED,
            ServiceHealthEvaluator.evaluate(
                permissionEnabled = false,
                runtimeConnected = false,
                storedConnected = true,
                lastHeartbeatWall = now,
                nowWall = now
            )
        )
    }

    @Test
    fun `live runtime is healthy even when persisted heartbeat is stale`() {
        assertEquals(
            ServiceHealthStatus.RUNTIME_CONNECTED,
            ServiceHealthEvaluator.evaluate(
                permissionEnabled = true,
                runtimeConnected = true,
                storedConnected = true,
                lastHeartbeatWall = now - SERVICE_STALE_AFTER_MS,
                nowWall = now
            )
        )
    }

    @Test
    fun `recent heartbeat tolerates a worker-started process`() {
        assertEquals(
            ServiceHealthStatus.RECENT_HEARTBEAT,
            ServiceHealthEvaluator.evaluate(
                permissionEnabled = true,
                runtimeConnected = false,
                storedConnected = true,
                lastHeartbeatWall = now - SERVICE_STALE_AFTER_MS + 1L,
                nowWall = now
            )
        )
    }

    @Test
    fun `expired heartbeat reports stale service even when permission remains enabled`() {
        assertEquals(
            ServiceHealthStatus.SERVICE_STALE,
            ServiceHealthEvaluator.evaluate(
                permissionEnabled = true,
                runtimeConnected = false,
                storedConnected = true,
                lastHeartbeatWall = now - SERVICE_STALE_AFTER_MS,
                nowWall = now
            )
        )
    }

    @Test
    fun `missing connection state is stale rather than healthy`() {
        assertEquals(
            ServiceHealthStatus.SERVICE_STALE,
            ServiceHealthEvaluator.evaluate(
                permissionEnabled = true,
                runtimeConnected = false,
                storedConnected = false,
                lastHeartbeatWall = 0L,
                nowWall = now
            )
        )
    }
}
