package com.example.chipvision.engine

import android.graphics.RectF
import com.example.chipvision.data.model.Detection
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class DuplicateCandidateMeasurement(
    val classId: Int,
    val confidence: Float,
    val boundingBox: RectF,
    val survivedFilter: Boolean
)

data class OverlappingCandidatePair(
    val first: DuplicateCandidateMeasurement,
    val second: DuplicateCandidateMeasurement,
    val iou: Float,
    val centerDistancePx: Float,
    val confidenceDifference: Float
)

data class DuplicateDiagnosticFrame(
    val frameId: Long,
    val timestampNs: Long,
    val candidateCountBeforeFilter: Int,
    val candidateCountAfterFilter: Int,
    val candidates: List<DuplicateCandidateMeasurement>,
    val overlappingSameClassPairs: List<OverlappingCandidatePair>
)

data class DuplicateDiagnosticsSnapshot(
    val enabled: Boolean = false,
    val sampleEveryNFrames: Long = 0L,
    val framesSampled: Int = 0,
    val candidatePairComparisons: Long = 0L,
    val sameClassOverlappingPairs: Int = 0,
    val candidateCountBeforeFilter: Long = 0L,
    val candidateCountAfterFilter: Long = 0L,
    val frames: List<DuplicateDiagnosticFrame> = emptyList()
)

/**
 * Collects geometric evidence only. It never removes or reorders detections.
 */
class DuplicateDiagnostics(
    private val maxFrames: Int = DEFAULT_MAX_FRAMES,
    private val maxPairs: Int = DEFAULT_MAX_PAIRS,
    private val maxComparisonsPerFrame: Int = DEFAULT_MAX_COMPARISONS_PER_FRAME,
    sampleEveryNFrames: Long = DEFAULT_SAMPLE_EVERY_N_FRAMES
) {
    private val lock = Any()
    private val frames = ArrayDeque<DuplicateDiagnosticFrame>(maxFrames)
    private var pairComparisons = 0L
    private var overlappingPairs = 0
    private var beforeFilter = 0L
    private var afterFilter = 0L
    private var samplingInterval = sampleEveryNFrames
    private var snapshot = DuplicateDiagnosticsSnapshot()

    init {
        require(maxFrames > 0) { "maxFrames must be positive" }
        require(maxPairs > 0) { "maxPairs must be positive" }
        require(maxComparisonsPerFrame > 0) { "maxComparisonsPerFrame must be positive" }
        require(sampleEveryNFrames >= 0L) { "sampleEveryNFrames must not be negative" }
        publish()
    }

    fun setSampleEveryNFrames(value: Long) {
        require(value >= 0L) { "sampleEveryNFrames must not be negative" }
        synchronized(lock) {
            samplingInterval = value
            publish()
        }
    }

    fun recordFrame(
        frameId: Long,
        timestampNs: Long,
        beforeFilter: List<Detection>,
        afterFilter: List<Detection>
    ) {
        require(frameId >= 0L) { "frameId must not be negative" }
        require(timestampNs >= 0L) { "timestampNs must not be negative" }
        synchronized(lock) {
            if (samplingInterval == 0L || frameId % samplingInterval != 0L) return

            val measurements = beforeFilter.map { detection ->
                DuplicateCandidateMeasurement(
                    classId = detection.classId,
                    confidence = detection.confidence,
                    boundingBox = RectF(detection.boundingBox),
                    survivedFilter = afterFilter.any { it === detection }
                )
            }
            val pairs = mutableListOf<OverlappingCandidatePair>()
            var comparisonsThisFrame = 0
            comparisonLoop@ for (firstIndex in beforeFilter.indices) {
                for (secondIndex in firstIndex + 1 until beforeFilter.size) {
                    if (comparisonsThisFrame >= maxComparisonsPerFrame) break@comparisonLoop
                    val first = beforeFilter[firstIndex]
                    val second = beforeFilter[secondIndex]
                    pairComparisons++
                    comparisonsThisFrame++
                    if (first.classId != second.classId) continue
                    val iou = intersectionOverUnion(first.boundingBox, second.boundingBox)
                    if (iou <= 0f || pairs.size >= maxPairs) continue
                    pairs += OverlappingCandidatePair(
                        first = measurements[firstIndex],
                        second = measurements[secondIndex],
                        iou = iou,
                        centerDistancePx = centerDistance(first.boundingBox, second.boundingBox),
                        confidenceDifference = kotlin.math.abs(first.confidence - second.confidence)
                    )
                }
            }
            overlappingPairs += pairs.size
            if (frames.size == maxFrames) frames.removeFirst()
            frames.addLast(
                DuplicateDiagnosticFrame(
                    frameId = frameId,
                    timestampNs = timestampNs,
                    candidateCountBeforeFilter = beforeFilter.size,
                    candidateCountAfterFilter = afterFilter.size,
                    candidates = measurements,
                    overlappingSameClassPairs = pairs
                )
            )
            this.beforeFilter += beforeFilter.size
            this.afterFilter += afterFilter.size
            publish()
        }
    }

    fun snapshot(): DuplicateDiagnosticsSnapshot = synchronized(lock) { snapshot }

    fun reset() {
        synchronized(lock) {
            frames.clear()
            pairComparisons = 0
            overlappingPairs = 0
            beforeFilter = 0L
            afterFilter = 0L
            publish()
        }
    }

    private fun publish() {
        snapshot = DuplicateDiagnosticsSnapshot(
            enabled = samplingInterval > 0L,
            sampleEveryNFrames = samplingInterval,
            framesSampled = frames.size,
            candidatePairComparisons = pairComparisons,
            sameClassOverlappingPairs = overlappingPairs,
            candidateCountBeforeFilter = beforeFilter,
            candidateCountAfterFilter = afterFilter,
            frames = frames.toList()
        )
    }

    companion object {
        const val DEFAULT_SAMPLE_EVERY_N_FRAMES = 30L
        const val DEFAULT_MAX_FRAMES = 60
        const val DEFAULT_MAX_PAIRS = 500
        const val DEFAULT_MAX_COMPARISONS_PER_FRAME = 2_000

        fun intersectionOverUnion(first: RectF, second: RectF): Float {
            val left = max(first.left, second.left)
            val top = max(first.top, second.top)
            val right = min(first.right, second.right)
            val bottom = min(first.bottom, second.bottom)
            val intersection = max(0f, right - left) * max(0f, bottom - top)
            val firstArea = max(0f, first.right - first.left) * max(0f, first.bottom - first.top)
            val secondArea = max(0f, second.right - second.left) * max(0f, second.bottom - second.top)
            val union = firstArea + secondArea - intersection
            return if (union > 0f) intersection / union else 0f
        }

        fun centerDistance(first: RectF, second: RectF): Float {
            val firstCenterX = (first.left + first.right) / 2f
            val firstCenterY = (first.top + first.bottom) / 2f
            val secondCenterX = (second.left + second.right) / 2f
            val secondCenterY = (second.top + second.bottom) / 2f
            return hypot(firstCenterX - secondCenterX, firstCenterY - secondCenterY)
        }
    }
}
