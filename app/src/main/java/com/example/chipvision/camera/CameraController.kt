package com.example.chipvision.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class CameraController(
    private val context: Context,
    private val onFrameAvailable: (Bitmap) -> Unit
) {
    private val tag = "CameraController"
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val isProcessingFrame = AtomicBoolean(false)
    private var cameraProvider: ProcessCameraProvider? = null

    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onError: (String) -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    processImageProxy(imageProxy)
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
                Log.i(tag, "Camera started successfully")
            } catch (e: Exception) {
                Log.e(tag, "Failed to bind camera lifecycle", e)
                onError(e.message ?: "Camera binding failure")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        try {
            // Drop frame if previous inference is still busy to guarantee real-time performance and prevent backlog
            if (isProcessingFrame.compareAndSet(false, true)) {
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                val bitmap = imageProxyToBitmap(imageProxy, rotationDegrees)
                if (bitmap != null) {
                    onFrameAvailable(bitmap)
                } else {
                    isProcessingFrame.set(false)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error processing camera frame", e)
            isProcessingFrame.set(false)
        } finally {
            // CRITICAL: Close every imageProxy immediately so CameraX can feed the next frame
            imageProxy.close()
        }
    }

    fun notifyFrameProcessingFinished() {
        isProcessingFrame.set(false)
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy, rotationDegrees: Int): Bitmap? {
        val rawBitmap = try {
            imageProxy.toBitmap()
        } catch (e: Exception) {
            null
        } ?: return null

        if (rotationDegrees == 0) {
            return rawBitmap
        }

        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
        }
        val rotated = Bitmap.createBitmap(
            rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true
        )
        if (rotated != rawBitmap) {
            rawBitmap.recycle()
        }
        return rotated
    }

    fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.e(tag, "Error unbinding camera", e)
        }
    }

    fun release() {
        stopCamera()
        cameraExecutor.shutdown()
    }
}
