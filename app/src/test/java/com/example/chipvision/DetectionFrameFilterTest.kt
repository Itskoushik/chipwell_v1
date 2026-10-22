package com.example.chipvision

import android.graphics.RectF
import com.example.chipvision.data.model.Detection
import com.example.chipvision.engine.DetectionFrameFilter
import com.example.chipvision.data.preferences.InspectionRoi
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DetectionFrameFilterTest {
    @Test fun filtersByClassSpecificConfidenceThresholds() {
        val values = listOf(
            Detection(RectF(0f, 0f, 10f, 10f), .80f, 0),
            Detection(RectF(0f, 0f, 10f, 10f), .799f, 0),
            Detection(RectF(20f, 0f, 30f, 10f), .70f, 1),
            Detection(RectF(20f, 0f, 30f, 10f), .699f, 1)
        )
        val filtered = DetectionFrameFilter.filter(values, 40f, 20f, InspectionRoi.FULL_FRAME)
        assertEquals(listOf(.80f, .70f), filtered.detections.map { it.confidence })
    }
}
