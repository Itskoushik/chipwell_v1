package com.example.chipvision

import com.example.chipvision.engine.InspectionDispatchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class InspectionDispatchStateTest {
    @Test
    fun onlyOneClassificationArrangementIsActive() {
        val state = InspectionDispatchState()

        assertTrue(state.begin(10))
        assertFalse(state.begin(20))
        assertEquals(10, state.activeSceneHash())
    }

    @Test
    fun newestArrangementReplacesOlderPendingArrangement() {
        val state = InspectionDispatchState()
        state.begin(10)

        state.observe(20)
        state.observe(30)

        assertEquals(30, state.pendingSceneHash())
        assertEquals(30, state.complete())
        assertNull(state.activeSceneHash())
        assertNull(state.pendingSceneHash())
    }

    @Test
    fun unchangedArrangementIsNotQueued() {
        val state = InspectionDispatchState()
        state.begin(10)

        state.observe(10)

        assertNull(state.pendingSceneHash())
    }

    @Test
    fun concurrentObservationsRetainOneNewestPendingArrangement() {
        val state = InspectionDispatchState()
        state.begin(10)
        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val release = CountDownLatch(1)

        try {
            val first = executor.submit {
                ready.countDown()
                release.await()
                state.observe(20)
            }
            val second = executor.submit {
                ready.countDown()
                release.await()
                state.observe(30)
            }

            ready.await()
            release.countDown()
            first.get()
            second.get()

            assertTrue(state.pendingSceneHash() == 20 || state.pendingSceneHash() == 30)
            assertEquals(state.pendingSceneHash(), state.complete())
        } finally {
            executor.shutdownNow()
        }
    }
}
