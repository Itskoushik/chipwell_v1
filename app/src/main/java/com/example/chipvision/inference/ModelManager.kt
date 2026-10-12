package com.example.chipvision.inference

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

data class TensorInfo(
    val shape: String = "Unknown",
    val dataType: String = "Unknown",
    val scale: Float = 0f,
    val zeroPoint: Int = 0
)

data class ModelStatusInfo(
    val fileName: String,
    val isLoaded: Boolean = false,
    val isMissing: Boolean = false,
    val isEmptyFile: Boolean = false,
    val fileSizeBytes: Long = 0L,
    val resolvedLocation: String = "None",
    val error: String? = null,
    val inputTensor: TensorInfo = TensorInfo(),
    val outputTensor: TensorInfo = TensorInfo(),
    val isNchw: Boolean = true,
    val inputSize: Int = 0
)

data class ModelDiagnostics(
    val detector: ModelStatusInfo = ModelStatusInfo(fileName = "chipvision_detector_direct.tflite"),
    val classifier: ModelStatusInfo = ModelStatusInfo(fileName = "chipvision_classifier.tflite"),
    val allModelsLoaded: Boolean = false,
    val lastCheckTimestampMs: Long = System.currentTimeMillis()
) {
    val detectorFileName: String get() = detector.fileName
    val detectorLoaded: Boolean get() = detector.isLoaded
    val detectorError: String? get() = detector.error
    val detectorInputShape: String get() = detector.inputTensor.shape
    val detectorOutputShape: String get() = detector.outputTensor.shape

    val classifierFileName: String get() = classifier.fileName
    val classifierLoaded: Boolean get() = classifier.isLoaded
    val classifierError: String? get() = classifier.error
    val classifierInputShape: String get() = classifier.inputTensor.shape
    val classifierOutputShape: String get() = classifier.outputTensor.shape
}

sealed class ModelSource {
    data class Asset(val assetPath: String) : ModelSource()
    data class DiskFile(val file: File) : ModelSource()
}

class ModelManager(private val context: Context) {

    companion object {
        private const val TAG_MODEL = "CHIPVISION_MODEL"
        private const val TAG_DETECTOR = "CHIPVISION_DETECTOR"
        private const val TAG_CLASSIFIER = "CHIPVISION_CLASSIFIER"

        const val DETECTOR_FILE_NAME = "chipvision_detector_direct.tflite"
        const val CLASSIFIER_FILE_NAME = "chipvision_classifier.tflite"
    }

    var detectorInterpreter: Interpreter? = null
        private set

    var classifierInterpreter: Interpreter? = null
        private set

    var diagnostics: ModelDiagnostics = ModelDiagnostics()
        private set

    var isDetectorNchw: Boolean = true
        private set
    var detectorInputSize: Int = 512
        private set

    var isClassifierNchw: Boolean = true
        private set
    var classifierInputSize: Int = 224
        private set

    @Synchronized
    fun initialize(): ModelDiagnostics {
        Log.i(TAG_MODEL, "Starting model initialization check...")

        // Close any previously opened interpreters before re-initializing
        close()

        val detectorStatus = inspectAndLoadModel(
            fileName = DETECTOR_FILE_NAME,
            tag = TAG_DETECTOR,
            defaultInputDim = 512,
            isDetector = true
        )

        val classifierStatus = inspectAndLoadModel(
            fileName = CLASSIFIER_FILE_NAME,
            tag = TAG_CLASSIFIER,
            defaultInputDim = 224,
            isDetector = false
        )

        val allReady = detectorStatus.isLoaded && classifierStatus.isLoaded

        diagnostics = ModelDiagnostics(
            detector = detectorStatus,
            classifier = classifierStatus,
            allModelsLoaded = allReady,
            lastCheckTimestampMs = System.currentTimeMillis()
        )

        Log.i(TAG_MODEL, "Model initialization completed. Detector loaded=${detectorStatus.isLoaded}, Classifier loaded=${classifierStatus.isLoaded}, All ready=$allReady")
        return diagnostics
    }

