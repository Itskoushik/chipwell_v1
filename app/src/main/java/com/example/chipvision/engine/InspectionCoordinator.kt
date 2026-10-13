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
import com.example.chipvision.tracking.CameraStabilityTimer
import com.example.chipvision.tracking.StabilityValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
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
    /**
     * Guards coordinator state transitions only. Detector and classifier inference
     * intentionally run outside this lock so the analyzer remains responsive.
     */
    private val stateLock = Any()

    // UI state flows
    private val _status = MutableStateFlow(InspectionStatus.INITIALIZING)
    val status: StateFlow<InspectionStatus> = _status.asStateFlow()

    private val _statusMessage = MutableStateFlow("System Initializing...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    // Current-frame candidates used for responsive live overlays.
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

    private val performanceMetrics = PerformanceMetrics()
    private val _performanceMetrics = MutableStateFlow(performanceMetrics.snapshot())
    val performance: StateFlow<PerformanceMetricsSnapshot> = _performanceMetrics.asStateFlow()

    private val duplicateDiagnostics = DuplicateDiagnostics()
    private val _duplicateDiagnostics = MutableStateFlow(duplicateDiagnostics.snapshot())
    val duplicateEvidence: StateFlow<DuplicateDiagnosticsSnapshot> = _duplicateDiagnostics.asStateFlow()

    private val _isStopped = MutableStateFlow(false)
    val isStopped: StateFlow<Boolean> = _isStopped.asStateFlow()

    // Session and snapshot identifiers
    private var currentSessionId: String = UUID.randomUUID().toString()
    private var sessionGeneration: Long = 0L
    private var inspectionGeneration: Long = 0L
    private var classificationToken: Long = 0L
    private var currentSnapshotId: String = ""
    private var isInspectionFinalizedForCurrentScene = false
    private var lastFinalizedSceneCandidatesHash: Int = 0
    private var filterFrameCount: Long = 0L
    private var processedFrameCount: Long = 0L
    private var receivedFrameCount: Long = 0L
    private val cameraStabilityTimer = CameraStabilityTimer()
    private var lastRenderableTrackIds: Set<Long>? = null

    // Source frame retaining
    private var retainedSnapshotFrame: Bitmap? = null
    private var resultHoldJob: Job? = null
    private var classificationJob: Job? = null
    private val inspectionDispatchState = InspectionDispatchState()
    private var activeClassificationAuthorization: ClassificationAuthorization? = null
    private var resultHoldUntilMs: Long = 0L
    private var arrangementChangedDuringResultHold = false

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
        val frameStartNs = SystemClock.elapsedRealtimeNanos()
        receivedFrameCount++
        if (receivedFrameCount == 1L || receivedFrameCount % 30L == 0L) {
            Log.i(
                "CHIPVISION_PIPELINE",
                "frameReceived frame=$receivedFrameCount " +
                    "session=$currentSessionId atMs=$currentTimeMs"
            )
        }

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
            val detectorStartNs = SystemClock.elapsedRealtimeNanos()
            val rawDetections: List<Detection> = if (settings.simulationMode && modelManager.detectorInterpreter == null) {
                generateSimulationDetections(bitmap)
            } else {
                detectorService.detect(bitmap, threshold = settings.detectorThreshold)
            }
            val detectorDurationMs = (SystemClock.elapsedRealtimeNanos() - detectorStartNs) / 1_000_000L
            recordMetric { performanceMetrics.recordDetector(detectorDurationMs) }

            val filterStartNs = SystemClock.elapsedRealtimeNanos()
            val filteredDetections = DetectionFrameFilter.filter(
                detections = rawDetections,
                confidenceThreshold = settings.detectorThreshold
            )
            val filterDurationMs = (SystemClock.elapsedRealtimeNanos() - filterStartNs) / 1_000_000L
            recordMetric { performanceMetrics.recordFilter(filterDurationMs) }
            val detections = filteredDetections.detections
            duplicateDiagnostics.recordFrame(
                frameId = receivedFrameCount,
                timestampNs = SystemClock.elapsedRealtimeNanos(),
                beforeFilter = rawDetections,
                afterFilter = detections
            )
            _duplicateDiagnostics.value = duplicateDiagnostics.snapshot()
            filterFrameCount++
            val confidenceEligibleCount = rawDetections.count {
                it.confidence >= settings.detectorThreshold
            }
            if (filterFrameCount == 1L || filterFrameCount % 30L == 0L) {
                Log.i(
                    "CHIPVISION_FILTER",
                    "frame=$filterFrameCount raw=${rawDetections.size} " +
                        "confidenceEligible=$confidenceEligibleCount " +
                        "initialChips=${filteredDetections.initialChipCount} " +
                        "acceptedChips=${filteredDetections.acceptedChipCount} " +
                        "rejectedUndersized=${filteredDetections.rejectedUndersizedChipCount} " +
                        "validatedEmpty=${filteredDetections.validatedEmptyDetections.size} " +
                        "rawExamples=${formatDetections(rawDetections)} " +
                        "acceptedExamples=${formatDetections(detections)}"
                )
            }
            if (filteredDetections.validatedEmptyDetections.isEmpty()) {
                tracker.removeTracksForDetections(
                    detections = filteredDetections.rejectedUndersizedDetections,
                    classId = 0
                )
            }

            // 2. Global scene motion detection
            val motionStartNs = SystemClock.elapsedRealtimeNanos()
            val isSceneMoving = stabilityValidator.checkGlobalSceneMotion(
                bitmap = bitmap,
                sensitivity = settings.globalMotionSensitivity,
                confirmationFrames = settings.movementConfirmationFrames
            )
            val motionDurationMs = (SystemClock.elapsedRealtimeNanos() - motionStartNs) / 1_000_000L
            recordMetric { performanceMetrics.recordMotion(motionDurationMs) }

            // 3. Update candidate tracking
            val trackingStartNs = SystemClock.elapsedRealtimeNanos()
            val allCandidates = tracker.updateTracks(
                detections = detections,
                currentTimeMs = currentTimeMs,
                settings = settings,
                sessionId = currentSessionId
            )
            val liveCandidates = allCandidates.filter { it.lastSeenMs == currentTimeMs }
            val renderedCandidates = tracker.renderableTracks(
                currentTimeMs = currentTimeMs,
                trackingTimeoutMs = settings.trackingTimeoutMs
            )
            val trackingDurationMs = (SystemClock.elapsedRealtimeNanos() - trackingStartNs) / 1_000_000L
            recordMetric { performanceMetrics.recordTracking(trackingDurationMs) }

            // Update current frame statistics
            updateFrameStats(
                candidates = renderedCandidates,
                rawDetectorPredictions = rawDetections.size,
                confidenceEligibleDetections = confidenceEligibleCount,
                acceptedDetections = filteredDetections.acceptedChipCount,
                rejectedUndersizedDetections = filteredDetections.rejectedUndersizedChipCount
            )
            _visibleCandidates.value = renderedCandidates
            if (filterFrameCount == 1L || filterFrameCount % 30L == 0L) {
                Log.i(
                    "CHIPVISION_RENDER",
                    "chipBoxes=${renderedCandidates.count { it.isChip }} " +
                        "emptyBoxes=${renderedCandidates.count { it.isEmptyImpression }} " +
                        "overlayCandidates=${renderedCandidates.size} " +
                        "liveDetections=${liveCandidates.size}"
                )
            }

            // 4. Handle scene motion
            if (isSceneMoving) {
                synchronized(stateLock) {
                    Log.i(
                        "CHIPVISION_STABILITY",
                        "timerReset reason=confirmed_scene_motion generation=$currentSessionId " +
                            "atMs=$currentTimeMs"
                    )
                    cameraStabilityTimer.reset()
                    inspectionGeneration++
                    activeClassificationAuthorization = null
                    Log.i(
                        "CHIPVISION_CLASSIFICATION",
                        "snapshotInvalidated reason=confirmed_scene_motion " +
                            "inspectionGeneration=$inspectionGeneration"
                    )
                    isInspectionFinalizedForCurrentScene = false
                    lastFinalizedSceneCandidatesHash = 0
                    inspectionDispatchState.clear()
                }
                tracker.invalidateAllStability(currentTimeMs)
                _status.value = InspectionStatus.MONITORING
                _statusMessage.value = "Conveyor motion detected. Waiting for rest."
                retainedSnapshotFrame?.recycle()
                retainedSnapshotFrame = null
                logFrameTiming(
                    detectorDurationMs,
                    filterDurationMs,
                    motionDurationMs,
                    trackingDurationMs,
                    0L,
                    frameStartNs,
                    renderedCandidates.size,
                    isSceneMoving
                )
                return
            }

            // A track entering or leaving after the grace period is a confirmed
            // arrangement re-arm event. Box jitter and confidence changes do not
            // affect the camera-only stability timer.
            val renderableTrackIds = renderedCandidates.mapTo(mutableSetOf()) { it.trackId }
            val previousTrackIds = lastRenderableTrackIds
            val trackSetChanged = isInspectionFinalizedForCurrentScene &&
                previousTrackIds != null &&
                previousTrackIds != renderableTrackIds
            val confirmedTrackMovement = allCandidates.any {
                it.movementEvidenceFrames >= settings.movementConfirmationFrames &&
                    it.stabilityStartMs == currentTimeMs
            }
            if (trackSetChanged || confirmedTrackMovement) {
                val reason = when {
                    confirmedTrackMovement -> "confirmed_tracked_movement"
                    else -> "track_entry_or_expiry"
                }
                Log.i(
                    "CHIPVISION_STABILITY",
                    "timerReset reason=$reason generation=$currentSessionId " +
                        "atMs=$currentTimeMs previousTracks=$previousTrackIds " +
                        "currentTracks=$renderableTrackIds"
                )
                cameraStabilityTimer.reset()
                isInspectionFinalizedForCurrentScene = false
                lastFinalizedSceneCandidatesHash = 0
            }
            lastRenderableTrackIds = renderableTrackIds

            val cameraStableForRequiredDuration = cameraStabilityTimer.update(
                cameraMoving = false,
                nowMs = currentTimeMs,
                requiredDurationMs = settings.stabilityDurationMs
            )
            val cameraStableSinceMs = cameraStabilityTimer.stableSinceMs()
            val cameraStableElapsedMs = cameraStableSinceMs?.let { currentTimeMs - it } ?: 0L

            if (liveCandidates.isEmpty() && !cameraStableForRequiredDuration) {
                // Keep a valid tracked box visible through the configured grace period,
                // but do not classify from a frame that did not contain the detection.
                _visibleCandidates.value = renderedCandidates
                if (tracker.activeTrackCount() == 0) {
                    isInspectionFinalizedForCurrentScene = false
                    lastFinalizedSceneCandidatesHash = 0
                    _status.value = InspectionStatus.MONITORING
                    _statusMessage.value = "Monitoring live conveyor scene"
                } else {
                    _status.value = InspectionStatus.WAITING_FOR_STABILITY
                    _statusMessage.value = "Waiting for detections to return."
                }
                return
            }

            if (!cameraStableForRequiredDuration) {
                if (!isInspectionFinalizedForCurrentScene) {
                    _status.value = InspectionStatus.WAITING_FOR_STABILITY
                    _statusMessage.value = "Waiting for camera stability (0.2s)..."
                }
                return
            }

            // Camera stability is authoritative. Candidate geometry is retained
            // for rendering and re-arm detection, but does not gate inspection.
            val inspectionCandidates = liveCandidates.map { it.copy(isStable = true) }
            Log.i(
                "CHIPVISION_PIPELINE",
                "deadlineSnapshot session=$currentSessionId " +
                    "snapshotPending=true acceptedCandidates=${inspectionCandidates.size}"
            )
            val currentSceneHash = computeSceneHash(liveCandidates)
            val holdStatus = synchronized(stateLock) {
                if (resultHoldUntilMs <= currentTimeMs) {
                    null
                } else {
                    if (currentSceneHash != lastFinalizedSceneCandidatesHash) {
                        arrangementChangedDuringResultHold = true
                    }
                    if (_currentDecision.value?.isPass == true) {
                        InspectionStatus.PASS
                    } else {
                        InspectionStatus.FAIL
                    }
                }
            }
            if (holdStatus != null) {
                _status.value = holdStatus
                return
            }
            val activeSceneHash = synchronized(stateLock) {
                if (activeClassificationAuthorization != null) {
                    inspectionDispatchState.observe(currentSceneHash)
                    inspectionDispatchState.activeSceneHash()
                } else {
                    null
                }
            }
            if (activeSceneHash != null) {
                if (currentSceneHash != activeSceneHash) {
                    Log.i(
                        "CHIPVISION_CLASSIFICATION",
                        "pendingArrangement sceneHash=$currentSceneHash " +
                            "activeSceneHash=$activeSceneHash " +
                            "atMs=$currentTimeMs"
                    )
                }
                _status.value = InspectionStatus.CLASSIFYING
                _statusMessage.value = "Classifying chips while monitoring live scene..."
                Log.i(
                    "CHIPVISION_RENDER",
                    "overlayUpdatedDuringClassification candidates=${renderedCandidates.size} " +
                        "atMs=$currentTimeMs"
                )
                return
            }
            synchronized(stateLock) {
                if (isInspectionFinalizedForCurrentScene &&
                    currentSceneHash != lastFinalizedSceneCandidatesHash
                ) {
                    isInspectionFinalizedForCurrentScene = false
                }
            }

            // If the scene has already been finalized and hasn't changed, don't re-run inspection
            val sceneAlreadyFinalized = synchronized(stateLock) {
                isInspectionFinalizedForCurrentScene &&
                    currentSceneHash == lastFinalizedSceneCandidatesHash
            }
            if (sceneAlreadyFinalized) {
                // Scene remains stationary after finalization - keep displaying finalized result
                return
            }

            // 6. A FRESH STABLE SCENE IS ESTABLISHED (0.2 seconds satisfied)
            Log.i(
                "CHIPVISION_STABILITY",
                "deadlineReached generation=$currentSessionId startMs=$cameraStableSinceMs " +
                    "nowMs=$currentTimeMs elapsedMs=$cameraStableElapsedMs " +
                    "requiredMs=${settings.stabilityDurationMs}"
            )
            _status.value = InspectionStatus.STABLE_SCENE_READY
            _statusMessage.value = "Stable scene locked. Evaluating..."

            val snapshotInspectionGeneration: Long
            val snapshotId: String
            synchronized(stateLock) {
                currentSnapshotId = UUID.randomUUID().toString()
                inspectionGeneration++
                snapshotInspectionGeneration = inspectionGeneration
                snapshotId = currentSnapshotId
            }
            val snapshotFrame = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            val snapshotAgeMs = (SystemClock.elapsedRealtimeNanos() - frameStartNs) / 1_000_000L
            recordMetric { performanceMetrics.recordSnapshotAge(snapshotAgeMs) }
            val inspectionCycleStartNs = SystemClock.elapsedRealtimeNanos()

            // 7. EMPTY-IMPRESSION-FIRST CHECK (Section 9)
            if (liveCandidates.any { it.isEmptyImpression }) {
                Log.i(
                    "CHIPVISION_CLASSIFICATION",
                    "skipped reason=validated_empty_impression generation=$currentSessionId " +
                        "snapshot=$snapshotId"
                )
                _status.value = InspectionStatus.CHECKING_EMPTY_IMPRESSIONS
                val finalizationStartNs = SystemClock.elapsedRealtimeNanos()
                finalizeInspection(
                    candidates = inspectionCandidates,
                    settings = settings,
                    overrideDecision = InspectionDecisionEngine.evaluate(
                        candidates = inspectionCandidates,
                        sessionId = currentSessionId,
                        snapshotId = snapshotId
                    )
                )
                recordMetric {
                    performanceMetrics.recordFinalization(
                        (SystemClock.elapsedRealtimeNanos() - finalizationStartNs) / 1_000_000L
                    )
                    performanceMetrics.recordInspectionCycle(
                        (SystemClock.elapsedRealtimeNanos() - inspectionCycleStartNs) / 1_000_000L
                    )
                }
                lastFinalizedSceneCandidatesHash = currentSceneHash
                snapshotFrame.recycle()
                return
            }

            // 8. Submit classification away from the CameraX analyzer. The
            // immutable snapshot is separate from the live overlay state.
            _status.value = InspectionStatus.CLASSIFYING
            _statusMessage.value = "Classifying chips..."
            Log.i(
                "CHIPVISION_CLASSIFICATION",
                "start generation=$currentSessionId snapshot=$currentSnapshotId " +
                    "candidates=${inspectionCandidates.size} snapshotAgeMs=$snapshotAgeMs"
            )
            val classificationSessionId: String
            val authorization: ClassificationAuthorization
            val job: Job
            synchronized(stateLock) {
                if (!inspectionDispatchState.begin(currentSceneHash)) {
                    snapshotFrame.recycle()
                    return
                }
                classificationSessionId = currentSessionId
                authorization = ClassificationAuthorization(
                    sessionId = classificationSessionId,
                    sessionGeneration = sessionGeneration,
                    inspectionGeneration = snapshotInspectionGeneration,
                    snapshotId = snapshotId,
                    sceneHash = currentSceneHash,
                    token = ++classificationToken
                )
                activeClassificationAuthorization = authorization
                job = scope.launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
                    classifySnapshot(
                        snapshotFrame = snapshotFrame,
                        candidates = inspectionCandidates,
                        settings = settings,
                        authorization = authorization,
                        snapshotAgeMs = snapshotAgeMs,
                        cycleStartNs = inspectionCycleStartNs
                    )
                }
                classificationJob = job
            }
            job.start()

        } catch (e: Exception) {
            Log.e(tag, "Inspection coordinator exception", e)
            val decision = InspectionDecisionEngine.evaluate(
                candidates = emptyList(),
                sessionId = currentSessionId,
                snapshotId = currentSnapshotId.ifBlank { UUID.randomUUID().toString() },
                inferenceError = e.message ?: e.javaClass.simpleName
            )
            if (!isInspectionFinalizedForCurrentScene) {
                Log.e(
                    "CHIPVISION_CLASSIFICATION",
                    "terminalFailure session=$currentSessionId snapshot=$currentSnapshotId " +
                        "error=${e.javaClass.simpleName}: ${e.message}"
                )
                val finalizationStartNs = SystemClock.elapsedRealtimeNanos()
                finalizeInspection(
                    candidates = emptyList(),
                    settings = settings,
                    overrideDecision = decision
                )
                recordMetric {
                    performanceMetrics.recordFinalization(
                        (SystemClock.elapsedRealtimeNanos() - finalizationStartNs) / 1_000_000L
                    )
                }
            } else {
                _currentDecision.value = decision
                _status.value = InspectionStatus.FAIL
                _statusMessage.value = decision.message
            }
        } finally {
            recordMetric {
                performanceMetrics.recordFrameAnalysis(
                    (SystemClock.elapsedRealtimeNanos() - frameStartNs) / 1_000_000L
                )
            }
            bitmap.recycle()
        }
    }

    private suspend fun classifySnapshot(
        snapshotFrame: Bitmap,
        candidates: List<TrackedCandidate>,
        settings: InspectionSettings,
        authorization: ClassificationAuthorization,
        snapshotAgeMs: Long,
        cycleStartNs: Long
    ) {
        val sessionId = authorization.sessionId
        val snapshotId = authorization.snapshotId
        val classificationStartNs = SystemClock.elapsedRealtimeNanos()
        try {
            Log.i(
                "CHIPVISION_CLASSIFICATION",
                "jobStarted session=$sessionId snapshot=$snapshotId " +
                    "snapshotAgeMs=$snapshotAgeMs"
            )
            val classifiedCandidates = mutableListOf<TrackedCandidate>()
            for (chipCandidate in candidates) {
                if (chipCandidate.isChip) {
                    Log.d(
                        "CHIPVISION_CLASSIFICATION",
                        "cropSubmitted session=$sessionId snapshot=$snapshotId " +
                            "track=${chipCandidate.trackId}"
                    )
                    val classification = if (settings.simulationMode && modelManager.classifierInterpreter == null) {
                        simulateChipClassification(chipCandidate)
                    } else {
                        classifierService.classifyChip(
                            sourceFrame = snapshotFrame,
                            boundingBox = chipCandidate.boundingBox,
                            threshold = settings.classifierThreshold
                        )
                    }
                    classifiedCandidates.add(chipCandidate.copy(classification = classification))
                } else {
                    classifiedCandidates.add(chipCandidate)
                }
            }

            val classificationDurationMs =
                (SystemClock.elapsedRealtimeNanos() - cycleStartNs) / 1_000_000L
            recordMetric { performanceMetrics.recordClassifier(classificationDurationMs) }
            val finalDecision = synchronized(stateLock) {
                if (!isClassificationAuthorized(authorization)) {
                    null
                } else {
                    classifiedCandidates.forEach { candidate ->
                        candidate.classification?.let { classification ->
                            tracker.updateClassification(candidate.trackId, classification)
                        }
                    }
                    val decision = InspectionDecisionEngine.evaluate(
                        candidates = classifiedCandidates,
                        sessionId = sessionId,
                        snapshotId = snapshotId
                    )
                    _status.value = InspectionStatus.FINALIZING
                    val finalizationStartNs = SystemClock.elapsedRealtimeNanos()
                    finalizeInspection(
                        candidates = classifiedCandidates,
                        settings = settings,
                        overrideDecision = decision
                    )
                    recordMetric {
                        performanceMetrics.recordFinalization(
                            (SystemClock.elapsedRealtimeNanos() - finalizationStartNs) / 1_000_000L
                        )
                        performanceMetrics.recordInspectionCycle(
                            (SystemClock.elapsedRealtimeNanos() - cycleStartNs) / 1_000_000L
                        )
                    }
                    lastFinalizedSceneCandidatesHash = authorization.sceneHash
                    arrangementChangedDuringResultHold = inspectionDispatchState.complete() != null
                    decision
                }
            }
            if (finalDecision == null) {
                recordMetric { performanceMetrics.recordStaleResultDiscarded() }
                Log.w(
                    "CHIPVISION_CLASSIFICATION",
                    "staleResultDiscarded reason=authorization_changed " +
                        "session=$sessionId snapshot=$snapshotId"
                )
                return
            }
            Log.i(
                "CHIPVISION_CLASSIFICATION",
                "jobCompleted session=$sessionId snapshot=$snapshotId " +
                    "durationMs=$classificationDurationMs decision=${finalDecision.reasonCode.code} " +
                    "pending=${arrangementChangedDuringResultHold}"
            )
        } catch (e: CancellationException) {
            Log.i(
                "CHIPVISION_CLASSIFICATION",
                "jobCancelled session=$sessionId snapshot=$snapshotId"
            )
            throw e
        } catch (e: Exception) {
            Log.e(
                "CHIPVISION_CLASSIFICATION",
                "jobFailed session=$sessionId snapshot=$snapshotId",
                e
            )
            val failureFinalized = synchronized(stateLock) {
                if (!isClassificationAuthorized(authorization)) {
                    false
                } else {
                    val decision = InspectionDecisionEngine.evaluate(
                        candidates = emptyList(),
                        sessionId = sessionId,
                        snapshotId = snapshotId,
                        inferenceError = e.message ?: e.javaClass.simpleName
                    )
                    finalizeInspection(
                        candidates = emptyList(),
                        settings = settings,
                        overrideDecision = decision
                    )
                    lastFinalizedSceneCandidatesHash = authorization.sceneHash
                    arrangementChangedDuringResultHold = inspectionDispatchState.complete() != null
                    true
                }
            }
            if (!failureFinalized) {
                recordMetric { performanceMetrics.recordStaleResultDiscarded() }
                Log.w(
                    "CHIPVISION_CLASSIFICATION",
                    "staleResultDiscarded reason=classification_failure_authorization_changed " +
                        "session=$sessionId snapshot=$snapshotId"
                )
            }
        } finally {
            snapshotFrame.recycle()
            synchronized(stateLock) {
                if (activeClassificationAuthorization == authorization) {
                    inspectionDispatchState.complete()
                    activeClassificationAuthorization = null
                    classificationJob = null
                } else {
                    Log.i(
                        "CHIPVISION_CLASSIFICATION",
                        "staleJobCleanupSkipped session=$sessionId snapshot=$snapshotId"
                    )
                }
            }
        }
    }

    private fun isClassificationAuthorized(
        authorization: ClassificationAuthorization
    ): Boolean {
        return synchronized(stateLock) {
            !_isStopped.value &&
                currentSessionId == authorization.sessionId &&
                sessionGeneration == authorization.sessionGeneration &&
                inspectionGeneration == authorization.inspectionGeneration &&
                currentSnapshotId == authorization.snapshotId &&
                activeClassificationAuthorization == authorization
        }
    }

    private fun finalizeInspection(
        candidates: List<TrackedCandidate>,
        settings: InspectionSettings,
        overrideDecision: InspectionDecision
    ) {
        synchronized(stateLock) {
            _currentDecision.value = overrideDecision
            isInspectionFinalizedForCurrentScene = true

            if (overrideDecision.isPass) {
                _status.value = InspectionStatus.PASS
                _statusMessage.value = overrideDecision.message
                audioAlertManager.playPassAlert(settings)
            } else {
                _status.value = InspectionStatus.FAIL
                _statusMessage.value = overrideDecision.message
                audioAlertManager.playFailAlert(
                    settings = settings,
                    repeatDurationMs = InspectionResultDelay.FAIL_DELAY_MS
                )
            }
            scheduleResultHold(overrideDecision)

            // Update cumulative statistics
            updateCumulativeStats(overrideDecision, candidates)
        }

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

    private fun scheduleResultHold(decision: InspectionDecision) {
        synchronized(stateLock) {
            resultHoldJob?.cancel()
            arrangementChangedDuringResultHold = false
            resultHoldUntilMs = SystemClock.elapsedRealtime() + InspectionResultDelay.forDecision(decision)
            Log.i(
                "CHIPVISION_RESULT_DELAY",
                "scheduled result=${if (decision.isPass) "PASS" else "FAIL"} " +
                    "delayMs=${InspectionResultDelay.forDecision(decision)} " +
                    "untilMs=$resultHoldUntilMs snapshot=${decision.snapshotId}"
            )
            resultHoldJob = scope.launch {
            delay(InspectionResultDelay.forDecision(decision))
            if (_isStopped.value) return@launch
            synchronized(stateLock) {
                resultHoldUntilMs = 0L
                if (arrangementChangedDuringResultHold) {
                    isInspectionFinalizedForCurrentScene = false
                    lastFinalizedSceneCandidatesHash = 0
                    _status.value = InspectionStatus.MONITORING
                    _statusMessage.value = "Result delay complete. Waiting for camera stability."
                }
                Log.i(
                    "CHIPVISION_RESULT_DELAY",
                    "expired result=${if (decision.isPass) "PASS" else "FAIL"} " +
                        "changedDuringHold=$arrangementChangedDuringResultHold"
                )
            }
            }
        }
    }

    private fun updateFrameStats(
        candidates: List<TrackedCandidate>,
        rawDetectorPredictions: Int,
        confidenceEligibleDetections: Int,
        acceptedDetections: Int,
        rejectedUndersizedDetections: Int
    ) {
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
            emptyImpressions = empty,
            rawDetectorPredictions = rawDetectorPredictions,
            confidenceEligibleDetections = confidenceEligibleDetections,
            acceptedDetections = acceptedDetections,
            rejectedUndersizedDetections = rejectedUndersizedDetections,
            liveCandidates = candidates.size
        )
    }

    private fun formatDetections(detections: List<Detection>): String {
        return detections.take(5).joinToString(prefix = "[", postfix = "]") { detection ->
            val box = detection.boundingBox
            "{class=${detection.classId},conf=${"%.3f".format(detection.confidence)}," +
                "box=${"%.1f".format(box.left)},${"%.1f".format(box.top)}-" +
                "${"%.1f".format(box.right)},${"%.1f".format(box.bottom)}}"
        }
    }

    private fun logFrameTiming(
        detectorDurationMs: Long,
        filterDurationMs: Long,
        motionDurationMs: Long,
        trackingDurationMs: Long,
        classificationDurationMs: Long,
        frameStartNs: Long,
        liveCandidateCount: Int,
        isSceneMoving: Boolean
    ) {
        processedFrameCount++
        if (processedFrameCount == 1L || processedFrameCount % 30L == 0L) {
            val totalDurationMs = (SystemClock.elapsedRealtimeNanos() - frameStartNs) / 1_000_000L
            Log.i(
                "CHIPVISION_TIMING",
                "frame=$processedFrameCount detectorMs=$detectorDurationMs " +
                    "filterMs=$filterDurationMs motionMs=$motionDurationMs " +
                    "trackingMs=$trackingDurationMs classificationMs=$classificationDurationMs " +
                    "totalMs=$totalDurationMs liveCandidates=$liveCandidateCount " +
                    "moving=$isSceneMoving"
            )
        }
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
        for (c in candidates.sortedWith(compareBy<TrackedCandidate> { it.classId }
            .thenBy { it.boundingBox.centerY() }
            .thenBy { it.boundingBox.centerX() })) {
            result = 31 * result + c.classId
            result = 31 * result + (c.boundingBox.centerX() / 10f).toInt()
            result = 31 * result + (c.boundingBox.centerY() / 10f).toInt()
        }
        return result
    }

    private fun recordMetric(update: () -> Unit) {
        update()
        _performanceMetrics.value = performanceMetrics.snapshot()
    }

    fun setDuplicateDiagnosticsSampling(sampleEveryNFrames: Long) {
        duplicateDiagnostics.setSampleEveryNFrames(sampleEveryNFrames)
        _duplicateDiagnostics.value = duplicateDiagnostics.snapshot()
    }

    // Controls: Start, Stop, Resume, Clear Statistics
    fun startSession() {
        val jobsToCancel: Pair<Job?, Job?>
        synchronized(stateLock) {
            _isStopped.value = true
            sessionGeneration++
            inspectionGeneration++
            activeClassificationAuthorization = null
            jobsToCancel = resultHoldJob to classificationJob
            resultHoldJob = null
            classificationJob = null
            inspectionDispatchState.clear()
            resultHoldUntilMs = 0L
            arrangementChangedDuringResultHold = false
            currentSessionId = UUID.randomUUID().toString()
            isInspectionFinalizedForCurrentScene = false
            lastFinalizedSceneCandidatesHash = 0
        }
        jobsToCancel.first?.cancel()
        jobsToCancel.second?.cancel()
        audioAlertManager.cancel()
        recordMetric { performanceMetrics.reset() }
        duplicateDiagnostics.reset()
        _duplicateDiagnostics.value = duplicateDiagnostics.snapshot()
        tracker.reset()
        stabilityValidator.reset()
        cameraStabilityTimer.reset()
        lastRenderableTrackIds = null
        _visibleCandidates.value = emptyList()
        _currentDecision.value = null
        synchronized(stateLock) {
            _isStopped.value = false
            _status.value = InspectionStatus.MONITORING
            _statusMessage.value = "New session started. Monitoring live scene."
        }
    }

    fun stopSession() {
        val jobsToCancel: Pair<Job?, Job?>
        synchronized(stateLock) {
            _isStopped.value = true
            sessionGeneration++
            inspectionGeneration++
            activeClassificationAuthorization = null
            jobsToCancel = resultHoldJob to classificationJob
            resultHoldJob = null
            classificationJob = null
            inspectionDispatchState.clear()
            resultHoldUntilMs = 0L
            arrangementChangedDuringResultHold = false
            _status.value = InspectionStatus.STOPPED
            _statusMessage.value = "Inspection stopped by operator."
        }
        audioAlertManager.cancel()
        jobsToCancel.first?.cancel()
        jobsToCancel.second?.cancel()
        audioAlertManager.cancel()
        duplicateDiagnostics.reset()
        _duplicateDiagnostics.value = duplicateDiagnostics.snapshot()
        tracker.reset()
        stabilityValidator.reset()
        cameraStabilityTimer.reset()
        lastRenderableTrackIds = null
        _visibleCandidates.value = emptyList()
        _currentDecision.value = null
        retainedSnapshotFrame?.recycle()
        retainedSnapshotFrame = null
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

    private data class ClassificationAuthorization(
        val sessionId: String,
        val sessionGeneration: Long,
        val inspectionGeneration: Long,
        val snapshotId: String,
        val sceneHash: Int,
        val token: Long
    )
}
