package com.example.chipvision.tracking

import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
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

    fun activeTrackCount(): Int = activeTracks.size

    fun renderableTracks(
        currentTimeMs: Long,
        trackingTimeoutMs: Long
    ): List<TrackedCandidate> {
        return activeTracks.values.filter {
            currentTimeMs - it.lastSeenMs <= trackingTimeoutMs
        }
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
            var bestScore = Float.NEGATIVE_INFINITY
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

                if (iou >= 0.30f || isNearby) {
                    // Prefer overlap, while still allowing a nearby detection whose
                    // box moved between frames to retain the existing track.
                    val score = iou + if (isNearby) 0.01f else 0f
                    if (score > bestScore) {
                        bestScore = score
                        bestDetectionIndex = i
                    }
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
                val centerDisplacement = calculateCenterDistance(prevBox, newBox)
                val referenceDimension = maxOf(prevBox.width(), prevBox.height(), 1f)
                val centerDisplacementRatio = centerDisplacement / referenceDimension
                val isWithinMovementTolerance =
                    centerDisplacementRatio <= settings.movementCenterToleranceRatio &&
                        iou >= settings.movementIouTolerance
                val isGeometricallyStable = (widthRatio <= settings.maxWidthChangeRatio) &&
                        (heightRatio <= settings.maxHeightChangeRatio) &&
                        iou >= settings.minStableBoxIou &&
                        isWithinMovementTolerance
                val movementEvidenceFrames = if (isWithinMovementTolerance) {
                    0
                } else {
                    track.movementEvidenceFrames + 1
                }
                val movementConfirmed =
                    movementEvidenceFrames >= settings.movementConfirmationFrames

                val stabilityStart = when {
                    movementConfirmed -> currentTimeMs
                    track.stabilityStartMs == null -> currentTimeMs
                    else -> track.stabilityStartMs
                }

                val stableDuration = currentTimeMs - stabilityStart
                val isConfirmedStable = stableDuration >= settings.stabilityDurationMs

                if (!isWithinMovementTolerance || movementConfirmed) {
                    Log.d(
                        "CHIPVISION_MOVEMENT",
                        "track=$trackId displacementRatio=${"%.4f".format(centerDisplacementRatio)} " +
                            "iou=${"%.3f".format(iou)} tolerance=${settings.movementCenterToleranceRatio} " +
                            "iouTolerance=${settings.movementIouTolerance} " +
                            "evidence=$movementEvidenceFrames/${settings.movementConfirmationFrames} " +
                            "confirmed=$movementConfirmed"
                    )
                }

                activeTracks[trackId] = track.copy(
                    boundingBox = newBox,
                    confidence = det.confidence,
                    lastSeenMs = currentTimeMs,
                    stabilityStartMs = stabilityStart,
                    isStable = isConfirmedStable,
                    sessionId = sessionId,
                    movementEvidenceFrames = movementEvidenceFrames
                )
            }
        }

        // 3. Any active track that was not matched in this frame has its stability invalidated
        for ((trackId, track) in activeTracks) {
            if (trackId !in matchedTrackIds) {
                // Preserve the stability start through a brief detector miss. The coordinator
                // clears the live overlay, while trackingTimeoutMs determines when the track is
                // genuinely gone and should re-arm inspection.
                activeTracks[trackId] = track.copy(
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
                    sessionId = sessionId,
                    movementEvidenceFrames = 0
                )
            }
        }

        return activeTracks.values.toList()
    }

    fun removeTracksForDetections(detections: List<Detection>, classId: Int) {
        if (detections.isEmpty()) return
        val trackIds = activeTracks.filter { (_, track) ->
            track.classId == classId && detections.any { detection ->
                calculateIoU(track.boundingBox, detection.boundingBox) >= 0.8f
            }
        }.keys
        trackIds.forEach(activeTracks::remove)
    }

    fun clearTracksForClass(classId: Int) {
        activeTracks
            .filterValues { it.classId == classId }
            .keys
            .forEach(activeTracks::remove)
    }

    fun invalidateAllStability(currentTimeMs: Long = SystemClock.elapsedRealtime()) {
        for ((id, track) in activeTracks) {
            activeTracks[id] = track.copy(
                stabilityStartMs = currentTimeMs,
                isStable = false,
                movementEvidenceFrames = 0
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