    private fun inspectAndLoadModel(
        fileName: String,
        tag: String,
        defaultInputDim: Int,
        isDetector: Boolean
    ): ModelStatusInfo {
        Log.i(tag, "Inspecting model binary for: $fileName")

        // Multi-source locator: checks assets/, assets/models/, internal filesDir, and external filesDir
        val source = resolveModelSource(fileName)
        if (source == null) {
            val errMsg = "File missing. Expected at: app/src/main/assets/$fileName"
            Log.w(tag, errMsg)
            return ModelStatusInfo(
                fileName = fileName,
                isMissing = true,
                fileSizeBytes = 0L,
                resolvedLocation = "None",
                error = errMsg
            )
        }

        val locationDesc = when (source) {
            is ModelSource.Asset -> "assets/${source.assetPath}"
            is ModelSource.DiskFile -> source.file.absolutePath
        }

        // Check byte size
        val size = getSourceByteLength(source)
        if (size == 0L) {
            val errMsg = "Model file is empty (0 bytes) at $locationDesc."
            Log.w(tag, errMsg)
            return ModelStatusInfo(
                fileName = fileName,
                isEmptyFile = true,
                fileSizeBytes = 0L,
                resolvedLocation = locationDesc,
                error = errMsg
            )
        }

        if (size < 20L) {
            val errMsg = "File is too small ($size bytes) at $locationDesc to be a valid TFLite FlatBuffer."
            Log.w(tag, errMsg)
            return ModelStatusInfo(
                fileName = fileName,
                fileSizeBytes = size,
                resolvedLocation = locationDesc,
                error = errMsg
            )
        }

        // Read byte buffer and verify FlatBuffer "TFL3" identifier
        val buffer = try {
            loadSourceByteBuffer(source)
        } catch (e: Exception) {
            val errMsg = "Failed to read binary buffer from $locationDesc: ${e.message}"
            Log.w(tag, errMsg, e)
            return ModelStatusInfo(
                fileName = fileName,
                fileSizeBytes = size,
                resolvedLocation = locationDesc,
                error = errMsg
            )
        }

        val headerError = verifyFlatBufferIdentifier(buffer, fileName)
        if (headerError != null) {
            Log.w(tag, headerError)
            return ModelStatusInfo(
                fileName = fileName,
                fileSizeBytes = size,
                resolvedLocation = locationDesc,
                error = headerError
            )
        }

        // Initialize TensorFlow Lite Interpreter
        return try {
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            val interp = Interpreter(buffer, options)

            val inTensor = interp.getInputTensor(0)
            val inShape = inTensor.shape()
            val inShapeStr = inShape.contentToString()
            val inType = inTensor.dataType().name
            val inQuant = inTensor.quantizationParams()

            var nchw = true
            var inputDim = defaultInputDim
            if (inShape.size == 4) {
                if (inShape[1] == 3) {
                    nchw = true
                    inputDim = inShape[2]
                } else if (inShape[3] == 3) {
                    nchw = false
                    inputDim = inShape[1]
                }
            }

            if (isDetector) {
                detectorInterpreter = interp
                isDetectorNchw = nchw
                detectorInputSize = inputDim
            } else {
                classifierInterpreter = interp
                isClassifierNchw = nchw
                classifierInputSize = inputDim
            }

            val outTensor = interp.getOutputTensor(0)
            val outShapeStr = outTensor.shape().contentToString()
            val outType = outTensor.dataType().name
            val outQuant = outTensor.quantizationParams()

            Log.i(tag, "$fileName successfully loaded from $locationDesc. Size=$size bytes, Layout=${if (nchw) "NCHW" else "NHWC"}, InputDim=$inputDim, Input: $inShapeStr ($inType), Output: $outShapeStr ($outType)")

            ModelStatusInfo(
                fileName = fileName,
                isLoaded = true,
                fileSizeBytes = size,
                resolvedLocation = locationDesc,
                inputTensor = TensorInfo(inShapeStr, inType, inQuant.scale, inQuant.zeroPoint),
                outputTensor = TensorInfo(outShapeStr, outType, outQuant.scale, outQuant.zeroPoint),
                isNchw = nchw,
                inputSize = inputDim
            )
        } catch (e: Exception) {
            val errMsg = "Failed to initialize interpreter from $locationDesc: ${e.message ?: e.javaClass.simpleName}"
            Log.w(tag, errMsg, e)
            ModelStatusInfo(
                fileName = fileName,
                fileSizeBytes = size,
                resolvedLocation = locationDesc,
                error = errMsg
            )
        }
    }

