package com.example.chipvision

import android.graphics.RectF
import com.example.chipvision.data.model.Detection
import com.example.chipvision.engine.DetectionFrameFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DetectionFrameFilterTest {
    private fun chip(box: RectF, confidence: Float = 0.9f) =
        Detection(box, confidence, 0)

    private fun empty(box: RectF, confidence: Float = 0.9f) =
        Detection(box, confidence, 1)

    @Test
    fun emptyBelowThresholdIsIgnored() {
        val result = DetectionFrameFilter.filter(listOf(empty(RectF(0f, 0f, 10f, 10f), 0.69f)), 0.70f)
        assertTrue(result.detections.isEmpty())
    }

    @Test
    fun chipAtConfidenceThresholdIsAccepted() {
        val result = DetectionFrameFilter.filter(
            listOf(chip(RectF(0f, 0f, 10f, 10f), 0.70f)),
            0.70f
        )

        assertEquals(1, result.acceptedChipCount)
        assertEquals(1, result.detections.size)
    }

    @Test
    fun overlappingEmptyIsConflictingAndIgnored() {
        val result = DetectionFrameFilter.filter(
            listOf(chip(RectF(0f, 0f, 10f, 10f)), empty(RectF(0f, 0f, 8f, 8f))),
            0.70f
        )
        assertTrue(result.validatedEmptyDetections.isEmpty())
        assertEquals(1, result.acceptedChipCount)
    }

    @Test
    fun nonOverlappingEmptyFailsBeforeChipFiltering() {
        val result = DetectionFrameFilter.filter(
            listOf(chip(RectF(0f, 0f, 10f, 10f)), empty(RectF(20f, 20f, 30f, 30f))),
            0.70f
        )
        assertEquals(1, result.validatedEmptyDetections.size)
        assertEquals(2, result.detections.size)
        assertEquals(1, result.acceptedChipCount)
    }

    @Test
    fun undersizedWidthIsRejected() {
        val result = DetectionFrameFilter.filter(
            listOf(
                chip(RectF(0f, 0f, 100f, 100f), 0.95f),
                chip(RectF(0f, 0f, 89f, 100f), 0.90f)
            ),
            0.70f
        )
        assertEquals(1, result.acceptedChipCount)
        assertEquals(1, result.rejectedUndersizedChipCount)
    }

    @Test
    fun largerBoxIsNotRejected() {
        val result = DetectionFrameFilter.filter(
            listOf(
                chip(RectF(0f, 0f, 100f, 100f), 0.95f),
                chip(RectF(0f, 0f, 120f, 120f), 0.90f)
            ),
            0.70f
        )
        assertEquals(2, result.acceptedChipCount)
    }

    @Test
    fun validatedEmptyKeepsAcceptedChipBoxesForTheOverlay() {
        val result = DetectionFrameFilter.filter(
            listOf(
                chip(RectF(0f, 0f, 100f, 100f), 0.95f),
                chip(RectF(120f, 0f, 220f, 100f), 0.90f),
                empty(RectF(300f, 0f, 350f, 50f), 0.92f)
            ),
            0.70f
        )

        assertEquals(2, result.acceptedChipCount)
        assertEquals(1, result.validatedEmptyDetections.size)
        assertEquals(3, result.detections.size)
    }

    @Test
    fun exactToleranceBoundaryIsAccepted() {
        val result = DetectionFrameFilter.filter(
            listOf(
                chip(RectF(0f, 0f, 100f, 100f), 0.95f),
                chip(RectF(0f, 0f, 90f, 90f), 0.90f)
            ),
            0.70f
        )
        assertEquals(2, result.acceptedChipCount)
    }
}
