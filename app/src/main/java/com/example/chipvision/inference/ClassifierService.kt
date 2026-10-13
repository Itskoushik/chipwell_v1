package com.example.chipvision.inference

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.example.chipvision.data.model.ChipClassification
import com.example.chipvision.data.model.ChipOrientation
import com.example.chipvision.data.model.ChipType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp

class ClassifierService(private val modelManager: ModelManager) {

    private val tag = "ClassifierService"

    private var inputBuffer: ByteBuffer? = null
    private var lastInputSize: Int = 0
    private var lastNchw: Boolean = false

    companion object {
        val CLASS_LABELS = arrayOf(
            "PCS_06_K_correct_upward", // 0
            "PCS_06_K_downward",       // 1
            "PCS_06_K_flipped",        // 2
            "PCS_26_C_correct_upward", // 3
            "PCS_26_C_downward",       // 4
            "PCS_26_C_flipped"         // 5
        )

        val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        val STD = floatArrayOf(0.229f, 0.224f, 0.225f)
    }

    /**
     * Crops the candidate chip from the retained source frame and classifies type and orientation.
     * Note: Empty impressions must NEVER be passed to this method!
     */
    @Synchronized
    fun classifyChip(
        sourceFrame: Bitmap,
        boundingBox: RectF,
        threshold: Float = 0.70f
    ): ChipClassification {
        val interpreter = modelManager.classifierInterpreter
            ?: return ChipClassification(
                chipType = ChipType.UNKNOWN,
                orientation = ChipOrientation.UNCERTAIN,
                classIndex = -1,
                label = "Classifier Unavailable",
                confidence = 0f,
                isAccepted = false
            )

        // Validate crop rect within source frame boundaries
        val left = boundingBox.left.toInt().coerceIn(0, sourceFrame.width - 1)
        val top = boundingBox.top.toInt().coerceIn(0, sourceFrame.height - 1)
        val right = boundingBox.right.toInt().coerceIn(left + 1, sourceFrame.width)
        val bottom = boundingBox.bottom.toInt().coerceIn(top + 1, sourceFrame.height)
        val cropW = right - left
        val cropH = bottom - top

        if (cropW < 4 || cropH < 4) {
            return ChipClassification(
                chipType = ChipType.UNKNOWN,
                orientation = ChipOrientation.UNCERTAIN,
                classIndex = -1,
                label = "Invalid Crop Area",
                confidence = 0f,
                isAccepted = false
            )
        }

        val cropped = Bitmap.createBitmap(sourceFrame, left, top, cropW, cropH)
        val inputSize = modelManager.classifierInputSize
        val isNchw = modelManager.isClassifierNchw

        val scaled = Bitmap.createScaledBitmap(cropped, inputSize, inputSize, true)
        if (cropped != scaled) {
            cropped.recycle()
        }

        val byteBuffer = getOrCreateBuffer(inputSize, isNchw)
        byteBuffer.rewind()

        val pixels = IntArray(inputSize * inputSize)
        scaled.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        scaled.recycle()

        if (isNchw) {
            val totalPixels = inputSize * inputSize
            // R plane
            for (i in 0 until totalPixels) {
                val r = ((pixels[i] shr 16) and 0xFF) / 255.0f
                byteBuffer.putFloat((r - MEAN[0]) / STD[0])
            }
            // G plane
            for (i in 0 until totalPixels) {
                val g = ((pixels[i] shr 8) and 0xFF) / 255.0f
                byteBuffer.putFloat((g - MEAN[1]) / STD[1])
            }
            // B plane
            for (i in 0 until totalPixels) {
                val b = (pixels[i] and 0xFF) / 255.0f
                byteBuffer.putFloat((b - MEAN[2]) / STD[2])
            }
        } else {
            // NHWC
            for (pixel in pixels) {
                val r = ((pixel shr 16) and 0xFF) / 255.0f
                val g = ((pixel shr 8) and 0xFF) / 255.0f
                val b = (pixel and 0xFF) / 255.0f

                byteBuffer.putFloat((r - MEAN[0]) / STD[0])
                byteBuffer.putFloat((g - MEAN[1]) / STD[1])
                byteBuffer.putFloat((b - MEAN[2]) / STD[2])
            }
        }

        val outputShape = interpreter.getOutputTensor(0).shape()
        if (outputShape.size != 2 || outputShape[0] != 1 || outputShape[1] != CLASS_LABELS.size) {
            Log.e(tag, "Unsupported classifier output shape: ${outputShape.contentToString()}")
            return ChipClassification(
                chipType = ChipType.UNKNOWN,
                orientation = ChipOrientation.UNCERTAIN,
                classIndex = -1,
                label = "Unsupported Output Shape",
                confidence = 0f,
                isAccepted = false
            )
        }
        val outputArray = Array(1) { FloatArray(outputShape[1]) }

        try {
            interpreter.run(byteBuffer, outputArray)
        } catch (e: Exception) {
            Log.e(tag, "Classifier inference failed", e)
            return ChipClassification(
                chipType = ChipType.UNKNOWN,
                orientation = ChipOrientation.UNCERTAIN,
                classIndex = -1,
                label = "Inference Error",
                confidence = 0f,
                isAccepted = false
            )
        }

        val rawScores = outputArray[0]
        val outputSum = rawScores.sum()
        val isProbabilityOutput = rawScores.all { it.isFinite() && it >= 0f && it <= 1f } &&
            kotlin.math.abs(outputSum - 1f) <= 0.01f
        val probabilities = if (isProbabilityOutput) rawScores.copyOf() else softmax(rawScores)
        Log.i(
            tag,
            "Classifier output=${rawScores.contentToString()} outputMode=" +
                "${if (isProbabilityOutput) "probabilities" else "logits"} " +
                "probabilities=${probabilities.contentToString()}"
        )

        var bestIndex = 0
        var maxProb = -1f
        for (i in probabilities.indices) {
            if (probabilities[i] > maxProb) {
                maxProb = probabilities[i]
                bestIndex = i
            }
        }

        val isAccepted = maxProb >= threshold && maxProb.isFinite()
        val label = if (bestIndex in CLASS_LABELS.indices) CLASS_LABELS[bestIndex] else "Unknown"

        val (chipType, orientation) = mapIndexToTypeAndOrientation(bestIndex, isAccepted)

        return ChipClassification(
            chipType = chipType,
            orientation = orientation,
            classIndex = bestIndex,
            label = label,
            confidence = maxProb,
            isAccepted = isAccepted
        )
    }

