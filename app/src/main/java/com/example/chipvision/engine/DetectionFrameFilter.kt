package com.example.chipvision.engine

import android.graphics.RectF
import com.example.chipvision.data.model.Detection

data class FilteredDetections(
    val detections: List<Detection>,
    val validatedEmptyDetections: List<Detection>,
    val rejectedUndersizedDetections: List<Detection>,
    val initialChipCount: Int,
    val acceptedChipCount: Int,
    val rejectedUndersizedChipCount: Int
)

object DetectionFrameFilter {
    const val DEFAULT_EMPTY_OVERLAP_THRESHOLD = 0.60f
    const val DEFAULT_BOX_SIZE_TOLERANCE = 0.10f

    fun filter(
        detections: List<Detection>,
        confidenceThreshold: Float,
        emptyOverlapThreshold: Float = DEFAULT_EMPTY_OVERLAP_THRESHOLD,
        boxSizeTolerance: Float = DEFAULT_BOX_SIZE_TOLERANCE
    ): FilteredDetections {
        val eligible = detections.filter { it.confidence >= confidenceThreshold }
        val chips = eligible.filter { it.isChip }
        val empty = eligible.filter { it.isEmptyImpression }

        val validatedEmpty = empty.filter { candidate ->
            chips.none { chip ->
                overlapRatio(candidate.boundingBox, chip.boundingBox) >= emptyOverlapThreshold
            }
        }
        if (validatedEmpty.isNotEmpty()) {
            val reference = chips.maxByOrNull { it.confidence }
            val acceptedChips = if (reference == null) {
                emptyList()
            } else {
                val referenceWidth = reference.boundingBox.right - reference.boundingBox.left
                val referenceHeight = reference.boundingBox.bottom - reference.boundingBox.top
                chips.filter { candidate ->
                    val candidateWidth = candidate.boundingBox.right - candidate.boundingBox.left
                    val candidateHeight = candidate.boundingBox.bottom - candidate.boundingBox.top
                    val widthDeficit = (referenceWidth - candidateWidth) / referenceWidth
                    val heightDeficit = (referenceHeight - candidateHeight) / referenceHeight
                    widthDeficit <= boxSizeTolerance && heightDeficit <= boxSizeTolerance
                }
            }
            val rejectedChips = chips - acceptedChips.toSet()
            return FilteredDetections(
                detections = acceptedChips + validatedEmpty,
                validatedEmptyDetections = validatedEmpty,
                rejectedUndersizedDetections = rejectedChips,
                initialChipCount = chips.size,
                acceptedChipCount = acceptedChips.size,
                rejectedUndersizedChipCount = rejectedChips.size
            )
        }

        if (chips.isEmpty()) {
            return FilteredDetections(
                detections = emptyList(),
                validatedEmptyDetections = emptyList(),
                rejectedUndersizedDetections = emptyList(),
                initialChipCount = 0,
                acceptedChipCount = 0,
                rejectedUndersizedChipCount = 0
            )
        }

        val reference = chips.maxBy { it.confidence }
        val referenceWidth = reference.boundingBox.right - reference.boundingBox.left
        val referenceHeight = reference.boundingBox.bottom - reference.boundingBox.top
        val accepted = chips.filter { candidate ->
            val candidateWidth = candidate.boundingBox.right - candidate.boundingBox.left
            val candidateHeight = candidate.boundingBox.bottom - candidate.boundingBox.top
            val widthDeficit = (referenceWidth - candidateWidth) / referenceWidth
            val heightDeficit = (referenceHeight - candidateHeight) / referenceHeight
            widthDeficit <= boxSizeTolerance && heightDeficit <= boxSizeTolerance
        }
        val rejected = chips - accepted.toSet()

        return FilteredDetections(
            detections = accepted,
            validatedEmptyDetections = emptyList(),
            rejectedUndersizedDetections = rejected,
            initialChipCount = chips.size,
            acceptedChipCount = accepted.size,
            rejectedUndersizedChipCount = chips.size - accepted.size
        )
    }

    fun overlapRatio(first: RectF, second: RectF): Float {
        val left = maxOf(first.left, second.left)
        val top = maxOf(first.top, second.top)
        val right = minOf(first.right, second.right)
        val bottom = minOf(first.bottom, second.bottom)
        val intersection = maxOf(0f, right - left) * maxOf(0f, bottom - top)
        val firstArea = (first.right - first.left) * (first.bottom - first.top)
        val secondArea = (second.right - second.left) * (second.bottom - second.top)
        val smallerArea = minOf(firstArea, secondArea)
        return if (smallerArea > 0f) intersection / smallerArea else 0f
    }
}
