package com.example.chipvision.data.model

import android.graphics.RectF

/**
 * Raw detection from the object detector.
 * classId: 0 = chip, 1 = marking
 */
data class Detection(
    val boundingBox: RectF, // Coordinates normalized (0f..1f) or pixel space
    val confidence: Float,
    val classId: Int
) {
    val isChip: Boolean get() = classId == 0
    val isMarking: Boolean get() = classId == 1
}

data class TrackedCandidate(
    val trackId: Long,
    val classId: Int,
    val boundingBox: RectF,
    val confidence: Float,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    val stabilityStartMs: Long?,
    val isStable: Boolean,
    val sessionId: String,
    val movementEvidenceFrames: Int = 0
) {
    val isChip: Boolean get() = classId == 0
    val isMarking: Boolean get() = classId == 1
}

enum class InspectionStatus(val displayName: String) {
    INITIALIZING("Initializing"),
    READY("Ready"),
    MONITORING("Monitoring live scene"),
    DETECTING("Detecting"),
    WAITING_FOR_STABILITY("Waiting for stability"),
    STABLE_SCENE_READY("Validating scene stability"),
    EVALUATING("Evaluating chips and markings"),
    FINALIZING("Finalizing"),
    PASS("PASS"),
    FAIL("FAIL"),
    ERROR("Inspection error"),
    STOPPED("Stopped")
}

enum class ReasonCode(val code: String, val isFail: Boolean) {
    PASS_SINGLE_MODEL("PASS_SINGLE_MODEL", false),
    FAIL_MARKING_RULE("FAIL_MARKING_RULE", true),
    FAIL_NO_VALID_CHIPS("FAIL_NO_VALID_CHIPS", true),
}

data class InspectionDecision(
    val isPass: Boolean,
    val reasonCode: ReasonCode,
    val message: String,
    val chipCount: Int,
    val emptyCount: Int,
    val timestampMs: Long = System.currentTimeMillis(),
    val sessionId: String = "",
    val snapshotId: String = "",
    val inspectionDetailsJson: String = ""
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
