package com.example.chipvision.engine

import com.example.chipvision.data.model.InspectionDecision
import com.example.chipvision.data.model.ReasonCode

/** Adapts the single-model post-processing result to the app's existing result/history contract. */
object InspectionDecisionEngine {
    fun evaluate(
        result: InspectionPostProcessor.Result,
        sessionId: String,
        snapshotId: String
    ): InspectionDecision {
        val pass = result.finalPass
        return InspectionDecision(
            isPass = pass,
            reasonCode = when {
                pass -> ReasonCode.PASS_SINGLE_MODEL
                result.chips.isEmpty() -> ReasonCode.FAIL_NO_VALID_CHIPS
                else -> ReasonCode.FAIL_MARKING_RULE
            },
            message = if (pass) "PASS" else "FAIL",
            chipCount = result.chips.size,
            emptyCount = 0,
            sessionId = sessionId,
            snapshotId = snapshotId,
            inspectionDetailsJson = result.chips.mapIndexed { index, chip ->
                "{\"chip\":${index + 1},\"confidence\":${chip.chip.confidence}," +
                    "\"retainedMarkings\":${chip.markings.size},\"pass\":${chip.passes}}"
            }.joinToString(prefix = "[", postfix = "]")
        )
    }
}
