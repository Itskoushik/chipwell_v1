package com.example.chipvision

import android.graphics.RectF
import com.example.chipvision.data.model.Detection
import com.example.chipvision.inference.DetectionNms
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DetectionNmsTest {
    @Test fun suppressesOverlappingBoxesWithinClassButKeepsDifferentClass() {
        val result = DetectionNms.apply(listOf(
            Detection(RectF(0f,0f,100f,100f),.95f,0),
            Detection(RectF(1f,1f,99f,99f),.90f,0),
            Detection(RectF(0f,0f,100f,100f),.85f,1)
        ))
        assertEquals(2, result.size)
        assertEquals(listOf(0,1), result.map { it.classId })
    }
}
