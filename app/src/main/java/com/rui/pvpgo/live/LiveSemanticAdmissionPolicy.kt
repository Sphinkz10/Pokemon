package com.rui.pvpgo.live

/**
 * V1.48: defensive admission boundary for future visual event detectors.
 * It cannot recognize anything by itself; no detector is approved by default.
 * The app must never treat a manual quick log or frame cadence as semantic evidence.
 */
data class SemanticObservationCandidate<T>(
    val detectorId: String,
    val sessionId: String,
    val eventKey: String,
    val frameId: Long,
    val atEpochMs: Long,
    val confidence: Double,
    val event: T
)

data class SemanticAdmissionResult<T>(
    val admitted: T? = null,
    val reason: String,
    val confirmations: Int = 0
)

class LiveSemanticAdmissionGate<T>(
    /** Explicitly empty in the shipped Companion until a detector is evaluated on-device. */
    private val approvedDetectorIds: Set<String> = emptySet(),
    private val minimumConfidence: Double = 0.97,
    private val confirmationsRequired: Int = 3,
    private val maxEvidenceAgeMs: Long = 1200
) {
    init {
        require(minimumConfidence in 0.0..1.0)
        require(confirmationsRequired >= 2)
        require(maxEvidenceAgeMs > 0)
    }
    private data class Pending(val sessionId: String, val detectorId: String,
        val key: String, val lastFrameId: Long, val confirmations: Int,
        val lastEpochMs: Long)
    private var pending: Pending? = null
    private val admittedKeys = LinkedHashSet<String>()
    private var currentSession: String? = null

    fun reset(sessionId: String? = null) {
        currentSession = sessionId
        pending = null
        admittedKeys.clear()
    }

    fun consider(candidate: SemanticObservationCandidate<T>, nowEpochMs: Long,
        quality: FrameQualityPresentation, activeSessionId: String?): SemanticAdmissionResult<T> {
        if (currentSession != activeSessionId) reset(activeSessionId)
        if (candidate.detectorId !in approvedDetectorIds)
            return SemanticAdmissionResult(reason = "DETECTOR_NOT_APPROVED")
        if (activeSessionId.isNullOrBlank() || candidate.sessionId != activeSessionId)
            return SemanticAdmissionResult(reason = "SESSION_MISMATCH")
        if (!quality.transportStable) {
            pending = null
            return SemanticAdmissionResult(reason = "FRAME_QUALITY_INSUFFICIENT")
        }
        if (candidate.eventKey.isBlank() || candidate.frameId <= 0 ||
            candidate.confidence.isNaN() || candidate.confidence !in minimumConfidence..1.0)
            return SemanticAdmissionResult(reason = "UNTRUSTED_CANDIDATE")
        val age = nowEpochMs - candidate.atEpochMs
        if (age !in 0..maxEvidenceAgeMs)
            return SemanticAdmissionResult(reason = "STALE_EVIDENCE")
        val identity = candidate.detectorId + "|" + candidate.eventKey
        if (identity in admittedKeys) return SemanticAdmissionResult(reason = "EVENT_ALREADY_ADMITTED")
        val last = pending
        if (last != null && candidate.frameId <= last.lastFrameId &&
            last.sessionId == candidate.sessionId && last.detectorId == candidate.detectorId &&
            last.key == candidate.eventKey) return SemanticAdmissionResult(reason = "REPEATED_FRAME")
        val count = if (last != null && last.sessionId == candidate.sessionId &&
            last.detectorId == candidate.detectorId && last.key == candidate.eventKey &&
            candidate.frameId == last.lastFrameId + 1 && candidate.atEpochMs >= last.lastEpochMs &&
            candidate.atEpochMs - last.lastEpochMs <= maxEvidenceAgeMs) last.confirmations + 1 else 1
        pending = Pending(candidate.sessionId, candidate.detectorId, candidate.eventKey,
            candidate.frameId, count, candidate.atEpochMs)
        if (count < confirmationsRequired)
            return SemanticAdmissionResult(reason = "PENDING_CONFIRMATIONS", confirmations = count)
        admittedKeys.add(identity)
        // Bound session-local identifiers; live streaming must not grow indefinitely.
        if (admittedKeys.size > 128) admittedKeys.remove(admittedKeys.first())
        pending = null
        return SemanticAdmissionResult(admitted = candidate.event,
            reason = "ADMITTED", confirmations = count)
    }
}
