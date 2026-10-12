package com.example.chipvision.tracking

import android.graphics.Bitmap
import android.os.SystemClock
import com.example.chipvision.data.model.TrackedCandidate
import com.example.chipvision.data.preferences.InspectionSettings

class StabilityValidator {

    private var previousSampleLuma: ByteArray? = null
    private val sampleSize = 32

    /**
     * Checks if global scene motion (e.g. conveyor moving or camera vibration) is occurring.
     * Downsamples the frame to 32x32 luminance grid to compute mean absolute pixel difference.
     */
    fun checkGlobalSceneMotion(bitmap: Bitmap, sensitivity: Float): Boolean {
        val scaled = Bitmap.createScaledBitmap(bitmap, sampleSize, sampleSize, false)
        val pixels = IntArray(sampleSize * sampleSize)
        scaled.getPixels(pixels, 0, sampleSize, 0, 0, sampleSize, sampleSize)
        if (scaled != bitmap) {
            scaled.recycle()
        }

        val currentLuma = ByteArray(sampleSize * sampleSize)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            currentLuma[i] = ((0.299f * r + 0.587f * g + 0.114f * b).toInt()).toByte()
        }

        val prev = previousSampleLuma
        previousSampleLuma = currentLuma

        if (prev == null) {
            return false // First frame, assume no motion
        }

        var totalDiff = 0L
        for (i in currentLuma.indices) {
            val diff = kotlin.math.abs((currentLuma[i].toInt() and 0xFF) - (prev[i].toInt() and 0xFF))
            totalDiff += diff
        }

        val averageDiffRatio = (totalDiff.toFloat() / (sampleSize * sampleSize)) / 255.0f
        return averageDiffRatio > sensitivity
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
    }
}

data class CandidatesStabilityResult(
    val isAllStable: Boolean,
    val stableCandidates: List<TrackedCandidate>,
    val unstableCandidates: List<TrackedCandidate>,
    val hasEmptyImpression: Boolean
)
