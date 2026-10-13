package com.example.chipvision.tracking

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import com.example.chipvision.data.model.TrackedCandidate
import com.example.chipvision.data.preferences.InspectionSettings

class StabilityValidator {

    private var previousSampleLuma: ByteArray? = null
    private val sampleSize = 32
    private val samplePixels = IntArray(sampleSize * sampleSize)
    private var currentSampleLuma = ByteArray(sampleSize * sampleSize)
    private val sampleBitmap = Bitmap.createBitmap(sampleSize, sampleSize, Bitmap.Config.ARGB_8888)
    private val sampleCanvas = Canvas(sampleBitmap)
    private val samplePaint = Paint().apply { isFilterBitmap = false }
    private val sampleDestination = Rect(0, 0, sampleSize, sampleSize)
    private var motionEvidenceFrames = 0
    private var lastMotionScore = 0f

    /**
     * Checks if global scene motion (e.g. conveyor moving or camera vibration) is occurring.
     * Downsamples the frame to 32x32 luminance grid to compute mean absolute pixel difference.
     */
    fun checkGlobalSceneMotion(
        bitmap: Bitmap,
        sensitivity: Float,
        confirmationFrames: Int = 2
    ): Boolean {
        sampleCanvas.drawBitmap(
            bitmap,
            null,
            sampleDestination,
            samplePaint
        )
        sampleBitmap.getPixels(samplePixels, 0, sampleSize, 0, 0, sampleSize, sampleSize)

        for (i in samplePixels.indices) {
            val p = samplePixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            currentSampleLuma[i] = ((0.299f * r + 0.587f * g + 0.114f * b).toInt()).toByte()
        }

        val prev = previousSampleLuma

        if (prev == null) {
            previousSampleLuma = currentSampleLuma
            currentSampleLuma = ByteArray(sampleSize * sampleSize)
            motionEvidenceFrames = 0
            lastMotionScore = 0f
            return false // First frame, assume no motion
        }

        var totalDiff = 0L
        for (i in currentSampleLuma.indices) {
            val diff = kotlin.math.abs((currentSampleLuma[i].toInt() and 0xFF) - (prev[i].toInt() and 0xFF))
            totalDiff += diff
        }

        val averageDiffRatio = (totalDiff.toFloat() / (sampleSize * sampleSize)) / 255.0f
        previousSampleLuma = currentSampleLuma
        currentSampleLuma = prev
        lastMotionScore = averageDiffRatio
        motionEvidenceFrames = if (averageDiffRatio > sensitivity) {
            motionEvidenceFrames + 1
        } else {
            0
        }
        val confirmed = motionEvidenceFrames >= confirmationFrames.coerceAtLeast(1)
        if (averageDiffRatio > sensitivity || confirmed) {
            android.util.Log.d(
                "CHIPVISION_MOVEMENT",
                "sceneScore=${"%.4f".format(averageDiffRatio)} threshold=$sensitivity " +
                    "evidence=$motionEvidenceFrames/${confirmationFrames.coerceAtLeast(1)} " +
                    "confirmed=$confirmed"
            )
        }
        return confirmed
    }

    /**
     * Validates whether all required candidates in the scene have maintained stability
     * for at least [InspectionSettings.stabilityDurationMs].
     */
    fun validateCandidatesStability(
        candidates: List<TrackedCandidate>,
        currentTimeMs: Long = SystemClock.elapsedRealtime(),
        settings: InspectionSettings
    ): CandidatesStabilityResult {
        if (candidates.isEmpty()) {
            return CandidatesStabilityResult(
                isAllStable = false,
                stableCandidates = emptyList(),
                unstableCandidates = emptyList(),
                hasEmptyImpression = false
            )
        }

        val stableList = mutableListOf<TrackedCandidate>()
        val unstableList = mutableListOf<TrackedCandidate>()
        var hasEmpty = false

        for (c in candidates) {
            val duration = if (c.stabilityStartMs != null) currentTimeMs - c.stabilityStartMs else 0L
            val isStable = duration >= settings.stabilityDurationMs

            if (isStable) {
                stableList.add(c)
                if (c.isEmptyImpression) {
                    hasEmpty = true
                }
            } else {
                unstableList.add(c)
            }
        }

        val isAllStable = unstableList.isEmpty() && stableList.isNotEmpty()

        return CandidatesStabilityResult(
            isAllStable = isAllStable,
            stableCandidates = stableList,
            unstableCandidates = unstableList,
            hasEmptyImpression = hasEmpty
        )
    }

    fun reset() {
        previousSampleLuma = null
        motionEvidenceFrames = 0
        lastMotionScore = 0f
    }
}

data class CandidatesStabilityResult(
    val isAllStable: Boolean,
    val stableCandidates: List<TrackedCandidate>,
    val unstableCandidates: List<TrackedCandidate>,
    val hasEmptyImpression: Boolean
)