    private fun resolveModelSource(fileName: String): ModelSource? {
        // 1. Direct assets/ root
        if (assetExists(fileName)) {
            return ModelSource.Asset(fileName)
        }
        // 2. Subdirectory assets/models/
        if (assetExists("models/$fileName")) {
            return ModelSource.Asset("models/$fileName")
        }
        // 3. Internal app files directory
        try {
            val f = File(context.filesDir, fileName)
            if (f.exists()) return ModelSource.DiskFile(f)
        } catch (ignored: Exception) {}

        // 4. External files directory
        try {
            val ext = context.getExternalFilesDir(null)
            if (ext != null) {
                val f = File(ext, fileName)
                if (f.exists()) return ModelSource.DiskFile(f)
            }
        } catch (ignored: Exception) {}

        return null
    }

    fun verifyFlatBufferIdentifier(buffer: ByteBuffer, fileName: String): String? {
        if (buffer.capacity() < 8) {
            return "File $fileName is too small to contain FlatBuffer header."
        }
        val b4 = buffer.get(4)
        val b5 = buffer.get(5)
        val b6 = buffer.get(6)
        val b7 = buffer.get(7)

        val isTFL3 = (b4 == 'T'.code.toByte() &&
                      b5 == 'F'.code.toByte() &&
                      b6 == 'L'.code.toByte() &&
                      b7 == '3'.code.toByte())

        if (!isTFL3) {
            val idString = try {
                String(byteArrayOf(b4, b5, b6, b7), Charsets.US_ASCII)
            } catch (e: Exception) {
                "unknown"
            }
            return "Invalid FlatBuffer identifier '$idString' at offset 4 (expected 'TFL3'). File is not a valid TensorFlow Lite model."
        }
        return null
    }

    private fun assetExists(path: String): Boolean {
        return try {
            val stream = context.assets.open(path)
            stream.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun getSourceByteLength(source: ModelSource): Long {
        return when (source) {
            is ModelSource.DiskFile -> source.file.length()
            is ModelSource.Asset -> {
                try {
                    val stream: InputStream = context.assets.open(source.assetPath)
                    var count = 0L
                    val buf = ByteArray(8192)
                    var r: Int
                    while (stream.read(buf).also { r = it } != -1) {
                        count += r
                    }
                    stream.close()
                    count
                } catch (e: Exception) {
                    0L
                }
            }
        }
    }

    private fun loadSourceByteBuffer(source: ModelSource): ByteBuffer {
        return when (source) {
            is ModelSource.DiskFile -> {
                val fis = FileInputStream(source.file)
                val channel = fis.channel
                val buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
                fis.close()
                buffer
            }
            is ModelSource.Asset -> {
                try {
                    val fd: AssetFileDescriptor = context.assets.openFd(source.assetPath)
                    val inputStream = FileInputStream(fd.fileDescriptor)
                    val fileChannel = inputStream.channel
                    val buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                    return buffer
                } catch (e: Exception) {
                    Log.i(TAG_MODEL, "openFd unmapped for ${source.assetPath} (${e.message}), reading via InputStream directly")
                }

                val inputStream = context.assets.open(source.assetPath)
                val bytes = inputStream.readBytes()
                inputStream.close()

                val buffer = ByteBuffer.allocateDirect(bytes.size)
                buffer.order(ByteOrder.nativeOrder())
                buffer.put(bytes)
                buffer.rewind()
                buffer
            }
        }
    }

    fun close() {
        try {
            detectorInterpreter?.close()
            detectorInterpreter = null
            classifierInterpreter?.close()
            classifierInterpreter = null
        } catch (e: Exception) {
            Log.w(TAG_MODEL, "Error releasing interpreters", e)
        }
    }
}
