package com.example.chipvision.inference

import android.graphics.RectF

enum class DetectorOutputLayout {
    CENTER_SINGLE_CLASS,
    CENTER_MULTI_CLASS
}

data class DecodedDetectorPrediction(
    val boundingBox: RectF,
    val confidence: Float,
    val classId: Int
)

object DetectorBoxDecoder {
    fun decode(
        raw: FloatArray,
        layout: DetectorOutputLayout,
        coordinatesAreNormalized: Boolean,
        inputSize: Int,
        padX: Float,
        padY: Float,
        scale: Float,
        originalWidth: Float,
        originalHeight: Float
    ): DecodedDetectorPrediction? {
        if (raw.size < 5 || raw.any { it.isNaN() || it.isInfinite() }) return null

        val confidence: Float
        val classId: Int
        if (layout == DetectorOutputLayout.CENTER_SINGLE_CLASS) {
            confidence = raw[4]
            classId = 0
        } else {
            if (raw.size < 6) return null
            classId = if (raw[4] >= raw[5]) 0 else 1
            confidence = maxOf(raw[4], raw[5])
        }
        if (confidence.isNaN() || confidence.isInfinite()) return null

        var centerX = raw[0]
        var centerY = raw[1]
        var width = raw[2]
        var height = raw[3]
        if (coordinatesAreNormalized) {
            centerX *= inputSize
            centerY *= inputSize
            width *= inputSize
            height *= inputSize
        }

        val letterboxBox = RectF(
            centerX - width / 2f,
            centerY - height / 2f,
            centerX + width / 2f,
            centerY + height / 2f
        )

        val left = ((letterboxBox.left - padX) / scale).coerceIn(0f, originalWidth)
        val top = ((letterboxBox.top - padY) / scale).coerceIn(0f, originalHeight)
        val right = ((letterboxBox.right - padX) / scale).coerceIn(0f, originalWidth)
        val bottom = ((letterboxBox.bottom - padY) / scale).coerceIn(0f, originalHeight)
        if (right <= left || bottom <= top) return null

        return DecodedDetectorPrediction(
            boundingBox = RectF(left, top, right, bottom),
            confidence = confidence,
            classId = classId
        )
    }
}
