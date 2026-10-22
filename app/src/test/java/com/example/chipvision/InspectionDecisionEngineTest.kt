package com.example.chipvision

import android.graphics.RectF
import com.example.chipvision.data.model.Detection
import com.example.chipvision.data.model.ReasonCode
import com.example.chipvision.engine.InspectionDecisionEngine
import com.example.chipvision.engine.InspectionPostProcessor
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InspectionDecisionEngineTest {
    @Test fun pipelinePassMapsToMinimalPassDecision() {
        val result = InspectionPostProcessor.process(listOf(
            Detection(RectF(0f,0f,100f,100f),.9f,0),
            Detection(RectF(45f,2f,55f,12f),.9f,1)
        ))
        val decision = InspectionDecisionEngine.evaluate(result, "session", "snapshot")
        assertTrue(decision.isPass)
        assertEquals(ReasonCode.PASS_SINGLE_MODEL, decision.reasonCode)
        assertEquals("PASS", decision.message)
    }

    @Test fun noChipsAndNoRetainedMarkingsAreProductFails() {
        val noChips = InspectionDecisionEngine.evaluate(InspectionPostProcessor.process(emptyList()), "s", "a")
        assertFalse(noChips.isPass)
        assertEquals(ReasonCode.FAIL_NO_VALID_CHIPS, noChips.reasonCode)
        val noMarks = InspectionDecisionEngine.evaluate(
            InspectionPostProcessor.process(listOf(Detection(RectF(0f,0f,100f,100f),.9f,0))), "s", "b"
        )
        assertFalse(noMarks.isPass)
        assertEquals(ReasonCode.FAIL_MARKING_RULE, noMarks.reasonCode)
    }
}
