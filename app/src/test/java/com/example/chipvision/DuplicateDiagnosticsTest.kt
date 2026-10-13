package com.example.chipvision

import android.graphics.RectF
import com.example.chipvision.data.model.Detection
import com.example.chipvision.engine.DuplicateDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DuplicateDiagnosticsTest {
    @Test
    fun pairwiseGeometryCoversIdenticalAndNonOverlappingBoxes() {
        val identical = RectF(0f, 0f, 10f, 10f)
        val adjacent = RectF(10f, 0f, 20f, 10f)

        assertEquals(1f, DuplicateDiagnostics.intersectionOverUnion(identical, identical), 0f)
        assertEquals(0f, DuplicateDiagnostics.intersectionOverUnion(identical, adjacent), 0f)
        assertEquals(0f, DuplicateDiagnostics.centerDistance(identical, identical), 0f)
        assertEquals(10f, DuplicateDiagnostics.centerDistance(identical, adjacent), 0f)
    }

    @Test
    fun recordsOnlyOverlappingSameClassPairsAndFilterSurvival() {
        val first = Detection(RectF(0f, 0f, 10f, 10f), 0.90f, 0)
        val second = Detection(RectF(1f, 1f, 9f, 9f), 0.80f, 0)
        val adjacent = Detection(RectF(20f, 0f, 30f, 10f), 0.95f, 0)
        val differentClass = Detection(RectF(1f, 1f, 9f, 9f), 0.80f, 1)
        val diagnostics = DuplicateDiagnostics(maxFrames = 2, maxPairs = 10, sampleEveryNFrames = 1)

        diagnostics.recordFrame(1L, 100L, listOf(first, second, adjacent, differentClass), listOf(first, adjacent))

        val frame = diagnostics.snapshot().frames.single()
        assertEquals(4, frame.candidateCountBeforeFilter)
        assertEquals(2, frame.candidateCountAfterFilter)
        assertEquals(1, frame.overlappingSameClassPairs.size)
        assertTrue(frame.candidates[0].survivedFilter)
        assertFalse(frame.candidates[1].survivedFilter)
        assertEquals(0.1f, frame.overlappingSameClassPairs.single().confidenceDifference, 0.0001f)
    }

    @Test
    fun retentionIsBoundedAndResetClearsEvidence() {
        val diagnostics = DuplicateDiagnostics(maxFrames = 2, maxPairs = 10, sampleEveryNFrames = 1)
        repeat(4) { frameId ->
            diagnostics.recordFrame(
                frameId.toLong(),
                frameId.toLong(),
                listOf(Detection(RectF(0f, 0f, 1f, 1f), 0.9f, 0)),
                emptyList()
            )
        }

        assertEquals(2, diagnostics.snapshot().framesSampled)
        diagnostics.reset()
        assertEquals(0, diagnostics.snapshot().framesSampled)
        assertEquals(0L, diagnostics.snapshot().candidatePairComparisons)
    }

    @Test
    fun concurrentCollectionRemainsConsistent() {
        val diagnostics = DuplicateDiagnostics(maxFrames = 8, maxPairs = 10, sampleEveryNFrames = 1)
        val executor = Executors.newFixedThreadPool(4)
        val latch = CountDownLatch(4)
        repeat(4) { worker ->
            executor.execute {
                try {
                    repeat(25) { offset ->
                        val detection = Detection(
                            RectF(worker.toFloat(), 0f, worker + 10f, 10f),
                            0.9f,
                            0
                        )
                        diagnostics.recordFrame(
                            (worker * 25 + offset).toLong(),
                            (worker * 25 + offset).toLong(),
                            listOf(detection),
                            listOf(detection)
                        )
                    }
                } finally {
                    latch.countDown()
                }
            }
        }
        latch.await()
        executor.shutdown()

        val snapshot = diagnostics.snapshot()
        assertEquals(8, snapshot.framesSampled)
        assertEquals(0L, snapshot.candidatePairComparisons)
        assertTrue(snapshot.frames.all { it.candidates.single().survivedFilter })
    }
}
