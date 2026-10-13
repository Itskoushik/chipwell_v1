package com.example.chipvision

import com.example.chipvision.audio.FailureBeepLoopState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FailureBeepLoopStateTest {
    @Test
    fun repeatedSchedulingCannotStartAnOverlappingLoop() {
        val state = FailureBeepLoopState()

        assertTrue(state.start())
        assertFalse(state.start())
        assertTrue(state.isActive())
    }

    @Test
    fun cancellationStopsTheActiveLoop() {
        val state = FailureBeepLoopState()

        state.start()
        state.stop()

        assertFalse(state.isActive())
        assertTrue(state.start())
    }
}
