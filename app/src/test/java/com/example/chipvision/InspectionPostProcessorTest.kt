package com.example.chipvision

import android.graphics.RectF
import com.example.chipvision.data.model.Detection
import com.example.chipvision.engine.InspectionPostProcessor as Pipeline
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InspectionPostProcessorTest {
    private fun chip(box: RectF, conf: Float = .9f) = Detection(box, conf, 0)
    private fun mark(box: RectF, conf: Float = .9f) = Detection(box, conf, 1)
    private val chipBox get() = RectF(0f, 0f, 100f, 100f)
    private fun top(size: Float = 10f, conf: Float = .9f) = mark(RectF(50f-size/2, 2f, 50f+size/2, 2f+size), conf)

    @Test fun classMappingAndExactConfidenceThresholds() {
        val result = Pipeline.process(listOf(chip(chipBox, .80f), chip(RectF(200f,0f,300f,100f), .799f), top(conf=.70f), mark(RectF(20f,20f,30f,30f), .699f)))
        assertEquals(1, result.chips.size)
        assertEquals(1, result.chips.single().markings.size)
        assertEquals(0, Pipeline.process(listOf(mark(RectF(0f,0f,1f,1f)))).chips.size)
    }

    @Test fun sizeFilterUsesHighestConfidenceAndRotationIndependentDimensions() {
        val ref = chip(RectF(0f,0f,100f,50f), .95f)
        val rotatedWithinTolerance = chip(RectF(0f,0f,60f,110f), .90f)
        val outside = chip(RectF(0f,0f,130f,65f), .89f)
        val kept = Pipeline.sizeFilter(listOf(outside, rotatedWithinTolerance, ref))
        assertEquals(2, kept.size)
        assertTrue(kept.contains(ref))
        assertTrue(kept.contains(rotatedWithinTolerance))
    }

    @Test fun markingAssociationUsesMarkingAreaDenominatorAtHalfOverlap() {
        val chip = chip(chipBox)
        val halfOutside = mark(RectF(-50f, 40f, 50f, 60f))
        val result = Pipeline.process(listOf(chip, halfOutside))
        assertEquals(1, result.chips.single().markings.size)
    }

    @Test fun markingSizeReferencesAreIndependentForEachChip() {
        val c1 = chip(RectF(0f,0f,100f,100f), .95f)
        val c2 = chip(RectF(150f,0f,250f,100f), .94f)
        val m1 = mark(RectF(45f,3f,55f,13f), .95f)
        val m1Rejected = mark(RectF(40f,3f,60f,23f), .80f)
        val m2 = mark(RectF(195f,3f,215f,23f), .96f)
        val m2Kept = mark(RectF(193f,3f,217f,27f), .80f)
        val result = Pipeline.process(listOf(c1,c2,m1,m1Rejected,m2,m2Kept))
        assertEquals(listOf(1,2), result.chips.map { it.markings.size })
    }

    @Test fun nearestSidesTopRightPassLeftBottomFail() {
        val placements = listOf(
            RectF(45f, 2f, 55f, 12f) to true,
            RectF(88f, 45f, 98f, 55f) to true,
            RectF(2f, 45f, 12f, 55f) to false,
            RectF(45f, 88f, 55f, 98f) to false
        )
        for ((box, pass) in placements) {
            val result = Pipeline.process(listOf(chip(chipBox), mark(box)))
            assertEquals(pass, result.chips.single().markings.single().passes)
        }
    }

    @Test fun noMarkingOrNoChipsFails() {
        assertFalse(Pipeline.process(listOf(chip(chipBox))).finalPass)
        assertFalse(Pipeline.process(emptyList()).finalPass)
    }

    @Test fun allAcceptedChipsMustPassAndRejectedChipDoesNotForceFailure() {
        val c1 = chip(chipBox, .95f)
        val c2 = chip(RectF(120f,0f,220f,100f), .90f)
        val rejected = chip(RectF(400f,0f,550f,150f), .85f)
        val m1 = mark(RectF(45f,2f,55f,12f))
        val m2 = mark(RectF(165f,2f,175f,12f))
        val allPass = Pipeline.process(listOf(c1,c2,rejected,m1,m2))
        assertEquals(2, allPass.chips.size)
        assertEquals(4, allPass.acceptedDetections.size)
        assertTrue(allPass.finalPass)
        assertFalse(Pipeline.process(listOf(c1,c2,m1,mark(RectF(122f,45f,132f,55f)))).finalPass)
    }

    @Test fun ignoredAndSizeRejectedMarkingsAreNotRendered() {
        val result = Pipeline.process(listOf(chip(chipBox), top(), mark(RectF(120f,120f,130f,130f)), mark(RectF(45f,3f,65f,23f), .8f)))
        assertEquals(2, result.acceptedDetections.size)
        assertFalse(result.acceptedDetections.any { it.boundingBox.left >= 120f })
    }
}
