package com.example.chipvision.engine

import android.graphics.RectF
import com.example.chipvision.data.preferences.InspectionRoi

object DetectionBoxValidator {
    fun isValidChipBox(
        box: RectF,
        frameWidth: Float,
        frameHeight: Float,
        roi: InspectionRoi
    ): Boolean {
        if (!roi.isValid || !frameWidth.isFinite() || !frameHeight.isFinite() ||
            frameWidth <= 0f || frameHeight <= 0f
        ) return false

        val coordinates = floatArrayOf(box.left, box.top, box.right, box.bottom)
        if (coordinates.any { !it.isFinite() }) return false

        val width = box.right - box.left
        val height = box.bottom - box.top
        if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) return false

        val insideFrame = box.left >= 0f && box.top >= 0f &&
            box.right <= frameWidth && box.bottom <= frameHeight
        if (!insideFrame) return false

        val roiLeft = roi.left * frameWidth
        val roiTop = roi.top * frameHeight
        val roiRight = roi.right * frameWidth
        val roiBottom = roi.bottom * frameHeight
        return box.left >= roiLeft && box.top >= roiTop &&
            box.right <= roiRight && box.bottom <= roiBottom
    }
}
