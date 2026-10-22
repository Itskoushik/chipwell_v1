package com.example.chipvision.inference

import com.example.chipvision.data.model.Detection
import kotlin.math.max
import kotlin.math.min

/** Standard class-aware greedy NMS for the model's dense, pre-NMS 5,376-candidate output. */
object DetectionNms {
    const val IOU_THRESHOLD = 0.70f
    const val MAX_DETECTIONS = 300

    fun apply(detections: List<Detection>, iouThreshold: Float = IOU_THRESHOLD): List<Detection> {
        val selected = mutableListOf<Detection>()
        for (classId in 0..1) {
            val candidates = detections.withIndex()
                .filter { it.value.classId == classId }
                .sortedWith(compareByDescending<IndexedValue<Detection>> { it.value.confidence }.thenBy { it.index })
                .map { it.value }
                .toMutableList()
            while (candidates.isNotEmpty()) {
                val best = candidates.removeAt(0)
                selected += best
                candidates.removeAll { iou(best, it) > iouThreshold }
            }
        }
        return selected.sortedByDescending { it.confidence }.take(MAX_DETECTIONS)
    }

    private fun iou(a: Detection, b: Detection): Float {
        val left = max(a.boundingBox.left, b.boundingBox.left)
        val top = max(a.boundingBox.top, b.boundingBox.top)
        val right = min(a.boundingBox.right, b.boundingBox.right)
        val bottom = min(a.boundingBox.bottom, b.boundingBox.bottom)
        val intersection = max(0f, right - left) * max(0f, bottom - top)
        val union = a.boundingBox.width() * a.boundingBox.height() + b.boundingBox.width() * b.boundingBox.height() - intersection
        return if (union > 0f) intersection / union else 0f
    }
}
