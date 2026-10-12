package com.example.chipvision

import android.graphics.RectF
import com.example.chipvision.data.model.ChipClassification
import com.example.chipvision.data.model.ChipOrientation
import com.example.chipvision.data.model.ChipType
import com.example.chipvision.data.model.Detection
import com.example.chipvision.data.model.ReasonCode
import com.example.chipvision.data.model.TrackedCandidate
import com.example.chipvision.data.preferences.InspectionSettings
import com.example.chipvision.engine.InspectionDecisionEngine
import com.example.chipvision.tracking.DetectionTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InspectionDecisionEngineTest {

    private fun createChipCandidate(
        trackId: Long,
        type: ChipType,
        orientation: ChipOrientation,
        confidence: Float = 0.95f,
        isAccepted: Boolean = true,
        isStable: Boolean = true
    ): TrackedCandidate {
        return TrackedCandidate(
            trackId = trackId,
            classId = 0, // chip
            boundingBox = RectF(10f, 10f, 100f, 100f),
            confidence = 0.92f,
            firstSeenMs = 0L,
            lastSeenMs = 1500L,
            stabilityStartMs = 0L,
            isStable = isStable,
            classification = ChipClassification(
                chipType = type,
                orientation = orientation,
                classIndex = 0,
                label = "${type.name}_${orientation.name}",
                confidence = confidence,
                isAccepted = isAccepted
            ),
            sessionId = "test-session"
        )
    }

    private fun createEmptyImpressionCandidate(
        trackId: Long,
        isStable: Boolean = true
    ): TrackedCandidate {
        return TrackedCandidate(
            trackId = trackId,
            classId = 1, // empty
            boundingBox = RectF(200f, 10f, 290f, 100f),
            confidence = 0.91f,
            firstSeenMs = 0L,
            lastSeenMs = 1500L,
            stabilityStartMs = 0L,
            isStable = isStable,
            classification = null, // Empty impressions are NEVER sent to classifier
            sessionId = "test-session"
        )
    }

    // 1. Three correctly upward chips -> PASS
    @Test
    fun testThreeCorrectUpwardChips_returnsPass() {
        val candidates = listOf(
            createChipCandidate(1, ChipType.PCS_06_K, ChipOrientation.CORRECT_UPWARD),
            createChipCandidate(2, ChipType.PCS_06_K, ChipOrientation.CORRECT_UPWARD),
            createChipCandidate(3, ChipType.PCS_26_C, ChipOrientation.CORRECT_UPWARD)
        )

        val decision = InspectionDecisionEngine.evaluate(candidates, "sess1", "snap1")
        assertTrue("Expected PASS for 3 upward chips", decision.isPass)
        assertEquals(ReasonCode.PASS_ALL_CHIPS_CORRECT_UPWARD, decision.reasonCode)
        assertEquals(3, decision.chipCount)
        assertEquals(0, decision.emptyCount)
    }

    // 2. Multiple PCS.06.K upward chips -> PASS
    @Test
    fun testMultiplePCS06KUpwardChips_returnsPass() {
        val candidates = listOf(
            createChipCandidate(1, ChipType.PCS_06_K, ChipOrientation.CORRECT_UPWARD),
            createChipCandidate(2, ChipType.PCS_06_K, ChipOrientation.CORRECT_UPWARD)
        )

        val decision = InspectionDecisionEngine.evaluate(candidates, "sess1", "snap1")
        assertTrue(decision.isPass)
        assertEquals(ReasonCode.PASS_ALL_CHIPS_CORRECT_UPWARD, decision.reasonCode)
    }

    // 3. Multiple PCS.26.C upward chips -> PASS
    @Test
    fun testMultiplePCS26CUpwardChips_returnsPass() {
        val candidates = listOf(
            createChipCandidate(1, ChipType.PCS_26_C, ChipOrientation.CORRECT_UPWARD),
            createChipCandidate(2, ChipType.PCS_26_C, ChipOrientation.CORRECT_UPWARD)
        )

        val decision = InspectionDecisionEngine.evaluate(candidates, "sess1", "snap1")
        assertTrue(decision.isPass)
        assertEquals(ReasonCode.PASS_ALL_CHIPS_CORRECT_UPWARD, decision.reasonCode)
    }

    // 4. Mixed chip types, all correctly upward -> PASS
    @Test
    fun testMixedChipTypesCorrectUpward_returnsPass() {
        val candidates = listOf(
            createChipCandidate(1, ChipType.PCS_06_K, ChipOrientation.CORRECT_UPWARD),
            createChipCandidate(2, ChipType.PCS_26_C, ChipOrientation.CORRECT_UPWARD)
        )

        val decision = InspectionDecisionEngine.evaluate(candidates, "sess1", "snap1")
        assertTrue(decision.isPass)
        assertEquals(ReasonCode.PASS_ALL_CHIPS_CORRECT_UPWARD, decision.reasonCode)
    }

    // 5. One confirmed empty impression -> FAIL
    @Test
    fun testOneConfirmedEmptyImpression_returnsFail() {
        val candidates = listOf(
            createChipCandidate(1, ChipType.PCS_06_K, ChipOrientation.CORRECT_UPWARD),
            createEmptyImpressionCandidate(2, isStable = true)
        )

        val decision = InspectionDecisionEngine.evaluate(candidates, "sess1", "snap1")
        assertFalse("Confirmed empty impression must FAIL", decision.isPass)
        assertEquals(ReasonCode.FAIL_EMPTY_IMPRESSION_PRESENT, decision.reasonCode)
        assertEquals(1, decision.emptyCount)
    }

    // 6. Multiple confirmed empty impressions -> FAIL
    @Test
    fun testMultipleConfirmedEmptyImpressions_returnsFail() {
        val candidates = listOf(
            createEmptyImpressionCandidate(1, isStable = true),
            createEmptyImpressionCandidate(2, isStable = true)
        )

        val decision = InspectionDecisionEngine.evaluate(candidates, "sess1", "snap1")
        assertFalse(decision.isPass)
        assertEquals(ReasonCode.FAIL_EMPTY_IMPRESSION_PRESENT, decision.reasonCode)
        assertEquals(2, decision.emptyCount)
    }

    // 7. One downward chip -> FAIL
    @Test
    fun testOneDownwardChip_returnsFail() {
        val candidates = listOf(
            createChipCandidate(1, ChipType.PCS_06_K, ChipOrientation.CORRECT_UPWARD),
            createChipCandidate(2, ChipType.PCS_06_K, ChipOrientation.DOWNWARD)
        )

        val decision = InspectionDecisionEngine.evaluate(candidates, "sess1", "snap1")
        assertFalse(decision.isPass)
        assertEquals(ReasonCode.FAIL_CHIP_DOWNWARD, decision.reasonCode)
    }

    // 8. One flipped chip -> FAIL
    @Test
    fun testOneFlippedChip_returnsFail() {
        val candidates = listOf(
            createChipCandidate(1, ChipType.PCS_26_C, ChipOrientation.CORRECT_UPWARD),
            createChipCandidate(2, ChipType.PCS_26_C, ChipOrientation.FLIPPED)
        )

        val decision = InspectionDecisionEngine.evaluate(candidates, "sess1", "snap1")
        assertFalse(decision.isPass)
        assertEquals(ReasonCode.FAIL_CHIP_FLIPPED, decision.reasonCode)
    }

    // 9. Detector threshold strict rule: confidence exactly 0.85 must be rejected
    @Test
    fun testDetectorConfidenceExactly85_isRejected() {
        val threshold = 0.85f
        val detAt85 = Detection(RectF(0f, 0f, 10f, 10f), 0.85f, 0)
        val detAbove85 = Detection(RectF(0f, 0f, 10f, 10f), 0.85001f, 0)

        assertFalse("At exactly 0.85 must be rejected", detAt85.confidence > threshold)
        assertTrue("Above 0.85 must be accepted", detAbove85.confidence > threshold)
    }

    // 10. Low-confidence or uncertain classification -> FAIL_UNCERTAIN_CLASSIFICATION
    @Test
    fun testUncertainClassification_returnsFail() {
        val candidates = listOf(
            createChipCandidate(
                trackId = 1,
                type = ChipType.PCS_06_K,
                orientation = ChipOrientation.UNCERTAIN,
                confidence = 0.55f, // Below 0.70 threshold
                isAccepted = false
            )
        )

        val decision = InspectionDecisionEngine.evaluate(candidates, "sess1", "snap1")
        assertFalse(decision.isPass)
        assertEquals(ReasonCode.FAIL_UNCERTAIN_CLASSIFICATION, decision.reasonCode)
    }

    // 11. No valid chips -> FAIL_NO_VALID_CHIPS
    @Test
    fun testNoValidChips_returnsFail() {
        val candidates = emptyList<TrackedCandidate>()
        val decision = InspectionDecisionEngine.evaluate(candidates, "sess1", "snap1")
        assertFalse(decision.isPass)
        assertEquals(ReasonCode.FAIL_NO_VALID_CHIPS, decision.reasonCode)
    }

    // 12. Unresolved candidate awaiting classification -> FAIL_UNRESOLVED_CANDIDATE
    @Test
    fun testUnresolvedCandidate_returnsFail() {
        val unclassifiedChip = TrackedCandidate(
            trackId = 1,
            classId = 0,
            boundingBox = RectF(10f, 10f, 100f, 100f),
            confidence = 0.95f,
            firstSeenMs = 0L,
            lastSeenMs = 1500L,
            stabilityStartMs = 0L,
            isStable = true,
            classification = null, // unclassified
            sessionId = "sess1"
        )

        val decision = InspectionDecisionEngine.evaluate(listOf(unclassifiedChip), "sess1", "snap1")
        assertFalse(decision.isPass)
        assertEquals(ReasonCode.FAIL_UNRESOLVED_CANDIDATE, decision.reasonCode)
    }

    // 13. Technical inference error -> FAIL_INFERENCE_ERROR
    @Test
    fun testInferenceError_returnsFailInferenceError() {
        val candidates = listOf(
            createChipCandidate(1, ChipType.PCS_06_K, ChipOrientation.CORRECT_UPWARD)
        )

        val decision = InspectionDecisionEngine.evaluate(
            candidates = candidates,
            sessionId = "sess1",
            snapshotId = "snap1",
            inferenceError = "Model interpreter crash"
        )
        assertFalse(decision.isPass)
        assertEquals(ReasonCode.FAIL_INFERENCE_ERROR, decision.reasonCode)
    }

    // 14. IoU calculation test
    @Test
    fun testIoUCalculation() {
        val boxA = RectF(0f, 0f, 10f, 10f) // area 100
        val boxB = RectF(0f, 0f, 10f, 10f) // identical
        val iouIdentical = DetectionTracker.calculateIoU(boxA, boxB)
        assertEquals(1.0f, iouIdentical, 0.001f)

        val boxC = RectF(20f, 20f, 30f, 30f) // disjoint
        val iouDisjoint = DetectionTracker.calculateIoU(boxA, boxC)
        assertEquals(0.0f, iouDisjoint, 0.001f)

        val boxD = RectF(5f, 0f, 15f, 10f) // 50% overlap: intersection 50, union 150 -> 0.333
        val iouPartial = DetectionTracker.calculateIoU(boxA, boxD)
        assertEquals(50f / 150f, iouPartial, 0.001f)
    }

    // 15. Tracking separates chip and empty candidates
    @Test
    fun testTrackerSeparatesChipAndEmpty() {
        val tracker = DetectionTracker()
        val settings = InspectionSettings()

        val det1 = Detection(RectF(10f, 10f, 100f, 100f), 0.95f, classId = 0) // chip
        val tracks1 = tracker.updateTracks(listOf(det1), 1000L, settings, "s1")
        assertEquals(1, tracks1.size)
        val chipTrackId = tracks1[0].trackId
        assertEquals(0, tracks1[0].classId)

        // At next frame, at the same location, an empty detection appears
        // Tracker must NOT match chip track with empty class!
        val det2 = Detection(RectF(10f, 10f, 100f, 100f), 0.95f, classId = 1) // empty
        val tracks2 = tracker.updateTracks(listOf(det2), 1100L, settings, "s1")
        val emptyTrack = tracks2.find { it.classId == 1 }
        assertTrue(emptyTrack != null)
        assertTrue("Empty detection must receive different track id from chip", emptyTrack!!.trackId != chipTrackId)
    }
}
