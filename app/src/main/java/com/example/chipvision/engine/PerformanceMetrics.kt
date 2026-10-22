package com.example.chipvision.engine

import kotlin.math.ceil

data class MetricSummary(
    val count: Int = 0,
    val averageMs: Double = 0.0,
    val p95Ms: Double = 0.0,
    val maximumMs: Long = 0L
)

data class PerformanceMetricsSnapshot(
    val frameAnalysis: MetricSummary = MetricSummary(),
    val detector: MetricSummary = MetricSummary(),
    val filter: MetricSummary = MetricSummary(),
    val motion: MetricSummary = MetricSummary(),
    val tracking: MetricSummary = MetricSummary(),
    val postProcessing: MetricSummary = MetricSummary(),
    val snapshotAge: MetricSummary = MetricSummary(),
    val finalization: MetricSummary = MetricSummary(),
    val inspectionCycle: MetricSummary = MetricSummary(),
    val processedFrames: Long = 0L,
    val skippedFrames: Long = 0L,
    val staleResultsDiscarded: Long = 0L,
    val completedInspections: Int = 0
)

/**
 * Bounded, thread-safe in-memory timing and counter aggregation.
 *
 * Durations are recorded in milliseconds from elapsed monotonic clocks.
 * The inspection cycle starts when an immutable snapshot is captured and ends
 * after the final decision is published; result display holds are excluded.
 */
class PerformanceMetrics(private val maxSamples: Int = DEFAULT_SAMPLE_WINDOW) {
    private val lock = Any()
    private val samples = linkedMapOf<String, ArrayDeque<Long>>()
    private var processedFrames = 0L
    private var skippedFrames = 0L
    private var staleResultsDiscarded = 0L
    private var completedInspections = 0
    private var snapshot = PerformanceMetricsSnapshot()

    init {
        require(maxSamples > 0) { "maxSamples must be positive" }
    }

    fun recordFrameAnalysis(durationMs: Long) = record("frameAnalysis", durationMs) {
        processedFrames++
    }

    fun recordDetector(durationMs: Long) = record("detector", durationMs)
    fun recordFilter(durationMs: Long) = record("filter", durationMs)
    fun recordMotion(durationMs: Long) = record("motion", durationMs)
    fun recordTracking(durationMs: Long) = record("tracking", durationMs)
    fun recordPostProcessing(durationMs: Long) = record("postProcessing", durationMs)
    fun recordSnapshotAge(durationMs: Long) = record("snapshotAge", durationMs)
    fun recordFinalization(durationMs: Long) = record("finalization", durationMs)
    fun recordInspectionCycle(durationMs: Long) = record("inspectionCycle", durationMs) {
        completedInspections++
    }

    fun recordSkippedFrame() {
        synchronized(lock) {
            skippedFrames++
            publish()
        }
    }

    fun recordStaleResultDiscarded() {
        synchronized(lock) {
            staleResultsDiscarded++
            publish()
        }
    }

    fun snapshot(): PerformanceMetricsSnapshot = synchronized(lock) { snapshot }

    fun reset() {
        synchronized(lock) {
            samples.clear()
            processedFrames = 0L
            skippedFrames = 0L
            staleResultsDiscarded = 0L
            completedInspections = 0
            publish()
        }
    }

    private fun record(
        key: String,
        durationMs: Long,
        after: (() -> Unit)? = null
    ) {
        require(durationMs >= 0L) { "durationMs must not be negative" }
        synchronized(lock) {
            val values = samples.getOrPut(key) { ArrayDeque(maxSamples) }
            if (values.size == maxSamples) values.removeFirst()
            values.addLast(durationMs)
            after?.invoke()
            publish()
        }
    }

    private fun publish() {
        snapshot = PerformanceMetricsSnapshot(
            frameAnalysis = summary("frameAnalysis"),
            detector = summary("detector"),
            filter = summary("filter"),
            motion = summary("motion"),
            tracking = summary("tracking"),
            postProcessing = summary("postProcessing"),
            snapshotAge = summary("snapshotAge"),
            finalization = summary("finalization"),
            inspectionCycle = summary("inspectionCycle"),
            processedFrames = processedFrames,
            skippedFrames = skippedFrames,
            staleResultsDiscarded = staleResultsDiscarded,
            completedInspections = completedInspections
        )
    }

    private fun summary(key: String): MetricSummary {
        val values = samples[key]?.sorted() ?: return MetricSummary()
        if (values.isEmpty()) return MetricSummary()
        val p95Index = (ceil(values.size * 0.95).toInt() - 1).coerceIn(0, values.lastIndex)
        return MetricSummary(
            count = values.size,
            averageMs = values.average(),
            p95Ms = values[p95Index].toDouble(),
            maximumMs = values.last()
        )
    }

    companion object {
        const val DEFAULT_SAMPLE_WINDOW = 120
    }
}
