package com.example.chipvision.engine

import com.example.chipvision.data.model.ChipOrientation
import com.example.chipvision.data.model.ChipSummary
import com.example.chipvision.data.model.ChipType
import com.example.chipvision.data.model.InspectionDecision
import com.example.chipvision.data.model.ReasonCode
import com.example.chipvision.data.model.TrackedCandidate

object InspectionDecisionEngine {

    /**
     * Evaluates a stable scene and produces the final immutable InspectionDecision.
     * Enforces the Empty-Impression-First rule, all PASS conditions, and explicit FAIL reason codes.
     */
    fun evaluate(
        candidates: List<TrackedCandidate>,
        sessionId: String,
        snapshotId: String,
        inferenceError: String? = null
    ): InspectionDecision {
        val now = System.currentTimeMillis()

        // 1. Technical / Inference failure check
        if (!inferenceError.isNullOrBlank()) {
            return InspectionDecision(
                isPass = false,
                reasonCode = ReasonCode.FAIL_INFERENCE_ERROR,
                message = "Inference failure: $inferenceError",
                chipCount = 0,
                emptyCount = 0,
                timestampMs = now,
                sessionId = sessionId,
                snapshotId = snapshotId
            )
        }

        // 2. Identify confirmed stable empty impressions
        val emptyImpressions = candidates.filter { it.isEmptyImpression && it.isStable }
        val emptyCount = emptyImpressions.size

        // RULE: Confirmed empty impression present = IMMEDIATE FAIL
        if (emptyCount > 0) {
            return InspectionDecision(
                isPass = false,
                reasonCode = ReasonCode.FAIL_EMPTY_IMPRESSION_PRESENT,
                message = "FAIL: $emptyCount empty impression(s) detected in scene",
                chipCount = candidates.count { it.isChip },
                emptyCount = emptyCount,
                chips = emptyList(), // Do not classify or report chip pass/fail when empty impressions exist
                timestampMs = now,
                sessionId = sessionId,
                snapshotId = snapshotId
            )
        }

        // 3. Evaluate chips
        val chipCandidates = candidates.filter { it.isChip && it.isStable }
        val chipCount = chipCandidates.size

        if (chipCount == 0) {
            return InspectionDecision(
                isPass = false,
                reasonCode = ReasonCode.FAIL_NO_VALID_CHIPS,
                message = "FAIL: No valid chips detected on conveyor",
                chipCount = 0,
                emptyCount = 0,
                timestampMs = now,
                sessionId = sessionId,
                snapshotId = snapshotId
            )
        }

        // 4. Check that all chip candidates have completed classification
        val unclassified = chipCandidates.filter { it.classification == null }
        if (unclassified.isNotEmpty()) {
            return InspectionDecision(
                isPass = false,
                reasonCode = ReasonCode.FAIL_UNRESOLVED_CANDIDATE,
                message = "FAIL: ${unclassified.size} chip candidate(s) awaiting classification",
                chipCount = chipCount,
                emptyCount = 0,
                timestampMs = now,
                sessionId = sessionId,
                snapshotId = snapshotId
            )
        }

        // 5. Evaluate individual chip classifications
        val chipSummaries = mutableListOf<ChipSummary>()
        var downwardFound = false
        var flippedFound = false
        var uncertainFound = false

        for (candidate in chipCandidates) {
            val cls = candidate.classification!!
            val isKnownType = cls.chipType == ChipType.PCS_06_K || cls.chipType == ChipType.PCS_26_C
            val isAccepted = cls.isAccepted && isKnownType

            val summary = ChipSummary(
                trackId = candidate.trackId,
                chipType = cls.chipType,
                orientation = cls.orientation,
                confidence = cls.confidence,
                isCorrectUpward = isAccepted && cls.orientation == ChipOrientation.CORRECT_UPWARD
            )
            chipSummaries.add(summary)

            if (!isAccepted || cls.orientation == ChipOrientation.UNCERTAIN) {
                uncertainFound = true
            } else if (cls.orientation == ChipOrientation.DOWNWARD) {
                downwardFound = true
            } else if (cls.orientation == ChipOrientation.FLIPPED) {
                flippedFound = true
            }
        }

        // Strict priority order for failure reasons
        if (downwardFound) {
            return InspectionDecision(
                isPass = false,
                reasonCode = ReasonCode.FAIL_CHIP_DOWNWARD,
                message = "FAIL: Downward chip orientation detected",
                chipCount = chipCount,
                emptyCount = 0,
                chips = chipSummaries,
                timestampMs = now,
                sessionId = sessionId,
                snapshotId = snapshotId
            )
        }

        if (flippedFound) {
            return InspectionDecision(
                isPass = false,
                reasonCode = ReasonCode.FAIL_CHIP_FLIPPED,
                message = "FAIL: Flipped chip orientation detected",
                chipCount = chipCount,
                emptyCount = 0,
                chips = chipSummaries,
                timestampMs = now,
                sessionId = sessionId,
                snapshotId = snapshotId
            )
        }

        if (uncertainFound) {
            return InspectionDecision(
                isPass = false,
                reasonCode = ReasonCode.FAIL_UNCERTAIN_CLASSIFICATION,
                message = "FAIL: Low confidence or uncertain chip classification",
                chipCount = chipCount,
                emptyCount = 0,
                chips = chipSummaries,
                timestampMs = now,
                sessionId = sessionId,
                snapshotId = snapshotId
            )
        }

        // If we reached here:
        // - At least one valid chip exists
        // - No empty impressions
        // - Every chip is PCS.06.K or PCS.26.C
        // - Every chip is CORRECT_UPWARD
        // - Confidences >= threshold
        return InspectionDecision(
            isPass = true,
            reasonCode = ReasonCode.PASS_ALL_CHIPS_CORRECT_UPWARD,
            message = "PASS: All $chipCount chip(s) correctly upward (${chipSummaries.joinToString { it.chipType.displayName }})",
            chipCount = chipCount,
            emptyCount = 0,
            chips = chipSummaries,
            timestampMs = now,
            sessionId = sessionId,
            snapshotId = snapshotId
        )
    }
}
