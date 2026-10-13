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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.os.SystemClock
import java.util.Locale
import kotlin.math.sin

object InspectionResultAnnouncement {
    const val PASS = "Pass"
    const val FAIL = "Fail"
}

class AudioAlertManager(private val context: Context) : TextToSpeech.OnInitListener {

    private val tag = "AudioAlertManager"
    private val scope = CoroutineScope(Dispatchers.Default)
    private var alertJob: Job? = null
    private val failureBeepLoop = FailureBeepLoopState()

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
        failureBeepLoop.stop()
        alertJob = scope.launch {
            if (settings.voiceEnabled && isTtsReady) {
                speakText(InspectionResultAnnouncement.PASS)
            } else if (settings.passAudioEnabled) {
                playBuzzerPattern(freqHz = 1600, count = 1, gapMs = 50, volume = settings.buzzerVolume)
            }
        }
    }

    fun playFailAlert(
        settings: InspectionSettings,
        repeatDurationMs: Long = 0L
    ) {
        alertJob?.cancel()
        failureBeepLoop.stop()
        val loopStarted = repeatDurationMs > 0L && settings.buzzerEnabled &&
            failureBeepLoop.start()
        alertJob = scope.launch {
            try {
                triggerVibration()

                if (settings.voiceEnabled && isTtsReady) {
                    speakText(InspectionResultAnnouncement.FAIL)
                }

                if (settings.buzzerEnabled) {
                    if (loopStarted) {
                        repeatFailureBuzzer(settings, repeatDurationMs)
                    } else {
                        playBuzzerPattern(
                            freqHz = settings.buzzerFrequencyHz,
                            count = settings.beepCount,
                            gapMs = settings.beepGapMs,
                            volume = settings.buzzerVolume
                        )
                    }
                }
            } finally {
                if (loopStarted) {
                    failureBeepLoop.stop()
                }
            }
        }
    }

    private suspend fun repeatFailureBuzzer(
        settings: InspectionSettings,
        repeatDurationMs: Long
    ) {
        val deadlineMs = SystemClock.elapsedRealtime() + repeatDurationMs
        while (SystemClock.elapsedRealtime() < deadlineMs) {
            playBuzzerPattern(
                freqHz = settings.buzzerFrequencyHz,
                count = settings.beepCount,
                gapMs = settings.beepGapMs,
                volume = settings.buzzerVolume
            )
            delay(1L)
        }
    }

    fun testBuzzer(settings: InspectionSettings) {
        alertJob?.cancel()
        failureBeepLoop.stop()
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
            speakText(InspectionResultAnnouncement.PASS)
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
        } catch (e: CancellationException) {
            throw e
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
        failureBeepLoop.stop()
        tts?.stop()
    }

    fun release() {
        cancel()
        try {
            tts?.shutdown()
        } catch (ignored: Exception) {}
    }
}
