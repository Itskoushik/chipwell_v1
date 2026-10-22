package com.example.chipvision.engine

import android.graphics.RectF
import com.example.chipvision.data.model.Detection
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Deterministic business rules for the chipwell_yolo26n two-class detector. */
object InspectionPostProcessor {
    const val CHIP_CLASS_ID = 0
    const val MARKING_CLASS_ID = 1
    const val CHIP_CONFIDENCE = 0.80f
    const val MARKING_CONFIDENCE = 0.70f
    const val SIZE_TOLERANCE = 0.25f
    const val MARKING_OVERLAP_THRESHOLD = 0.50f

    enum class Side { TOP, RIGHT, BOTTOM, LEFT }
    data class RetainedMarking(val detection: Detection, val passes: Boolean, val nearestSide: Side)
    data class ChipResult(val chip: Detection, val markings: List<RetainedMarking>, val passes: Boolean)
    data class Result(val chips: List<ChipResult>, val finalPass: Boolean) {
        val acceptedDetections: List<Detection>
            get() = chips.flatMap { listOf(it.chip) + it.markings.map(RetainedMarking::detection) }
    }

    fun process(detections: List<Detection>): Result {
        val chips = detections.filter { it.classId == CHIP_CLASS_ID && it.confidence >= CHIP_CONFIDENCE && valid(it.boundingBox) }
        val markings = detections.filter { it.classId == MARKING_CLASS_ID && it.confidence >= MARKING_CONFIDENCE && valid(it.boundingBox) }
        val acceptedChips = sizeFilter(chips)
        val associated = acceptedChips.associateWith { mutableListOf<Detection>() }
        for (mark in markings) {
            val best = acceptedChips.maxByOrNull { overlap(mark.boundingBox, it.boundingBox) } ?: continue
            if (overlap(mark.boundingBox, best.boundingBox) >= MARKING_OVERLAP_THRESHOLD) associated.getValue(best).add(mark)
        }
        val results = acceptedChips.map { chip ->
            val retained = sizeFilter(associated.getValue(chip)).map { mark ->
                val side = nearestSide(chip.boundingBox, mark.boundingBox)
                RetainedMarking(mark, side == Side.TOP || side == Side.RIGHT, side)
            }
            ChipResult(chip, retained, retained.isNotEmpty() && retained.all { it.passes })
        }
        return Result(results, results.isNotEmpty() && results.all { it.passes })
    }

    fun sizeFilter(items: List<Detection>, tolerance: Float = SIZE_TOLERANCE): List<Detection> {
        val valid = items.filter { valid(it.boundingBox) }
        val reference = valid.maxByOrNull { it.confidence } ?: return emptyList()
        val (refShort, refLong) = dimensions(reference.boundingBox)
        return valid.filter { candidate ->
            if (candidate === reference) true else {
                val (short, long) = dimensions(candidate.boundingBox)
                refShort > 0f && refLong > 0f &&
                    abs(short - refShort) / refShort <= tolerance && abs(long - refLong) / refLong <= tolerance
            }
        }
    }

    fun nearestSide(chip: RectF, mark: RectF): Side {
        val u = (mark.centerX() - chip.left) / chip.width()
        val v = (mark.centerY() - chip.top) / chip.height()
        // Tie order matches the reference's distance tuple: left, right, top, bottom.
        val distances = listOf(u to Side.LEFT, 1f - u to Side.RIGHT, v to Side.TOP, 1f - v to Side.BOTTOM)
        return distances.minBy { it.first }.second
    }

    fun overlap(mark: RectF, chip: RectF): Float {
        val intersection = max(0f, min(mark.right, chip.right) - max(mark.left, chip.left)) *
            max(0f, min(mark.bottom, chip.bottom) - max(mark.top, chip.top))
        val markArea = mark.width() * mark.height()
        return if (markArea > 0f) intersection / markArea else 0f
    }

    private fun dimensions(box: RectF): Pair<Float, Float> = min(box.width(), box.height()) to max(box.width(), box.height())
    private fun valid(box: RectF): Boolean = box.left.isFinite() && box.top.isFinite() && box.right.isFinite() && box.bottom.isFinite() && box.width() > 0f && box.height() > 0f
}