    private fun softmax(scores: FloatArray): FloatArray {
        var maxScore = Float.NEGATIVE_INFINITY
        for (s in scores) {
            if (s > maxScore) maxScore = s
        }

        var expSum = 0f
        val expScores = FloatArray(scores.size)
        for (i in scores.indices) {
            val e = exp(scores[i] - maxScore)
            expScores[i] = e
            expSum += e
        }

        if (expSum > 0f) {
            for (i in expScores.indices) {
                expScores[i] /= expSum
            }
        }
        return expScores
    }

    fun mapIndexToTypeAndOrientation(
        index: Int,
        isAccepted: Boolean
    ): Pair<ChipType, ChipOrientation> {
        if (!isAccepted) {
            return Pair(ChipType.UNKNOWN, ChipOrientation.UNCERTAIN)
        }

        return when (index) {
            0 -> Pair(ChipType.PCS_06_K, ChipOrientation.CORRECT_UPWARD)
            1 -> Pair(ChipType.PCS_06_K, ChipOrientation.DOWNWARD)
            2 -> Pair(ChipType.PCS_06_K, ChipOrientation.FLIPPED)
            3 -> Pair(ChipType.PCS_26_C, ChipOrientation.CORRECT_UPWARD)
            4 -> Pair(ChipType.PCS_26_C, ChipOrientation.DOWNWARD)
            5 -> Pair(ChipType.PCS_26_C, ChipOrientation.FLIPPED)
            else -> Pair(ChipType.UNKNOWN, ChipOrientation.UNCERTAIN)
        }
    }

    private fun getOrCreateBuffer(size: Int, isNchw: Boolean): ByteBuffer {
        if (inputBuffer == null || lastInputSize != size || lastNchw != isNchw) {
            val numBytes = 1 * 3 * size * size * 4
            inputBuffer = ByteBuffer.allocateDirect(numBytes).apply {
                order(ByteOrder.nativeOrder())
            }
            lastInputSize = size
            lastNchw = isNchw
        }
        return inputBuffer!!
    }
}
