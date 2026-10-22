package com.example.chipvision.inference

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import android.os.SystemClock
import com.example.chipvision.data.model.Detection
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DetectorService(private val modelManager: ModelManager) {

    private val tag = "DetectorService"

    // Reusable buffers to avoid GC allocations during high FPS continuous video
    private var inputBuffer: ByteBuffer? = null
    private var lastInputSize: Int = 0
    private var lastNchw: Boolean = false
    private var inferenceCount = 0L

    /**
     * Runs detection on the supplied RGB Bitmap.
     * Maps coordinates back to the original image dimensions [origWidth x origHeight].
     */
    @Synchronized
    fun detect(bitmap: Bitmap, threshold: Float = 0.70f): List<Detection> {
        val interpreter = modelManager.detectorInterpreter
            ?: throw IllegalStateException("${ModelManager.MODEL_FILE_NAME} is not initialized")

        val inputSize = modelManager.detectorInputSize
        val isNchw = modelManager.isDetectorNchw

        // Letterbox / resize bitmap to inputSize x inputSize
        val origW = bitmap.width.toFloat()
        val origH = bitmap.height.toFloat()
        if (origW <= 0f || origH <= 0f) return emptyList()

        val scale = minOf(inputSize.toFloat() / origW, inputSize.toFloat() / origH)
        val scaledW = (origW * scale).toInt().coerceAtLeast(1)
        val scaledH = (origH * scale).toInt().coerceAtLeast(1)
        val padX = (inputSize - scaledW) / 2f
        val padY = (inputSize - scaledH) / 2f

        val resizedBitmap = Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resizedBitmap)
        canvas.drawColor(Color.rgb(114, 114, 114)) // Standard neutral letterbox padding
        val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
        val dstRect = Rect(padX.toInt(), padY.toInt(), padX.toInt() + scaledW, padY.toInt() + scaledH)
        canvas.drawBitmap(bitmap, srcRect, dstRect, Paint(Paint.FILTER_BITMAP_FLAG))

        // Prepare ByteBuffer
        val byteBuffer = getOrCreateBuffer(inputSize, isNchw)
        byteBuffer.rewind()

        val pixels = IntArray(inputSize * inputSize)
        resizedBitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        resizedBitmap.recycle()

        if (isNchw) {
            // NCHW: Plane R, then Plane G, then Plane B
            val totalPixels = inputSize * inputSize
            // Red channel
            for (i in 0 until totalPixels) {
                val pixel = pixels[i]
                byteBuffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f)
            }
            // Green channel
            for (i in 0 until totalPixels) {
                val pixel = pixels[i]
                byteBuffer.putFloat(((pixel shr 8) and 0xFF) / 255.0f)
            }
            // Blue channel
            for (i in 0 until totalPixels) {
                val pixel = pixels[i]
                byteBuffer.putFloat((pixel and 0xFF) / 255.0f)
            }
        } else {
            // NHWC: Interleaved R, G, B
            for (pixel in pixels) {
                byteBuffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f)
                byteBuffer.putFloat(((pixel shr 8) and 0xFF) / 255.0f)
                byteBuffer.putFloat((pixel and 0xFF) / 255.0f)
            }
        }

        // Run inference
        val outputTensor = interpreter.getOutputTensor(0)
        val outputShape = outputTensor.shape()
        if (outputShape.size != 3 || outputShape[0] != 1 || outputTensor.dataType().name != "FLOAT32") {
            throw IllegalStateException("Unsupported detector output tensor ${outputShape.contentToString()} (${outputTensor.dataType()})")
        }
        val recordsFirst = outputShape[2] == 5 || outputShape[2] == 6
        val channelsFirst = outputShape[1] == 5 || outputShape[1] == 6
        if (recordsFirst == channelsFirst) {
            throw IllegalStateException("Unsupported detector output layout ${outputShape.contentToString()}")
        }
        val recordSize = if (recordsFirst) outputShape[2] else outputShape[1]
        if (recordSize != 6) throw IllegalStateException("Expected 6 values per raw YOLO prediction, got $recordSize from ${outputShape.contentToString()}")
        val outputLayout = DetectorOutputLayout.CENTER_MULTI_CLASS
        val outputArray = Array(outputShape[0]) {
            Array(outputShape[1]) { FloatArray(outputShape[2]) }
        }

        val inferenceStartMs = SystemClock.elapsedRealtime()
        try {
            interpreter.run(byteBuffer, outputArray)
        } catch (e: Exception) {
            Log.e(tag, "Detector inference failed", e)
            throw IllegalStateException("Detector inference failed", e)
        }

        val detections = mutableListOf<Detection>()
        val rawDetections = outputArray.firstOrNull() ?: return emptyList()
        val rowCount = if (recordsFirst) outputShape[1] else outputShape[2]
        var finiteCount = 0
        var supportedClassCount = 0
        var validBoxCount = 0
        var thresholdCount = 0
        var maxConfidence = Float.NEGATIVE_INFINITY
        val coordinateMax = if (recordsFirst) {
            rawDetections.maxOf { values ->
                values.take(4).maxOf { kotlin.math.abs(it) }
            }
        } else {
            (0 until rowCount).maxOf { index ->
                (0 until 4).maxOf { valueIndex ->
                    kotlin.math.abs(rawDetections[valueIndex][index])
                }
            }
        }
        val rawSample = (0 until minOf(3, rowCount)).joinToString { index ->
            val values = if (recordsFirst) {
                rawDetections[index]
            } else {
                FloatArray(recordSize) { valueIndex -> rawDetections[valueIndex][index] }
            }
            values.joinToString(prefix = "[", postfix = "]")
        }

        for (i in 0 until rowCount) {
            val raw = if (recordsFirst) {
                rawDetections[i]
            } else {
                FloatArray(recordSize) { valueIndex -> rawDetections[valueIndex][i] }
            }
            val confidence = maxOf(raw[4], raw[5])

            // Detector candidates at the configured threshold are eligible.
            if (confidence.isFinite()) {
                finiteCount++
                maxConfidence = maxOf(maxConfidence, confidence)
            }
            if (confidence < threshold || !confidence.isFinite()) {
                continue
            }
            thresholdCount++

            val decoded = DetectorBoxDecoder.decode(
                raw = raw,
                layout = outputLayout,
                coordinatesAreNormalized = coordinateMax <= 1.5f,
                inputSize = inputSize,
                padX = padX,
                padY = padY,
                scale = scale,
                originalWidth = origW,
                originalHeight = origH
            ) ?: continue
            supportedClassCount++
            validBoxCount++
            detections += Detection(
                boundingBox = decoded.boundingBox,
                confidence = decoded.confidence,
                classId = decoded.classId
            )
        }

        inferenceCount++
        if (inferenceCount == 1L || inferenceCount % 30L == 0L) {
            Log.i(
                tag,
                "Detector frame=$inferenceCount input=${inputSize}x${inputSize} " +
                    "output=${outputShape.contentToString()} layout=$outputLayout " +
                    "rawSample=$rawSample " +
                    "raw=$rowCount finite=$finiteCount threshold=$thresholdCount " +
                    "classes=$supportedClassCount boxes=$validBoxCount retained=${detections.size} " +
                    "maxConfidence=$maxConfidence normalizedCoords=${coordinateMax <= 1.5f} " +
                    "inferenceMs=${SystemClock.elapsedRealtime() - inferenceStartMs}"
            )
        }
        return DetectionNms.apply(detections)
    }

    private fun getOrCreateBuffer(size: Int, isNchw: Boolean): ByteBuffer {
        if (inputBuffer == null || lastInputSize != size || lastNchw != isNchw) {
            val numBytes = 1 * 3 * size * size * 4 // float32 = 4 bytes
            inputBuffer = ByteBuffer.allocateDirect(numBytes).apply {
                order(ByteOrder.nativeOrder())
            }
            lastInputSize = size
            lastNchw = isNchw
        }
        return inputBuffer!!
    }
}
