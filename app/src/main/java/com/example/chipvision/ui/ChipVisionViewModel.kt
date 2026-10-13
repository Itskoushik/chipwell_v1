package com.example.chipvision.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chipvision.audio.AudioAlertManager
import com.example.chipvision.data.database.InspectionDatabase
import com.example.chipvision.data.database.InspectionRecord
import com.example.chipvision.data.preferences.InspectionSettings
import com.example.chipvision.data.preferences.SettingsRepository
import com.example.chipvision.engine.InspectionCoordinator
import com.example.chipvision.inference.ClassifierService
import com.example.chipvision.inference.DetectorService
import com.example.chipvision.inference.ModelManager
import com.example.chipvision.tracking.DetectionTracker
import com.example.chipvision.tracking.StabilityValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AppScreen {
    HOME,
    INSPECTION,
    HISTORY,
    SETTINGS,
    DIAGNOSTICS
}

// Kept for backward compatibility
enum class MainNavTab {
    INSPECTION,
    HISTORY,
    SETTINGS
}

class ChipVisionViewModel(application: Application) : AndroidViewModel(application) {

    private val db = InspectionDatabase.getInstance(application)
    private val inspectionDao = db.inspectionDao()
    private val settingsRepo = SettingsRepository(application)
    private val audioAlertManager = AudioAlertManager(application)

    private val modelManager = ModelManager(application)
    private val detectorService = DetectorService(modelManager)
    private val classifierService = ClassifierService(modelManager)
    private val tracker = DetectionTracker()
    private val stabilityValidator = StabilityValidator()

