package com.example.chipvision.inference

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import com.example.chipvision.data.model.Detection
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DetectorService(private val modelManager: ModelManager) {

    private val tag = "DetectorService"

    // Reusable buffers to avoid GC allocations during high FPS continuous video
    private var inputBuffer: ByteBuffer? = null
    private var lastInputSize: Int = 0
    private var lastNchw: Boolean = false

    private val outputArray = Array(1) { Array(300) { FloatArray(6) } }

    /**
     * Runs detection on the supplied RGB Bitmap.
     * Maps coordinates back to the original image dimensions [origWidth x origHeight].
     */
    @Synchronized
    fun detect(bitmap: Bitmap, threshold: Float = 0.85f): List<Detection> {
        val interpreter = modelManager.detectorInterpreter ?: return emptyList()

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
        try {
            interpreter.run(byteBuffer, outputArray)
        } catch (e: Exception) {
            Log.e(tag, "Detector inference failed", e)
            return emptyList()
        }

        val detections = mutableListOf<Detection>()
        val rawDetections = outputArray[0]

        for (i in 0 until 300) {
            val raw = rawDetections[i]
            var x1 = raw[0]
            var y1 = raw[1]
            var x2 = raw[2]
            var y2 = raw[3]
            val confidence = raw[4]
            val classIdRaw = raw[5]

            // Strict threshold rule: confidence > threshold (reject if <= threshold)
            if (confidence <= threshold || !confidence.isFinite() || confidence > 1.0f) {
                continue
            }

            val classId = classIdRaw.toInt()
            if (classId != 0 && classId != 1) {
                // Reject invalid classId
                continue
            }

            if (!x1.isFinite() || !y1.isFinite() || !x2.isFinite() || !y2.isFinite()) {
                continue
            }

            // Determine if coordinates are in 512x512 space or 0..1 space
            if (x2 > 1.5f || y2 > 1.5f || x1 > 1.5f || y1 > 1.5f) {
                // Coordinates are in inputSize x inputSize pixels
                // Unpad and scale back to original bitmap dimensions
                x1 = (x1 - padX) / scale
                y1 = (y1 - padY) / scale
                x2 = (x2 - padX) / scale
                y2 = (y2 - padY) / scale
            } else {
                // Coordinates are normalized to inputSize
                val px1 = x1 * inputSize
                val py1 = y1 * inputSize
                val px2 = x2 * inputSize
                val py2 = y2 * inputSize

                x1 = (px1 - padX) / scale
                y1 = (py1 - padY) / scale
                x2 = (px2 - padX) / scale
                y2 = (py2 - padY) / scale
            }

            // Order coords correctly
            val left = minOf(x1, x2).coerceIn(0f, origW)
            val top = minOf(y1, y2).coerceIn(0f, origH)
            val right = maxOf(x1, x2).coerceIn(0f, origW)
            val bottom = maxOf(y1, y2).coerceIn(0f, origH)

            // Reject zero-area or inverted boxes
            if (right <= left || bottom <= top) {
                continue
            }

            // Reject degenerate tiny boxes (e.g. less than 4x4 pixels)
            if ((right - left) < 4f || (bottom - top) < 4f) {
                continue
            }

            detections.add(
                Detection(
                    boundingBox = RectF(left, top, right, bottom),
                    confidence = confidence,
                    classId = classId
                )
            )
        }

        return detections
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
