package com.example.chipvision.tracking

/**
 * Authoritative monotonic timer for camera-only stability.
 */
class CameraStabilityTimer {
    private var stableSinceMs: Long? = null

    fun update(
        cameraMoving: Boolean,
        nowMs: Long,
        requiredDurationMs: Long = DEFAULT_DURATION_MS
    ): Boolean {
        if (cameraMoving) {
            stableSinceMs = null
            return false
        }

        if (stableSinceMs == null) {
            stableSinceMs = nowMs
        }
        return nowMs - stableSinceMs!! >= requiredDurationMs
    }

    fun reset() {
        stableSinceMs = null
    }

    fun stableSinceMs(): Long? = stableSinceMs

    companion object {
        const val DEFAULT_DURATION_MS = 200L
    }
}
