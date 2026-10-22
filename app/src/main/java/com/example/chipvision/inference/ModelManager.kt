package com.example.chipvision.inference

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

data class TensorInfo(val shape: String = "Unknown", val dataType: String = "Unknown", val scale: Float = 0f, val zeroPoint: Int = 0)
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
    val isNchw: Boolean = false,
    val inputSize: Int = 0,
    val sha256: String? = null
)
data class ModelDiagnostics(
    val model: ModelStatusInfo = ModelStatusInfo(ModelManager.MODEL_FILE_NAME),
    val lastCheckTimestampMs: Long = System.currentTimeMillis()
) {
    val modelLoaded get() = model.isLoaded
}

class ModelManager(private val context: Context) {
    companion object {
        private const val TAG = "CHIPWELL_MODEL"
        const val MODEL_FILE_NAME = "chipwell_yolo26n.tflite"
        const val OUTPUT_CANDIDATE_COUNT = 5376
    }

    @Volatile var detectorInterpreter: Interpreter? = null
        private set
    var diagnostics: ModelDiagnostics = ModelDiagnostics()
        private set
    var isDetectorNchw: Boolean = false
        private set
    var detectorInputSize: Int = 512
        private set

    @Synchronized fun initialize(): ModelDiagnostics {
        close()
        val status = try {
            val descriptor = context.assets.openFd(MODEL_FILE_NAME)
            val size = descriptor.declaredLength
            val buffer = FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                channel.map(FileChannel.MapMode.READ_ONLY, descriptor.startOffset, size).order(ByteOrder.nativeOrder())
            }
            descriptor.close()
            require(size > 20) { "TFLite asset is empty or truncated" }
            require(buffer.get(4) == 'T'.code.toByte() && buffer.get(5) == 'F'.code.toByte() && buffer.get(6) == 'L'.code.toByte() && buffer.get(7) == '3'.code.toByte()) { "Invalid TFLite FlatBuffer identifier" }
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hashBuffer = buffer.duplicate()
            val chunk = ByteArray(64 * 1024)
            while (hashBuffer.hasRemaining()) {
                val count = minOf(hashBuffer.remaining(), chunk.size)
                hashBuffer.get(chunk, 0, count)
                digest.update(chunk, 0, count)
            }
            val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
            val interpreter = Interpreter(buffer, Interpreter.Options().setNumThreads(4))
            try {
                require(interpreter.inputTensorCount == 1 && interpreter.outputTensorCount >= 1) { "Expected one input and at least one output tensor" }
                val input = interpreter.getInputTensor(0)
                val shape = input.shape()
                require(shape.size == 4 && input.dataType().name == "FLOAT32") { "Unsupported input tensor ${shape.contentToString()} ${input.dataType()}" }
                val nchw = shape[1] == 3 && shape[2] == shape[3]
                val inputSize = when {
                    nchw -> shape[2]
                    shape[3] == 3 && shape[1] == shape[2] -> shape[1]
                    else -> error("Unsupported image input tensor shape ${shape.contentToString()}")
                }
                require(inputSize == 512 && (shape.contentEquals(intArrayOf(1, 3, 512, 512)) || shape.contentEquals(intArrayOf(1, 512, 512, 3)))) {
                    "Expected float32 512x512 RGB input, got ${shape.contentToString()}"
                }
                val output = interpreter.getOutputTensor(0)
                require(output.dataType().name == "FLOAT32") { "Unsupported output tensor type ${output.dataType()}" }
                val inputQuant = input.quantizationParams()
                val outputQuant = output.quantizationParams()
                require(inputQuant.scale.isFinite() && inputQuant.scale >= 0f && outputQuant.scale.isFinite() && outputQuant.scale >= 0f) {
                    "Invalid tensor quantization scales: input=${inputQuant.scale}, output=${outputQuant.scale}"
                }
                val outputShape = output.shape()
                require(outputShape.contentEquals(intArrayOf(1, 6, OUTPUT_CANDIDATE_COUNT)) ||
                    outputShape.contentEquals(intArrayOf(1, OUTPUT_CANDIDATE_COUNT, 6))) {
                    "Unsupported model output; expected [1,6,$OUTPUT_CANDIDATE_COUNT] dense YOLO candidates, got ${outputShape.contentToString()}"
                }
                detectorInterpreter = interpreter
                isDetectorNchw = nchw
                detectorInputSize = inputSize
                ModelStatusInfo(
                    fileName = MODEL_FILE_NAME, isLoaded = true, fileSizeBytes = size,
                    resolvedLocation = "assets/$MODEL_FILE_NAME",
                    inputTensor = TensorInfo(shape.contentToString(), input.dataType().name, inputQuant.scale, inputQuant.zeroPoint),
                    outputTensor = TensorInfo(outputShape.contentToString(), output.dataType().name, outputQuant.scale, outputQuant.zeroPoint),
                    isNchw = nchw, inputSize = inputSize, sha256 = sha256
                )
            } catch (e: Exception) {
                interpreter.close()
                throw e
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to load $MODEL_FILE_NAME", e)
            ModelStatusInfo(fileName = MODEL_FILE_NAME, isMissing = e is java.io.FileNotFoundException, error = e.message ?: e.javaClass.simpleName)
        }
        diagnostics = ModelDiagnostics(status)
        Log.i(TAG, "${status.fileName} loaded=${status.isLoaded} sha256=${status.sha256} input=${status.inputTensor.shape} output=${status.outputTensor.shape} error=${status.error}")
        return diagnostics
    }

    @Synchronized fun close() {
        detectorInterpreter?.close()
        detectorInterpreter = null
    }
}