    val settings: StateFlow<InspectionSettings> = settingsRepo.settingsFlow.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        InspectionSettings()
    )

    private val coordinator = InspectionCoordinator(
        modelManager = modelManager,
        detectorService = detectorService,
        classifierService = classifierService,
        tracker = tracker,
        stabilityValidator = stabilityValidator,
        audioAlertManager = audioAlertManager,
        inspectionDao = inspectionDao,
        scope = viewModelScope
    )

    val status = coordinator.status
    val statusMessage = coordinator.statusMessage
    val visibleCandidates = coordinator.visibleCandidates
    val currentDecision = coordinator.currentDecision
    val cumulativeStats = coordinator.cumulativeStats
    val currentFrameStats = coordinator.currentFrameStats
    private val _cameraFrameSize = MutableStateFlow(0 to 0)
    val cameraFrameSize: StateFlow<Pair<Int, Int>> = _cameraFrameSize.asStateFlow()
    val diagnostics = coordinator.diagnostics
    val performance = coordinator.performance
    val duplicateEvidence = coordinator.duplicateEvidence
    val isStopped = coordinator.isStopped

    val historyRecords: StateFlow<List<InspectionRecord>> = inspectionDao.getAllRecords().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // Dedicated navigation state - defaults strictly to HOME
    private val _currentScreen = MutableStateFlow(AppScreen.HOME)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // Reloading indicator and feedback message for Retry button
    private val _isReloadingModels = MutableStateFlow(false)
    val isReloadingModels: StateFlow<Boolean> = _isReloadingModels.asStateFlow()

    private val _lastReloadFeedback = MutableStateFlow<String?>(null)
    val lastReloadFeedback: StateFlow<String?> = _lastReloadFeedback.asStateFlow()

    // Legacy tab flow for compatibility
    val currentTab: StateFlow<MainNavTab> = MutableStateFlow(MainNavTab.INSPECTION)

    sealed class InspectionStartResult {
        object Ready : InspectionStartResult()
        object MissingCameraPermission : InspectionStartResult()
        data class ModelsNotReady(val reason: String) : InspectionStartResult()
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            coordinator.initialize()
        }
    }

    fun isSystemReady(): Boolean {
        val sim = settings.value.simulationMode
        val diag = coordinator.diagnostics.value
        return sim || (diag.detectorLoaded && diag.classifierLoaded)
    }

    fun checkInspectionPrerequisites(hasCameraPermission: Boolean): InspectionStartResult {
        if (!hasCameraPermission) {
            return InspectionStartResult.MissingCameraPermission
        }
        val sim = settings.value.simulationMode
        val diag = coordinator.diagnostics.value
        if (sim || (diag.detectorLoaded && diag.classifierLoaded)) {
            return InspectionStartResult.Ready
        }
        val missing = mutableListOf<String>()
        if (!diag.detectorLoaded) missing.add("Detector model (${com.example.chipvision.inference.ModelManager.DETECTOR_FILE_NAME})")
        if (!diag.classifierLoaded) missing.add("Classifier model (${com.example.chipvision.inference.ModelManager.CLASSIFIER_FILE_NAME})")
        return InspectionStartResult.ModelsNotReady(
            "Cannot start inspection: Neural network models are not loaded into memory (${missing.joinToString(", ")}).\n\n" +
            "Please ensure the model files are located in assets, or enable Simulation Mode in Settings for testing."
        )
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun selectTab(tab: MainNavTab) {
        when (tab) {
            MainNavTab.INSPECTION -> navigateTo(AppScreen.INSPECTION)
            MainNavTab.HISTORY -> navigateTo(AppScreen.HISTORY)
            MainNavTab.SETTINGS -> navigateTo(AppScreen.SETTINGS)
        }
    }

    /**
     * Executes asynchronous re-scan and reload of TFLite models on Dispatchers.IO,
     * updating state and feedback message to eliminate frozen or silent retry behavior.
     */
    fun retryModelLoading() {
        if (_isReloadingModels.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isReloadingModels.value = true
            try {
                coordinator.retryModelLoading()
                val diag = coordinator.diagnostics.value
                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

                val feedback = if (diag.allModelsLoaded) {
                    "✓ Both models successfully verified and loaded ($timeStr)."
                } else {
                    val issues = mutableListOf<String>()
                    if (!diag.detector.isLoaded) issues.add("Detector: ${diag.detector.error ?: "failed"}")
                    if (!diag.classifier.isLoaded) issues.add("Classifier: ${diag.classifier.error ?: "failed"}")
                    "Scan completed at $timeStr: ${issues.joinToString("; ")}"
                }
                _lastReloadFeedback.value = feedback
            } catch (e: Exception) {
                _lastReloadFeedback.value = "Error during reload: ${e.message}"
            } finally {
                _isReloadingModels.value = false
            }
        }
    }

    fun onCameraFrame(bitmap: Bitmap, onFrameHandled: () -> Unit) {
        try {
            _cameraFrameSize.value = bitmap.width to bitmap.height
            coordinator.processFrame(bitmap, settings.value)
        } finally {
            onFrameHandled()
        }
    }

    fun clearCameraFrameSize() {
        _cameraFrameSize.value = 0 to 0
    }

    fun startInspection() {
        coordinator.startSession()
    }

    fun stopInspection() {
        coordinator.stopSession()
    }

    fun setDuplicateDiagnosticsSampling(sampleEveryNFrames: Long) {
        coordinator.setDuplicateDiagnosticsSampling(sampleEveryNFrames)
    }

    fun resumeInspection() {
        coordinator.resumeSession()
    }

    fun clearStatistics() {
        coordinator.clearStatistics()
    }

    fun clearHistory() {
        viewModelScope.launch {
            inspectionDao.clearAll()
        }
    }

    fun updateSettings(transform: (InspectionSettings) -> InspectionSettings) {
        viewModelScope.launch {
            settingsRepo.updateSettings(transform)
        }
    }

    fun testBuzzer() {
        audioAlertManager.testBuzzer(settings.value)
    }

    fun testVoice() {
        audioAlertManager.testVoice()
    }

    override fun onCleared() {
        super.onCleared()
        audioAlertManager.release()
        modelManager.close()
    }
}
