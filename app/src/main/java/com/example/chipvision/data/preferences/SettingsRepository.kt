package com.example.chipvision.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "chipvision_settings")

data class InspectionSettings(
    val detectorThreshold: Float = 0.70f,
    val classifierThreshold: Float = 0.70f,
    val stabilityDurationMs: Long = DEFAULT_STABILITY_DURATION_MS,
    val maxWidthChangeRatio: Float = 0.20f,
    val maxHeightChangeRatio: Float = 0.20f,
    val minStableBoxIou: Float = 0.80f,
    val movementCenterToleranceRatio: Float = 0.10f,
    val movementIouTolerance: Float = 0.70f,
    val movementConfirmationFrames: Int = 2,
    val globalMotionSensitivity: Float = 0.08f,
    val trackingTimeoutMs: Long = 400L,
    val inspectionTimeoutMs: Long = 5000L,
    val showConfidenceLabels: Boolean = true,
    val buzzerEnabled: Boolean = true,
    val buzzerFrequencyHz: Int = 950, // 950, 1600, or 600
    val beepCount: Int = 2,
    val beepGapMs: Int = 100,
    val buzzerVolume: Float = 0.8f,
    val voiceEnabled: Boolean = true,
    val voiceLocale: String = "en-US",
    val passAudioEnabled: Boolean = false,
    val simulationMode: Boolean = false // Demo / synthetic test mode for verification without physical conveyor
) {
    companion object {
        const val DEFAULT_STABILITY_DURATION_MS = 200L
    }
}

class SettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val DETECTOR_THRESHOLD = floatPreferencesKey("detector_threshold")
        val CLASSIFIER_THRESHOLD = floatPreferencesKey("classifier_threshold")
        val STABILITY_DURATION_MS = longPreferencesKey("stability_duration_ms")
        val MAX_WIDTH_CHANGE_RATIO = floatPreferencesKey("max_width_change_ratio")
        val MAX_HEIGHT_CHANGE_RATIO = floatPreferencesKey("max_height_change_ratio")
        val MIN_STABLE_BOX_IOU = floatPreferencesKey("min_stable_box_iou")
        val MOVEMENT_CENTER_TOLERANCE_RATIO = floatPreferencesKey("movement_center_tolerance_ratio")
        val MOVEMENT_IOU_TOLERANCE = floatPreferencesKey("movement_iou_tolerance")
        val MOVEMENT_CONFIRMATION_FRAMES = intPreferencesKey("movement_confirmation_frames")
        val GLOBAL_MOTION_SENSITIVITY = floatPreferencesKey("global_motion_sensitivity")
        val TRACKING_TIMEOUT_MS = longPreferencesKey("tracking_timeout_ms")
        val INSPECTION_TIMEOUT_MS = longPreferencesKey("inspection_timeout_ms")
        val SHOW_CONFIDENCE_LABELS = booleanPreferencesKey("show_confidence_labels")
        val BUZZER_ENABLED = booleanPreferencesKey("buzzer_enabled")
        val BUZZER_FREQ_HZ = intPreferencesKey("buzzer_freq_hz")
        val BEEP_COUNT = intPreferencesKey("beep_count")
        val BEEP_GAP_MS = intPreferencesKey("beep_gap_ms")
        val BUZZER_VOLUME = floatPreferencesKey("buzzer_volume")
        val VOICE_ENABLED = booleanPreferencesKey("voice_enabled")
        val VOICE_LOCALE = stringPreferencesKey("voice_locale")
        val PASS_AUDIO_ENABLED = booleanPreferencesKey("pass_audio_enabled")
        val SIMULATION_MODE = booleanPreferencesKey("simulation_mode")
    }

    val settingsFlow: Flow<InspectionSettings> = context.dataStore.data.map { preferences ->
        InspectionSettings(
            detectorThreshold = preferences[PreferencesKeys.DETECTOR_THRESHOLD] ?: 0.70f,
            classifierThreshold = preferences[PreferencesKeys.CLASSIFIER_THRESHOLD] ?: 0.70f,
            // Stability is camera-only and fixed at the application default.
            // Legacy 500 ms/1500 ms values are not allowed to override it.
            stabilityDurationMs = InspectionSettings.DEFAULT_STABILITY_DURATION_MS,
            maxWidthChangeRatio = preferences[PreferencesKeys.MAX_WIDTH_CHANGE_RATIO] ?: 0.20f,
            maxHeightChangeRatio = preferences[PreferencesKeys.MAX_HEIGHT_CHANGE_RATIO] ?: 0.20f,
            minStableBoxIou = preferences[PreferencesKeys.MIN_STABLE_BOX_IOU] ?: 0.80f,
            movementCenterToleranceRatio = preferences[PreferencesKeys.MOVEMENT_CENTER_TOLERANCE_RATIO] ?: 0.10f,
            movementIouTolerance = preferences[PreferencesKeys.MOVEMENT_IOU_TOLERANCE] ?: 0.70f,
            movementConfirmationFrames = preferences[PreferencesKeys.MOVEMENT_CONFIRMATION_FRAMES] ?: 2,
            globalMotionSensitivity = preferences[PreferencesKeys.GLOBAL_MOTION_SENSITIVITY] ?: 0.08f,
            trackingTimeoutMs = preferences[PreferencesKeys.TRACKING_TIMEOUT_MS] ?: 400L,
            inspectionTimeoutMs = preferences[PreferencesKeys.INSPECTION_TIMEOUT_MS] ?: 5000L,
            showConfidenceLabels = preferences[PreferencesKeys.SHOW_CONFIDENCE_LABELS] ?: true,
            buzzerEnabled = preferences[PreferencesKeys.BUZZER_ENABLED] ?: true,
            buzzerFrequencyHz = preferences[PreferencesKeys.BUZZER_FREQ_HZ] ?: 950,
            beepCount = preferences[PreferencesKeys.BEEP_COUNT] ?: 2,
            beepGapMs = preferences[PreferencesKeys.BEEP_GAP_MS] ?: 100,
            buzzerVolume = preferences[PreferencesKeys.BUZZER_VOLUME] ?: 0.8f,
            voiceEnabled = preferences[PreferencesKeys.VOICE_ENABLED] ?: true,
            voiceLocale = preferences[PreferencesKeys.VOICE_LOCALE] ?: "en-US",
            passAudioEnabled = preferences[PreferencesKeys.PASS_AUDIO_ENABLED] ?: false,
            simulationMode = preferences[PreferencesKeys.SIMULATION_MODE] ?: false
        )
    }

    suspend fun updateSettings(transform: (InspectionSettings) -> InspectionSettings) {
        context.dataStore.edit { preferences ->
            val current = InspectionSettings(
                detectorThreshold = preferences[PreferencesKeys.DETECTOR_THRESHOLD] ?: 0.70f,
                classifierThreshold = preferences[PreferencesKeys.CLASSIFIER_THRESHOLD] ?: 0.70f,
                stabilityDurationMs = InspectionSettings.DEFAULT_STABILITY_DURATION_MS,
                maxWidthChangeRatio = preferences[PreferencesKeys.MAX_WIDTH_CHANGE_RATIO] ?: 0.20f,
                maxHeightChangeRatio = preferences[PreferencesKeys.MAX_HEIGHT_CHANGE_RATIO] ?: 0.20f,
                minStableBoxIou = preferences[PreferencesKeys.MIN_STABLE_BOX_IOU] ?: 0.80f,
                movementCenterToleranceRatio = preferences[PreferencesKeys.MOVEMENT_CENTER_TOLERANCE_RATIO] ?: 0.10f,
                movementIouTolerance = preferences[PreferencesKeys.MOVEMENT_IOU_TOLERANCE] ?: 0.70f,
                movementConfirmationFrames = preferences[PreferencesKeys.MOVEMENT_CONFIRMATION_FRAMES] ?: 2,
                globalMotionSensitivity = preferences[PreferencesKeys.GLOBAL_MOTION_SENSITIVITY] ?: 0.08f,
                trackingTimeoutMs = preferences[PreferencesKeys.TRACKING_TIMEOUT_MS] ?: 400L,
                inspectionTimeoutMs = preferences[PreferencesKeys.INSPECTION_TIMEOUT_MS] ?: 5000L,
                showConfidenceLabels = preferences[PreferencesKeys.SHOW_CONFIDENCE_LABELS] ?: true,
                buzzerEnabled = preferences[PreferencesKeys.BUZZER_ENABLED] ?: true,
                buzzerFrequencyHz = preferences[PreferencesKeys.BUZZER_FREQ_HZ] ?: 950,
                beepCount = preferences[PreferencesKeys.BEEP_COUNT] ?: 2,
                beepGapMs = preferences[PreferencesKeys.BEEP_GAP_MS] ?: 100,
                buzzerVolume = preferences[PreferencesKeys.BUZZER_VOLUME] ?: 0.8f,
                voiceEnabled = preferences[PreferencesKeys.VOICE_ENABLED] ?: true,
                voiceLocale = preferences[PreferencesKeys.VOICE_LOCALE] ?: "en-US",
                passAudioEnabled = preferences[PreferencesKeys.PASS_AUDIO_ENABLED] ?: false,
                simulationMode = preferences[PreferencesKeys.SIMULATION_MODE] ?: false
            )
            val updated = transform(current)
            preferences[PreferencesKeys.DETECTOR_THRESHOLD] = updated.detectorThreshold
            preferences[PreferencesKeys.CLASSIFIER_THRESHOLD] = updated.classifierThreshold
            preferences[PreferencesKeys.STABILITY_DURATION_MS] =
                InspectionSettings.DEFAULT_STABILITY_DURATION_MS
            preferences[PreferencesKeys.MAX_WIDTH_CHANGE_RATIO] = updated.maxWidthChangeRatio
            preferences[PreferencesKeys.MAX_HEIGHT_CHANGE_RATIO] = updated.maxHeightChangeRatio
            preferences[PreferencesKeys.MIN_STABLE_BOX_IOU] = updated.minStableBoxIou
            preferences[PreferencesKeys.MOVEMENT_CENTER_TOLERANCE_RATIO] = updated.movementCenterToleranceRatio
            preferences[PreferencesKeys.MOVEMENT_IOU_TOLERANCE] = updated.movementIouTolerance
            preferences[PreferencesKeys.MOVEMENT_CONFIRMATION_FRAMES] = updated.movementConfirmationFrames
            preferences[PreferencesKeys.GLOBAL_MOTION_SENSITIVITY] = updated.globalMotionSensitivity
            preferences[PreferencesKeys.TRACKING_TIMEOUT_MS] = updated.trackingTimeoutMs
            preferences[PreferencesKeys.INSPECTION_TIMEOUT_MS] = updated.inspectionTimeoutMs
            preferences[PreferencesKeys.SHOW_CONFIDENCE_LABELS] = updated.showConfidenceLabels
            preferences[PreferencesKeys.BUZZER_ENABLED] = updated.buzzerEnabled
            preferences[PreferencesKeys.BUZZER_FREQ_HZ] = updated.buzzerFrequencyHz
            preferences[PreferencesKeys.BEEP_COUNT] = updated.beepCount
            preferences[PreferencesKeys.BEEP_GAP_MS] = updated.beepGapMs
            preferences[PreferencesKeys.BUZZER_VOLUME] = updated.buzzerVolume
            preferences[PreferencesKeys.VOICE_ENABLED] = updated.voiceEnabled
            preferences[PreferencesKeys.VOICE_LOCALE] = updated.voiceLocale
            preferences[PreferencesKeys.PASS_AUDIO_ENABLED] = updated.passAudioEnabled
            preferences[PreferencesKeys.SIMULATION_MODE] = updated.simulationMode
        }
    }
}
