package com.example.chipvision.data.model

import android.graphics.RectF

/**
 * Raw detection from the object detector.
 * classId: 0 = chip, 1 = empty
 */
data class Detection(
    val boundingBox: RectF, // Coordinates normalized (0f..1f) or pixel space
    val confidence: Float,
    val classId: Int
) {
    val isChip: Boolean get() = classId == 0
    val isEmptyImpression: Boolean get() = classId == 1
}

enum class ChipType(val displayName: String) {
    PCS_06_K("PCS.06.K"),
    PCS_26_C("PCS.26.C"),
    UNKNOWN("Unknown")
}

enum class ChipOrientation(val displayName: String, val isPass: Boolean) {
    CORRECT_UPWARD("Correct Upward", true),
    DOWNWARD("Downward", false),
    FLIPPED("Flipped", false),
    UNCERTAIN("Uncertain", false)
}

data class ChipClassification(
    val chipType: ChipType,
    val orientation: ChipOrientation,
    val classIndex: Int,
    val label: String,
    val confidence: Float,
    val isAccepted: Boolean
)

data class TrackedCandidate(
    val trackId: Long,
    val classId: Int,
    val boundingBox: RectF,
    val confidence: Float,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    val stabilityStartMs: Long?,
    val isStable: Boolean,
    val classification: ChipClassification? = null,
    val sessionId: String,
    val movementEvidenceFrames: Int = 0
) {
    val isChip: Boolean get() = classId == 0
    val isEmptyImpression: Boolean get() = classId == 1
}

enum class InspectionStatus(val displayName: String) {
    INITIALIZING("Initializing"),
    READY("Ready"),
    MONITORING("Monitoring live scene"),
    DETECTING("Detecting"),
    WAITING_FOR_STABILITY("Waiting for stability"),
    STABLE_SCENE_READY("Validating scene stability"),
    CHECKING_EMPTY_IMPRESSIONS("Checking empty impressions"),
    CLASSIFYING("Classifying chips"),
    FINALIZING("Finalizing"),
    PASS("PASS"),
    FAIL("FAIL"),
    ERROR("Inspection error"),
    STOPPED("Stopped")
}

enum class ReasonCode(val code: String, val isFail: Boolean) {
    PASS_ALL_CHIPS_CORRECT_UPWARD("PASS_ALL_CHIPS_CORRECT_UPWARD", false),
    FAIL_EMPTY_IMPRESSION_PRESENT("FAIL_EMPTY_IMPRESSION_PRESENT", true),
    FAIL_CHIP_DOWNWARD("FAIL_CHIP_DOWNWARD", true),
    FAIL_CHIP_FLIPPED("FAIL_CHIP_FLIPPED", true),
    FAIL_UNCERTAIN_CLASSIFICATION("FAIL_UNCERTAIN_CLASSIFICATION", true),
    FAIL_NO_VALID_CHIPS("FAIL_NO_VALID_CHIPS", true),
    FAIL_UNRESOLVED_CANDIDATE("FAIL_UNRESOLVED_CANDIDATE", true),
    FAIL_INFERENCE_ERROR("FAIL_INFERENCE_ERROR", true),
    FAIL_STABILITY_TIMEOUT("FAIL_STABILITY_TIMEOUT", true),
    FAIL_SESSION_INVALIDATED("FAIL_SESSION_INVALIDATED", true)
}

data class ChipSummary(
    val trackId: Long,
    val chipType: ChipType,
    val orientation: ChipOrientation,
    val confidence: Float,
    val isCorrectUpward: Boolean
)

data class InspectionDecision(
    val isPass: Boolean,
    val reasonCode: ReasonCode,
    val message: String,
    val chipCount: Int,
    val emptyCount: Int,
    val chips: List<ChipSummary> = emptyList(),
    val timestampMs: Long = System.currentTimeMillis(),
    val sessionId: String = "",
    val snapshotId: String = ""
)

data class CumulativeStats(
    val totalInspections: Int = 0,
    val passCount: Int = 0,
    val failCount: Int = 0,
    val errorCount: Int = 0,
    val totalChipsClassified: Int = 0,
    val upwardChipsCount: Int = 0,
    val downwardChipsCount: Int = 0,
    val flippedChipsCount: Int = 0,
    val emptyImpressionsCount: Int = 0
)

data class CurrentFrameStats(
    val detectedChips: Int = 0,
    val classifiedChips: Int = 0,
    val upwardChips: Int = 0,
    val downwardChips: Int = 0,
    val flippedChips: Int = 0,
    val emptyImpressions: Int = 0,
    val rawDetectorPredictions: Int = 0,
    val confidenceEligibleDetections: Int = 0,
    val acceptedDetections: Int = 0,
    val rejectedUndersizedDetections: Int = 0,
    val liveCandidates: Int = 0
)
