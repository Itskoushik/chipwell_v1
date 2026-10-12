package com.example.chipvision.tracking

import android.graphics.RectF
import android.os.SystemClock
import com.example.chipvision.data.model.Detection
import com.example.chipvision.data.model.TrackedCandidate
import com.example.chipvision.data.preferences.InspectionSettings
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.hypot

class DetectionTracker {

    private val idGenerator = AtomicLong(1L)
    private val activeTracks = mutableMapOf<Long, TrackedCandidate>()

    fun reset() {
        activeTracks.clear()
    }

    /**
     * Updates active tracks with newly detected candidates in the current video frame.
     * Uses Hungarian/greedy IoU & center distance matching constrained by class compatibility.
     */
    fun updateTracks(
        detections: List<Detection>,
        currentTimeMs: Long = SystemClock.elapsedRealtime(),
        settings: InspectionSettings,
        sessionId: String
    ): List<TrackedCandidate> {
        // 1. Remove expired tracks exceeding trackingTimeoutMs
        val expiredTrackIds = activeTracks.filter { (_, track) ->
            (currentTimeMs - track.lastSeenMs) > settings.trackingTimeoutMs
        }.keys.toList()

        expiredTrackIds.forEach { activeTracks.remove(it) }

        // 2. Separate detections and active tracks by class compatibility (chip vs empty)
        val matchedDetectionIndices = mutableSetOf<Int>()
        val matchedTrackIds = mutableSetOf<Long>()

        // Greedily match existing tracks with current detections
        for ((trackId, track) in activeTracks) {
            var bestIou = 0f
            var bestDetectionIndex = -1

            for (i in detections.indices) {
                if (i in matchedDetectionIndices) continue
                val det = detections[i]

                // Class must match strictly (chip can never match empty)
                if (det.classId != track.classId) continue

                val iou = calculateIoU(track.boundingBox, det.boundingBox)
                val centerDist = calculateCenterDistance(track.boundingBox, det.boundingBox)

                // Must have either sufficient IoU or very close center
                val maxDim = maxOf(track.boundingBox.width(), track.boundingBox.height())
                val isNearby = centerDist <= (maxDim * 0.5f)

                if ((iou >= 0.30f || isNearby) && iou > bestIou) {
                    bestIou = iou
                    bestDetectionIndex = i
                }
            }

            if (bestDetectionIndex != -1) {
                matchedDetectionIndices.add(bestDetectionIndex)
                matchedTrackIds.add(trackId)

                val det = detections[bestDetectionIndex]
                val prevBox = track.boundingBox
                val newBox = det.boundingBox

                // Evaluate geometric stability between frames
                val widthRatio = if (prevBox.width() > 0) kotlin.math.abs(newBox.width() - prevBox.width()) / prevBox.width() else 1f
                val heightRatio = if (prevBox.height() > 0) kotlin.math.abs(newBox.height() - prevBox.height()) / prevBox.height() else 1f
                val iou = calculateIoU(prevBox, newBox)

                val isGeometricallyStable = (widthRatio <= settings.maxWidthChangeRatio) &&
                        (heightRatio <= settings.maxHeightChangeRatio) &&
                        (iou >= settings.minStableBoxIou)

                val stabilityStart = when {
                    !isGeometricallyStable -> currentTimeMs // Reset stability timer on sudden box jump
                    track.stabilityStartMs == null -> currentTimeMs
                    else -> track.stabilityStartMs
                }

                val stableDuration = currentTimeMs - stabilityStart
                val isConfirmedStable = stableDuration >= settings.stabilityDurationMs

                // If candidate moved significantly and had a previous classification, invalidate it
                val classification = if (!isGeometricallyStable) null else track.classification

                activeTracks[trackId] = track.copy(
                    boundingBox = newBox,
                    confidence = det.confidence,
                    lastSeenMs = currentTimeMs,
                    stabilityStartMs = stabilityStart,
                    isStable = isConfirmedStable,
                    classification = classification,
                    sessionId = sessionId
                )
            }
        }

        // 3. Any active track that was not matched in this frame has its stability invalidated
        for ((trackId, track) in activeTracks) {
            if (trackId !in matchedTrackIds) {
                // Not seen in this frame: cannot confirm stability
                activeTracks[trackId] = track.copy(
                    stabilityStartMs = currentTimeMs,
                    isStable = false
                )
            }
        }

        // 4. Create new tracks for unmatched detections
        for (i in detections.indices) {
            if (i !in matchedDetectionIndices) {
                val det = detections[i]
                val newId = idGenerator.getAndIncrement()
                activeTracks[newId] = TrackedCandidate(
                    trackId = newId,
                    classId = det.classId,
                    boundingBox = det.boundingBox,
                    confidence = det.confidence,
                    firstSeenMs = currentTimeMs,
                    lastSeenMs = currentTimeMs,
                    stabilityStartMs = currentTimeMs,
                    isStable = false, // Newly detected candidate starts with fresh stability history
                    classification = null,
                    sessionId = sessionId
                )
            }
        }

        return activeTracks.values.toList()
    }

    fun updateClassification(trackId: Long, classification: com.example.chipvision.data.model.ChipClassification) {
        val existing = activeTracks[trackId] ?: return
        activeTracks[trackId] = existing.copy(classification = classification)
    }

    fun invalidateAllStability(currentTimeMs: Long = SystemClock.elapsedRealtime()) {
        for ((id, track) in activeTracks) {
            activeTracks[id] = track.copy(
                stabilityStartMs = currentTimeMs,
                isStable = false,
                classification = null
            )
        }
    }

    companion object {
        fun calculateIoU(boxA: RectF, boxB: RectF): Float {
            val intersectionLeft = maxOf(boxA.left, boxB.left)
            val intersectionTop = maxOf(boxA.top, boxB.top)
            val intersectionRight = minOf(boxA.right, boxB.right)
            val intersectionBottom = minOf(boxA.bottom, boxB.bottom)

            val intersectionWidth = maxOf(0f, intersectionRight - intersectionLeft)
            val intersectionHeight = maxOf(0f, intersectionBottom - intersectionTop)
            val intersectionArea = intersectionWidth * intersectionHeight

            val areaA = maxOf(0f, boxA.width()) * maxOf(0f, boxA.height())
            val areaB = maxOf(0f, boxB.width()) * maxOf(0f, boxB.height())
            val unionArea = areaA + areaB - intersectionArea

            return if (unionArea > 0f) intersectionArea / unionArea else 0f
        }

        fun calculateCenterDistance(boxA: RectF, boxB: RectF): Float {
            val cAx = boxA.centerX()
            val cAy = boxA.centerY()
            val cBx = boxB.centerX()
            val cBy = boxB.centerY()
            return hypot(cAx - cBx, cAy - cBy)
        }
    }
}
