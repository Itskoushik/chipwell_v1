package com.example.chipvision.engine

import com.example.chipvision.data.model.Detection
import com.example.chipvision.data.preferences.InspectionRoi

data class FilteredDetections(
    val detections: List<Detection>,
    val initialChipCount: Int,
    val eligibleChipCount: Int
)

/** Applies the fixed class confidence thresholds and optional chip inspection region. */
object DetectionFrameFilter {
    fun filter(
        detections: List<Detection>,
        frameWidth: Float,
        frameHeight: Float,
        inspectionRoi: InspectionRoi
    ): FilteredDetections {
        val eligible = detections.filter { detection ->
            val box = detection.boundingBox
            val valid = box.left.isFinite() && box.top.isFinite() && box.right.isFinite() && box.bottom.isFinite() &&
                box.width() > 0f && box.height() > 0f
            valid && when (detection.classId) {
                InspectionPostProcessor.CHIP_CLASS_ID -> detection.confidence >= InspectionPostProcessor.CHIP_CONFIDENCE &&
                    DetectionBoxValidator.isValidChipBox(box, frameWidth, frameHeight, inspectionRoi)
                InspectionPostProcessor.MARKING_CLASS_ID -> detection.confidence >= InspectionPostProcessor.MARKING_CONFIDENCE
                else -> false
            }
        }
        return FilteredDetections(
            detections = eligible,
            initialChipCount = detections.count { it.classId == InspectionPostProcessor.CHIP_CLASS_ID },
            eligibleChipCount = eligible.count { it.classId == InspectionPostProcessor.CHIP_CLASS_ID }
        )
    }
}
