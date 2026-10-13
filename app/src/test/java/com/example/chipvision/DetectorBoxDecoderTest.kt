package com.example.chipvision

import com.example.chipvision.inference.DetectorBoxDecoder
import com.example.chipvision.inference.DetectorOutputLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DetectorBoxDecoderTest {
    @Test
    fun decodesNormalizedCenterCoordinatesAndReversesLetterbox() {
        val result = DetectorBoxDecoder.decode(
            raw = floatArrayOf(0.5f, 0.5f, 0.25f, 0.5f, 0.9f),
            layout = DetectorOutputLayout.CENTER_SINGLE_CLASS,
            coordinatesAreNormalized = true,
            inputSize = 512,
            padX = 0f,
            padY = 64f,
            scale = 0.5f,
            originalWidth = 1024f,
            originalHeight = 768f
        )

        assertNotNull(result)
        assertEquals(384f, result!!.boundingBox.left, 0.01f)
        assertEquals(128f, result.boundingBox.top, 0.01f)
        assertEquals(640f, result.boundingBox.right, 0.01f)
        assertEquals(640f, result.boundingBox.bottom, 0.01f)
        assertEquals(0, result.classId)
    }

    @Test
    fun decodesPixelCenterCoordinatesAndSelectsHighestClassScore() {
        val result = DetectorBoxDecoder.decode(
            raw = floatArrayOf(256f, 256f, 100f, 80f, 0.2f, 0.8f),
            layout = DetectorOutputLayout.CENTER_MULTI_CLASS,
            coordinatesAreNormalized = false,
            inputSize = 512,
            padX = 0f,
            padY = 0f,
            scale = 1f,
            originalWidth = 512f,
            originalHeight = 512f
        )

        assertNotNull(result)
        assertEquals(206f, result!!.boundingBox.left, 0.01f)
        assertEquals(216f, result.boundingBox.top, 0.01f)
        assertEquals(306f, result.boundingBox.right, 0.01f)
        assertEquals(296f, result.boundingBox.bottom, 0.01f)
        assertEquals(1, result.classId)
        assertEquals(0.8f, result.confidence, 0.01f)
    }

    @Test
    fun rejectsZeroAreaDecodedBoxes() {
        val result = DetectorBoxDecoder.decode(
            raw = floatArrayOf(0.5f, 0.5f, 0f, 0.2f, 0.9f),
            layout = DetectorOutputLayout.CENTER_SINGLE_CLASS,
            coordinatesAreNormalized = true,
            inputSize = 512,
            padX = 0f,
            padY = 0f,
            scale = 1f,
            originalWidth = 512f,
            originalHeight = 512f
        )

        assertEquals(null, result)
    }
}
