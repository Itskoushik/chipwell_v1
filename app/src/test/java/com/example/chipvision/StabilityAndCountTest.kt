package com.example.chipvision

import android.graphics.Bitmap
import android.graphics.RectF
import com.example.chipvision.data.model.CurrentFrameStats
import com.example.chipvision.data.model.Detection
import com.example.chipvision.data.preferences.InspectionSettings
import com.example.chipvision.tracking.DetectionTracker
import com.example.chipvision.tracking.StabilityValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StabilityAndCountTest {

    @Test
    fun candidateStabilityUsesThe200MsDefaultDuration() {
        val validator = StabilityValidator()
        val tracker = DetectionTracker()
        val settings = InspectionSettings()
        val candidates = tracker.updateTracks(
            detections = listOf(Detection(RectF(10f, 10f, 40f, 40f), 0.95f, 0)),
            currentTimeMs = 1_000L,
            settings = settings,
            sessionId = "test"
        )

        assertFalse(
            validator.validateCandidatesStability(candidates, 1_199L, settings).isAllStable
        )
        assertTrue(
            validator.validateCandidatesStability(candidates, 1_200L, settings).isAllStable
        )
    }

    @Test
    fun significantBoxMovementResetsTheStabilityStart() {
        val tracker = DetectionTracker()
        val settings = InspectionSettings()
        tracker.updateTracks(
            detections = listOf(Detection(RectF(10f, 10f, 40f, 40f), 0.95f, 0)),
            currentTimeMs = 1_000L,
            settings = settings,
            sessionId = "test"
        )

        val moved = tracker.updateTracks(
            detections = listOf(Detection(RectF(80f, 80f, 110f, 110f), 0.95f, 0)),
            currentTimeMs = 1_100L,
            settings = settings,
            sessionId = "test"
        )

        assertEquals(1_100L, moved.single { it.lastSeenMs == 1_100L }.stabilityStartMs)
        assertFalse(moved.single { it.lastSeenMs == 1_100L }.isStable)
    }

    @Test
    fun smallBoxJitterDoesNotResetTheStabilityStart() {
        val tracker = DetectionTracker()
        val settings = InspectionSettings(
            movementCenterToleranceRatio = 0.10f,
            movementIouTolerance = 0.70f,
            movementConfirmationFrames = 2
        )
        tracker.updateTracks(
            detections = listOf(Detection(RectF(10f, 10f, 40f, 40f), 0.95f, 0)),
            currentTimeMs = 1_000L,
            settings = settings,
            sessionId = "test"
        )

        val jitter = tracker.updateTracks(
            detections = listOf(Detection(RectF(11f, 10f, 41f, 40f), 0.95f, 0)),
            currentTimeMs = 1_100L,
            settings = settings,
            sessionId = "test"
        ).single { it.lastSeenMs == 1_100L }

        assertEquals(1_000L, jitter.stabilityStartMs)
        assertEquals(0, jitter.movementEvidenceFrames)
    }

    @Test
    fun oneNoisyMovementFrameDoesNotResetTheStabilityStart() {
        val tracker = DetectionTracker()
        val settings = InspectionSettings(movementConfirmationFrames = 2)
        tracker.updateTracks(
            detections = listOf(Detection(RectF(10f, 10f, 40f, 40f), 0.95f, 0)),
            currentTimeMs = 1_000L,
            settings = settings,
            sessionId = "test"
        )

        val noisy = tracker.updateTracks(
            detections = listOf(Detection(RectF(21f, 10f, 51f, 40f), 0.95f, 0)),
            currentTimeMs = 1_100L,
            settings = settings,
            sessionId = "test"
        ).single { it.lastSeenMs == 1_100L }

        assertEquals(1_000L, noisy.stabilityStartMs)
        assertEquals(1, noisy.movementEvidenceFrames)
    }

    @Test
    fun consecutiveMeaningfulMovementFramesResetTheStabilityStart() {
        val tracker = DetectionTracker()
        val settings = InspectionSettings(movementConfirmationFrames = 2)
        tracker.updateTracks(
            detections = listOf(Detection(RectF(10f, 10f, 40f, 40f), 0.95f, 0)),
            currentTimeMs = 1_000L,
            settings = settings,
            sessionId = "test"
        )
        tracker.updateTracks(
            detections = listOf(Detection(RectF(21f, 10f, 51f, 40f), 0.95f, 0)),
            currentTimeMs = 1_100L,
            settings = settings,
            sessionId = "test"
        )

        val moved = tracker.updateTracks(
            detections = listOf(Detection(RectF(32f, 10f, 62f, 40f), 0.95f, 0)),
            currentTimeMs = 1_200L,
            settings = settings,
            sessionId = "test"
        ).single { it.lastSeenMs == 1_200L }

        assertEquals(1_200L, moved.stabilityStartMs)
        assertEquals(2, moved.movementEvidenceFrames)
    }

    @Test
    fun motionSamplerTreatsTheFirstFrameAsStableAndResets() {
        val validator = StabilityValidator()
        val first = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        first.eraseColor(0xFF000000.toInt())

        assertFalse(validator.checkGlobalSceneMotion(first, 0.08f))
        validator.reset()
        assertFalse(validator.checkGlobalSceneMotion(first, 0.08f))

        first.recycle()
    }

    @Test
    fun frameStatsExposeAcceptedAndLiveCountMeaningsSeparately() {
        val stats = CurrentFrameStats(
            detectedChips = 6,
            rawDetectorPredictions = 37,
            confidenceEligibleDetections = 12,
            acceptedDetections = 6,
            rejectedUndersizedDetections = 6,
            liveCandidates = 6
        )

        assertEquals(37, stats.rawDetectorPredictions)
        assertEquals(6, stats.detectedChips)
        assertEquals(6, stats.acceptedDetections)
        assertEquals(6, stats.liveCandidates)
    }

    @Test
    fun aBriefMissKeepsTheTrackRenderableButExpiredTracksAreRemoved() {
        val tracker = DetectionTracker()
        val settings = InspectionSettings(trackingTimeoutMs = 400L)
        tracker.updateTracks(
            detections = listOf(Detection(RectF(10f, 10f, 40f, 40f), 0.95f, 0)),
            currentTimeMs = 1_000L,
            settings = settings,
            sessionId = "test"
        )

        tracker.updateTracks(
            detections = emptyList(),
            currentTimeMs = 1_200L,
            settings = settings,
            sessionId = "test"
        )
        assertEquals(1, tracker.renderableTracks(1_200L, settings.trackingTimeoutMs).size)

        tracker.updateTracks(
            detections = emptyList(),
            currentTimeMs = 1_401L,
            settings = settings,
            sessionId = "test"
        )
        assertEquals(0, tracker.renderableTracks(1_401L, settings.trackingTimeoutMs).size)
    }
}
