package com.example.chipvision

import com.example.chipvision.engine.PerformanceMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PerformanceMetricsTest {
    @Test
    fun boundedSamplesCalculateAverageP95AndMaximum() {
        val metrics = PerformanceMetrics(maxSamples = 4)
        listOf(10L, 20L, 30L, 40L, 100L).forEach(metrics::recordDetector)

        val summary = metrics.snapshot().detector
        assertEquals(4, summary.count)
        assertEquals(47.5, summary.averageMs, 0.001)
        assertEquals(100.0, summary.p95Ms, 0.001)
        assertEquals(100L, summary.maximumMs)
    }

    @Test
    fun emptyMetricsAreSafe() {
        val summary = PerformanceMetrics(maxSamples = 2).snapshot().classifier

        assertEquals(0, summary.count)
        assertEquals(0.0, summary.averageMs, 0.0)
        assertEquals(0L, summary.maximumMs)
    }

    @Test
    fun countersAndResetAreAccurate() {
        val metrics = PerformanceMetrics()
        metrics.recordFrameAnalysis(5)
        metrics.recordSkippedFrame()
        metrics.recordStaleResultDiscarded()
        metrics.recordInspectionCycle(25)

        val beforeReset = metrics.snapshot()
        assertEquals(1L, beforeReset.processedFrames)
        assertEquals(1L, beforeReset.skippedFrames)
        assertEquals(1L, beforeReset.staleResultsDiscarded)
        assertEquals(1, beforeReset.completedInspections)

        metrics.reset()
        val afterReset = metrics.snapshot()
        assertEquals(0L, afterReset.processedFrames)
        assertEquals(0, afterReset.inspectionCycle.count)
    }

    @Test
    fun concurrentUpdatesRemainBoundedAndAccurate() {
        val metrics = PerformanceMetrics(maxSamples = 8)
        val executor = Executors.newFixedThreadPool(4)
        val ready = CountDownLatch(4)
        val release = CountDownLatch(1)

        try {
            repeat(4) {
                executor.submit {
                    ready.countDown()
                    release.await()
                    repeat(10) {
                        metrics.recordFrameAnalysis(1)
                        metrics.recordSkippedFrame()
                    }
                }
            }
            ready.await()
            release.countDown()
            executor.shutdown()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))

            val snapshot = metrics.snapshot()
            assertEquals(8, snapshot.frameAnalysis.count)
            assertEquals(40L, snapshot.processedFrames)
            assertEquals(40L, snapshot.skippedFrames)
            assertTrue(snapshot.frameAnalysis.maximumMs >= 1L)
        } finally {
            executor.shutdownNow()
        }
    }
}
