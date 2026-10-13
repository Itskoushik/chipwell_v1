package com.example.chipvision

import com.example.chipvision.data.model.InspectionDecision
import com.example.chipvision.data.model.ReasonCode
import com.example.chipvision.engine.InspectionResultDelay
import org.junit.Assert.assertEquals
import org.junit.Test

class InspectionResultDelayTest {
    private fun decision(isPass: Boolean) = InspectionDecision(
        isPass = isPass,
        reasonCode = if (isPass) {
            ReasonCode.PASS_ALL_CHIPS_CORRECT_UPWARD
        } else {
            ReasonCode.FAIL_NO_VALID_CHIPS
        },
        message = if (isPass) "PASS" else "FAIL",
        chipCount = 0,
        emptyCount = 0,
        timestampMs = 0L,
        sessionId = "test",
        snapshotId = "snapshot"
    )

    @Test
    fun passUsesOneSecondHold() {
        assertEquals(1_000L, InspectionResultDelay.forDecision(decision(true)))
    }

    @Test
    fun failUsesFiveSecondHold() {
        assertEquals(5_000L, InspectionResultDelay.forDecision(decision(false)))
    }

    @Test
    fun failureBeepIsDueOnlyBeforeTheFiveSecondDeadline() {
        assertEquals(true, InspectionResultDelay.failureBeepIsDue(true, 1_000L, 5_999L))
        assertEquals(false, InspectionResultDelay.failureBeepIsDue(true, 1_000L, 6_000L))
    }

    @Test
    fun passDoesNotUseTheFailureBeepDeadline() {
        assertEquals(1_000L, InspectionResultDelay.forDecision(decision(true)))
        assertEquals(false, InspectionResultDelay.failureBeepIsDue(false, 1_000L, 3_001L))
    }
}
