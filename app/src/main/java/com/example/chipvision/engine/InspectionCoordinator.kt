package com.example.chipvision.engine

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.example.chipvision.audio.AudioAlertManager
import com.example.chipvision.data.database.InspectionDao
import com.example.chipvision.data.database.InspectionRecord
import com.example.chipvision.data.model.CumulativeStats
import com.example.chipvision.data.model.CurrentFrameStats
import com.example.chipvision.data.model.Detection
import com.example.chipvision.data.model.InspectionDecision
import com.example.chipvision.data.model.InspectionStatus
import com.example.chipvision.data.model.ReasonCode
import com.example.chipvision.data.model.TrackedCandidate
import com.example.chipvision.data.preferences.InspectionSettings
import com.example.chipvision.inference.ClassifierService
import com.example.chipvision.inference.DetectorService
import com.example.chipvision.inference.ModelDiagnostics
import com.example.chipvision.inference.ModelManager
import com.example.chipvision.tracking.DetectionTracker
import com.example.chipvision.tracking.StabilityValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class InspectionCoordinator(
    private val modelManager: ModelManager,
    private val detectorService: DetectorService,
    private val classifierService: ClassifierService,
    private val tracker: DetectionTracker,
    private val stabilityValidator: StabilityValidator,
    private val audioAlertManager: AudioAlertManager,
    private val inspectionDao: InspectionDao,
    private val scope: CoroutineScope
) {
    private val tag = "InspectionCoordinator"

    // UI state flows
    private val _status = MutableStateFlow(InspectionStatus.INITIALIZING)
    val status: StateFlow<InspectionStatus> = _status.asStateFlow()

    private val _statusMessage = MutableStateFlow("System Initializing...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    // Active visible candidates: UNSTABLE CANDIDATES ARE NEVER EXPOSED (Rule: Section 7)
    private val _visibleCandidates = MutableStateFlow<List<TrackedCandidate>>(emptyList())
    val visibleCandidates: StateFlow<List<TrackedCandidate>> = _visibleCandidates.asStateFlow()

    private val _currentDecision = MutableStateFlow<InspectionDecision?>(null)
    val currentDecision: StateFlow<InspectionDecision?> = _currentDecision.asStateFlow()

    private val _cumulativeStats = MutableStateFlow(CumulativeStats())
    val cumulativeStats: StateFlow<CumulativeStats> = _cumulativeStats.asStateFlow()

    private val _currentFrameStats = MutableStateFlow(CurrentFrameStats())
    val currentFrameStats: StateFlow<CurrentFrameStats> = _currentFrameStats.asStateFlow()

    private val _diagnostics = MutableStateFlow(ModelDiagnostics())
    val diagnostics: StateFlow<ModelDiagnostics> = _diagnostics.asStateFlow()

    private val _isStopped = MutableStateFlow(false)
    val isStopped: StateFlow<Boolean> = _isStopped.asStateFlow()

    // Session and snapshot identifiers
    private var currentSessionId: String = UUID.randomUUID().toString()
    private var currentSnapshotId: String = ""
    private var isInspectionFinalizedForCurrentScene = false
    private var lastFinalizedSceneCandidatesHash: Int = 0

    // Source frame retaining
    private var retainedSnapshotFrame: Bitmap? = null

    fun initialize() {
        val diag = modelManager.initialize()
        _diagnostics.value = diag

        if (diag.detectorLoaded && diag.classifierLoaded) {
            _status.value = InspectionStatus.READY
            _statusMessage.value = "Models Loaded. Monitoring live scene."
        } else {
            val errorParts = mutableListOf<String>()
            if (!diag.detectorLoaded) errorParts.add("Detector: ${diag.detectorError}")
            if (!diag.classifierLoaded) errorParts.add("Classifier: ${diag.classifierError}")
            _status.value = InspectionStatus.ERROR
            _statusMessage.value = "Model Error: ${errorParts.joinToString("; ")}"
        }
    }

    fun retryModelLoading() {
        initialize()
    }

    /**
     * Called continuously on each incoming camera frame.
     * The camera preview and detection NEVER pause during stability waiting or classification.
     */
    fun processFrame(bitmap: Bitmap, settings: InspectionSettings) {
        if (_isStopped.value) {
            bitmap.recycle()
            return
        }

        val currentTimeMs = SystemClock.elapsedRealtime()

        try {
            // Check if models are ready
            if (modelManager.detectorInterpreter == null || modelManager.classifierInterpreter == null) {
                if (!settings.simulationMode) {
                    _status.value = InspectionStatus.ERROR
                    _statusMessage.value = "Models not loaded. Place .tflite files in assets or enable Demo Mode."
                    _visibleCandidates.value = emptyList()
                    return
                }
            }

            // 1. Run Detector inference
            val rawDetections: List<Detection> = if (settings.simulationMode && modelManager.detectorInterpreter == null) {
                generateSimulationDetections(bitmap)
            } else {
                detectorService.detect(bitmap, threshold = settings.detectorThreshold)
            }

            // 2. Global scene motion detection
            val isSceneMoving = stabilityValidator.checkGlobalSceneMotion(bitmap, settings.globalMotionSensitivity)

            // 3. Update candidate tracking
            val allCandidates = tracker.updateTracks(
                detections = rawDetections,
                currentTimeMs = currentTimeMs,
                settings = settings,
                sessionId = currentSessionId
            )

            // Update current frame statistics
            updateFrameStats(allCandidates)

            // 4. Handle scene motion
            if (isSceneMoving) {
                // If the scene/conveyor is moving:
                tracker.invalidateAllStability(currentTimeMs)
                // Invalidate current scene finalization so new stable scene can be inspected
                isInspectionFinalizedForCurrentScene = false
                _status.value = InspectionStatus.MONITORING
                _statusMessage.value = "Conveyor motion detected. Waiting for rest."
                _visibleCandidates.value = emptyList() // HIDE UNSTABLE OVERLAYS COMPLETELY
                retainedSnapshotFrame?.recycle()
                retainedSnapshotFrame = null
                return
            }

            // 5. Evaluate candidate stability
            val stabilityResult = stabilityValidator.validateCandidatesStability(
                candidates = allCandidates,
                currentTimeMs = currentTimeMs,
                settings = settings
            )

            // CRITICAL: HIDE UNSTABLE CANDIDATES COMPLETELY (Section 7)
            // Only stable candidates are ever displayed on screen!
            _visibleCandidates.value = stabilityResult.stableCandidates

            if (allCandidates.isEmpty()) {
                if (!isInspectionFinalizedForCurrentScene) {
                    _status.value = InspectionStatus.MONITORING
                    _statusMessage.value = "Monitoring live conveyor scene"
                }
                return
            }

            // If not all candidates are stable yet
            if (!stabilityResult.isAllStable) {
                if (!isInspectionFinalizedForCurrentScene) {
                    _status.value = InspectionStatus.WAITING_FOR_STABILITY
                    _statusMessage.value = "Stabilizing candidates (1.5s active observation)..."
                }
                return
            }

            // If the scene has already been finalized and hasn't changed, don't re-run inspection
            val currentSceneHash = computeSceneHash(stabilityResult.stableCandidates)
            if (isInspectionFinalizedForCurrentScene && currentSceneHash == lastFinalizedSceneCandidatesHash) {
                // Scene remains stationary after finalization - keep displaying finalized result
                return
            }

            // 6. A FRESH STABLE SCENE IS ESTABLISHED (1.5 seconds satisfied)
            _status.value = InspectionStatus.STABLE_SCENE_READY
            _statusMessage.value = "Stable scene locked. Evaluating..."

            currentSnapshotId = UUID.randomUUID().toString()
            retainedSnapshotFrame?.recycle()
            retainedSnapshotFrame = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            val snapshotFrame = retainedSnapshotFrame ?: return

            // 7. EMPTY-IMPRESSION-FIRST CHECK (Section 9)
            if (stabilityResult.hasEmptyImpression) {
                _status.value = InspectionStatus.CHECKING_EMPTY_IMPRESSIONS
                finalizeInspection(
                    candidates = stabilityResult.stableCandidates,
                    settings = settings,
                    overrideDecision = InspectionDecisionEngine.evaluate(
                        candidates = stabilityResult.stableCandidates,
                        sessionId = currentSessionId,
                        snapshotId = currentSnapshotId
                    )
                )
                lastFinalizedSceneCandidatesHash = currentSceneHash
                return
            }

            // 8. CLASSIFY EVERY VALID CHIP (Section 10)
            _status.value = InspectionStatus.CLASSIFYING
            _statusMessage.value = "Classifying chips..."

            val classifiedCandidates = mutableListOf<TrackedCandidate>()
            for (chipCandidate in stabilityResult.stableCandidates) {
                if (chipCandidate.isChip) {
                    val classification = if (settings.simulationMode && modelManager.classifierInterpreter == null) {
                        simulateChipClassification(chipCandidate)
                    } else {
                        classifierService.classifyChip(
                            sourceFrame = snapshotFrame,
                            boundingBox = chipCandidate.boundingBox,
                            threshold = settings.classifierThreshold
                        )
                    }
                    tracker.updateClassification(chipCandidate.trackId, classification)
                    classifiedCandidates.add(chipCandidate.copy(classification = classification))
                } else {
                    classifiedCandidates.add(chipCandidate)
                }
            }

            // 9. CENTRALIZED DECISION (Section 11)
            _status.value = InspectionStatus.FINALIZING
            val finalDecision = InspectionDecisionEngine.evaluate(
                candidates = classifiedCandidates,
                sessionId = currentSessionId,
                snapshotId = currentSnapshotId
            )

            // Update visible candidates with new classifications
            _visibleCandidates.value = classifiedCandidates

            finalizeInspection(
                candidates = classifiedCandidates,
                settings = settings,
                overrideDecision = finalDecision
            )
            lastFinalizedSceneCandidatesHash = currentSceneHash

        } catch (e: Exception) {
            Log.e(tag, "Inspection coordinator exception", e)
            _status.value = InspectionStatus.ERROR
            _statusMessage.value = "Inspection Error: ${e.message}"
        } finally {
            bitmap.recycle()
        }
    }

    private fun finalizeInspection(
        candidates: List<TrackedCandidate>,
        settings: InspectionSettings,
        overrideDecision: InspectionDecision
    ) {
        _currentDecision.value = overrideDecision
        isInspectionFinalizedForCurrentScene = true

        if (overrideDecision.isPass) {
            _status.value = InspectionStatus.PASS
            _statusMessage.value = overrideDecision.message
            audioAlertManager.playPassAlert(settings)
        } else {
            _status.value = InspectionStatus.FAIL
            _statusMessage.value = overrideDecision.message
            audioAlertManager.playFailAlert(overrideDecision.message, settings)
        }

        // Update cumulative statistics
        updateCumulativeStats(overrideDecision, candidates)

        // Save record to local Room database
        scope.launch(Dispatchers.IO) {
            try {
                val record = InspectionRecord(
                    timestampMs = overrideDecision.timestampMs,
                    sessionId = overrideDecision.sessionId,
                    snapshotId = overrideDecision.snapshotId,
                    isPass = overrideDecision.isPass,
                    reasonCode = overrideDecision.reasonCode.code,
                    message = overrideDecision.message,
                    chipCount = overrideDecision.chipCount,
                    emptyCount = overrideDecision.emptyCount,
                    chipDetailsJson = overrideDecision.chips.joinToString(prefix = "[", postfix = "]") {
                        "{\"trackId\":${it.trackId},\"type\":\"${it.chipType.displayName}\",\"orient\":\"${it.orientation.displayName}\",\"conf\":${it.confidence}}"
                    }
                )
                inspectionDao.insertRecord(record)
            } catch (e: Exception) {
                Log.e(tag, "Failed to persist inspection record", e)
            }
        }
    }

    private fun updateFrameStats(candidates: List<TrackedCandidate>) {
        var chips = 0
        var classified = 0
        var upward = 0
        var downward = 0
        var flipped = 0
        var empty = 0

        for (c in candidates) {
            if (c.isChip) {
                chips++
                val cls = c.classification
                if (cls != null && cls.isAccepted) {
                    classified++
                    when (cls.orientation) {
                        com.example.chipvision.data.model.ChipOrientation.CORRECT_UPWARD -> upward++
                        com.example.chipvision.data.model.ChipOrientation.DOWNWARD -> downward++
                        com.example.chipvision.data.model.ChipOrientation.FLIPPED -> flipped++
                        else -> {}
                    }
                }
            } else if (c.isEmptyImpression) {
                empty++
            }
        }

        _currentFrameStats.value = CurrentFrameStats(
            detectedChips = chips,
            classifiedChips = classified,
            upwardChips = upward,
            downwardChips = downward,
            flippedChips = flipped,
            emptyImpressions = empty
        )
    }

    private fun updateCumulativeStats(decision: InspectionDecision, candidates: List<TrackedCandidate>) {
        val current = _cumulativeStats.value
        val isPass = decision.isPass
        val isErr = decision.reasonCode == ReasonCode.FAIL_INFERENCE_ERROR

        var newUpward = current.upwardChipsCount
        var newDownward = current.downwardChipsCount
        var newFlipped = current.flippedChipsCount
        var newClassified = current.totalChipsClassified
        val newEmpty = current.emptyImpressionsCount + decision.emptyCount

        for (c in candidates) {
            val cls = c.classification
            if (cls != null && cls.isAccepted) {
                newClassified++
                when (cls.orientation) {
                    com.example.chipvision.data.model.ChipOrientation.CORRECT_UPWARD -> newUpward++
                    com.example.chipvision.data.model.ChipOrientation.DOWNWARD -> newDownward++
                    com.example.chipvision.data.model.ChipOrientation.FLIPPED -> newFlipped++
                    else -> {}
                }
            }
        }

        _cumulativeStats.value = current.copy(
            totalInspections = current.totalInspections + 1,
            passCount = if (isPass) current.passCount + 1 else current.passCount,
            failCount = if (!isPass && !isErr) current.failCount + 1 else current.failCount,
            errorCount = if (isErr) current.errorCount + 1 else current.errorCount,
            totalChipsClassified = newClassified,
            upwardChipsCount = newUpward,
            downwardChipsCount = newDownward,
            flippedChipsCount = newFlipped,
            emptyImpressionsCount = newEmpty
        )
    }

    private fun computeSceneHash(candidates: List<TrackedCandidate>): Int {
        var result = candidates.size
        for (c in candidates) {
            result = 31 * result + c.classId
            result = 31 * result + (c.boundingBox.centerX() / 10f).toInt()
            result = 31 * result + (c.boundingBox.centerY() / 10f).toInt()
        }
        return result
    }

    // Controls: Start, Stop, Resume, Clear Statistics
    fun startSession() {
        currentSessionId = UUID.randomUUID().toString()
        _isStopped.value = false
        isInspectionFinalizedForCurrentScene = false
        lastFinalizedSceneCandidatesHash = 0
        tracker.reset()
        stabilityValidator.reset()
        _visibleCandidates.value = emptyList()
        _currentDecision.value = null
        _status.value = InspectionStatus.MONITORING
        _statusMessage.value = "New session started. Monitoring live scene."
    }

    fun stopSession() {
        _isStopped.value = true
        audioAlertManager.cancel()
        tracker.reset()
        stabilityValidator.reset()
        _visibleCandidates.value = emptyList()
        _currentDecision.value = null
        retainedSnapshotFrame?.recycle()
        retainedSnapshotFrame = null
        _status.value = InspectionStatus.STOPPED
        _statusMessage.value = "Inspection stopped by operator."
    }

    fun resumeSession() {
        startSession()
    }

    fun clearStatistics() {
        _cumulativeStats.value = CumulativeStats()
        _currentDecision.value = null
    }

    // Simulation helpers when physical camera/models are not yet loaded
    private fun generateSimulationDetections(bitmap: Bitmap): List<Detection> {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        return listOf(
            Detection(
                boundingBox = android.graphics.RectF(w * 0.2f, h * 0.35f, w * 0.45f, h * 0.65f),
                confidence = 0.95f,
                classId = 0
            ),
            Detection(
                boundingBox = android.graphics.RectF(w * 0.55f, h * 0.35f, w * 0.80f, h * 0.65f),
                confidence = 0.93f,
                classId = 0
            )
        )
    }

    private fun simulateChipClassification(candidate: TrackedCandidate): com.example.chipvision.data.model.ChipClassification {
        return com.example.chipvision.data.model.ChipClassification(
            chipType = com.example.chipvision.data.model.ChipType.PCS_06_K,
            orientation = com.example.chipvision.data.model.ChipOrientation.CORRECT_UPWARD,
            classIndex = 0,
            label = "PCS_06_K_correct_upward",
            confidence = 0.96f,
            isAccepted = true
        )
    }
}
