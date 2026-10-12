package com.example.chipvision.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.chipvision.data.preferences.InspectionSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.sin

class AudioAlertManager(private val context: Context) : TextToSpeech.OnInitListener {

    private val tag = "AudioAlertManager"
    private val scope = CoroutineScope(Dispatchers.Default)
    private var alertJob: Job? = null

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.w(tag, "TTS initialization error", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            isTtsReady = true
            Log.i(tag, "TTS initialized successfully")
        } else {
            isTtsReady = false
            Log.w(tag, "TTS initialization failed with status $status")
        }
    }

    fun playPassAlert(settings: InspectionSettings) {
        if (!settings.passAudioEnabled && !settings.voiceEnabled) return

        alertJob?.cancel()
        alertJob = scope.launch {
            if (settings.voiceEnabled && isTtsReady) {
                speakText("Pass")
            } else if (settings.passAudioEnabled) {
                playBuzzerPattern(freqHz = 1600, count = 1, gapMs = 50, volume = settings.buzzerVolume)
            }
        }
    }

    fun playFailAlert(message: String, settings: InspectionSettings) {
        alertJob?.cancel()
        alertJob = scope.launch {
            // Trigger haptic vibration for industrial feedback
            triggerVibration()

            // Buzzer alert
            if (settings.buzzerEnabled) {
                playBuzzerPattern(
                    freqHz = settings.buzzerFrequencyHz,
                    count = settings.beepCount,
                    gapMs = settings.beepGapMs,
                    volume = settings.buzzerVolume
                )
            }

            // Voice announcement
            if (settings.voiceEnabled && isTtsReady) {
                val spoken = when {
                    message.contains("empty impression", ignoreCase = true) -> "Fail. Empty impression detected."
                    message.contains("downward", ignoreCase = true) -> "Fail. Downward chip detected."
                    message.contains("flipped", ignoreCase = true) -> "Fail. Flipped chip detected."
                    else -> "Fail. Inspection rejected."
                }
                speakText(spoken)
            }
        }
    }

    fun testBuzzer(settings: InspectionSettings) {
        alertJob?.cancel()
        alertJob = scope.launch {
            playBuzzerPattern(
                freqHz = settings.buzzerFrequencyHz,
                count = settings.beepCount,
                gapMs = settings.beepGapMs,
                volume = settings.buzzerVolume
            )
        }
    }

    fun testVoice() {
        if (isTtsReady) {
            speakText("Pass")
        }
    }

    private fun speakText(text: String) {
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "chipvision_tts_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            Log.e(tag, "TTS speak failed", e)
        }
    }

    private suspend fun playBuzzerPattern(freqHz: Int, count: Int, gapMs: Int, volume: Float) {
        val sampleRate = 44100
        val durationMs = 120
        val numSamples = (durationMs * sampleRate) / 1000
        val generatedSnd = ShortArray(numSamples)

        val clampedVol = volume.coerceIn(0.05f, 1.0f)
        for (i in 0 until numSamples) {
            val angle = 2.0 * Math.PI * i / (sampleRate.toDouble() / freqHz)
            generatedSnd[i] = (sin(angle) * Short.MAX_VALUE * clampedVol).toInt().toShort()
        }

        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        var audioTrack: AudioTrack? = null
        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBufferSize, numSamples * 2))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack.play()

            for (b in 0 until count) {
                audioTrack.write(generatedSnd, 0, numSamples)
                if (b < count - 1) {
                    delay(gapMs.toLong())
                }
            }
            delay(100L)
        } catch (e: Exception) {
            Log.e(tag, "Error playing audio tone", e)
        } finally {
            try {
                audioTrack?.stop()
                audioTrack?.release()
            } catch (ignored: Exception) {}
        }
    }

    private fun triggerVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(250)
            }
        } catch (ignored: Exception) {}
    }

    fun cancel() {
        alertJob?.cancel()
        tts?.stop()
    }

    fun release() {
        cancel()
        try {
            tts?.shutdown()
        } catch (ignored: Exception) {}
    }
}
