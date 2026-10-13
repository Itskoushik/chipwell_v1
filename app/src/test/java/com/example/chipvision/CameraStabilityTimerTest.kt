package com.example.chipvision

import com.example.chipvision.tracking.CameraStabilityTimer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraStabilityTimerTest {

    @Test
    fun cameraStabilityIsNotEligibleAt199Ms() {
        val timer = CameraStabilityTimer()

        assertFalse(timer.update(cameraMoving = false, nowMs = 1_000L, requiredDurationMs = 200L))
        assertFalse(timer.update(cameraMoving = false, nowMs = 1_199L, requiredDurationMs = 200L))
    }

    @Test
    fun cameraStabilityReachesDeadlineAfterExactly200Ms() {
        val timer = CameraStabilityTimer()

        assertFalse(timer.update(cameraMoving = false, nowMs = 1_000L, requiredDurationMs = 200L))
        assertTrue(timer.update(cameraMoving = false, nowMs = 1_200L, requiredDurationMs = 200L))
    }

    @Test
    fun confirmedCameraMovementResetsTheDeadline() {
        val timer = CameraStabilityTimer()

        timer.update(cameraMoving = false, nowMs = 1_000L, requiredDurationMs = 200L)
        assertFalse(timer.update(cameraMoving = true, nowMs = 1_100L, requiredDurationMs = 200L))
        assertFalse(timer.update(cameraMoving = false, nowMs = 1_100L, requiredDurationMs = 200L))
        assertFalse(timer.update(cameraMoving = false, nowMs = 1_299L, requiredDurationMs = 200L))
        assertTrue(timer.update(cameraMoving = false, nowMs = 1_300L, requiredDurationMs = 200L))
    }

    @Test
    fun repeatedStableUpdatesPreserveTheOriginalCameraStart() {
        val timer = CameraStabilityTimer()

        timer.update(cameraMoving = false, nowMs = 1_000L, requiredDurationMs = 200L)
        timer.update(cameraMoving = false, nowMs = 1_100L, requiredDurationMs = 200L)
        timer.update(cameraMoving = false, nowMs = 1_200L, requiredDurationMs = 200L)

        assertTrue(timer.update(cameraMoving = false, nowMs = 1_200L, requiredDurationMs = 200L))
    }

    @Test
    fun boxJitterDoesNotResetCameraTimer() {
        val timer = CameraStabilityTimer()

        timer.update(cameraMoving = false, nowMs = 1_000L, requiredDurationMs = 200L)
        // Box geometry is intentionally absent from this camera-only timer.
        assertFalse(timer.update(cameraMoving = false, nowMs = 1_199L, requiredDurationMs = 200L))
        assertTrue(timer.update(cameraMoving = false, nowMs = 1_200L, requiredDurationMs = 200L))
    }
}
