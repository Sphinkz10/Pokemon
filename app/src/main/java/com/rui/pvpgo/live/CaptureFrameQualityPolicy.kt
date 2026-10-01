package com.rui.pvpgo.live

/**
 * Frame transport quality only. This gate DOES NOT recognize Pokémon, moves or battle events,
 * and MUST NOT be used as a confidence score for a tactical recommendation.
 * Pure Kotlin: deterministic tests can run without an Android SDK.
 */
data class FrameQualityInput(
    val sessionId: String,
    val frameId: Long,
    val epochMs: Long,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int
)

enum class FrameQualityIssue {
    NONE, NO_SESSION, NO_FRAMES, INVALID_FRAME, FRAME_GAP,
    NON_MONOTONIC, GEOMETRY_CHANGED, ROTATION_CHANGED, FRAME_STALE, CAPTURE_INACTIVE
}

enum class FrameQualityLevel { INACTIVE, WAITING, STABLE, DEGRADED, STALE }

data class FrameQualitySnapshot(
    val sessionId: String? = null,
    val totalFrames: Long = 0,
    val stableFrameStreak: Int = 0,
    val anomalyCount: Int = 0,
    val lastFrameAtEpochMs: Long? = null,
    val lastIssue: FrameQualityIssue = FrameQualityIssue.NO_FRAMES
)

data class FrameQualityPresentation(
    val level: FrameQualityLevel,
    val title: String,
    val detail: String,
    val transportStable: Boolean
)

/** Resets on every session. Never retains images, pixels, OCR text, or human identifiers. */
class CaptureFrameQualityGate(
    private val requiredStableFrames: Int = 12,
    private val maxFrameGapMs: Long = 650,
    private val staleAfterMs: Long = 1800
) {
    init {
        require(requiredStableFrames > 1)
        require(maxFrameGapMs > 0L)
        require(staleAfterMs > maxFrameGapMs)
    }

    private var previous: FrameQualityInput? = null
    private var latest = FrameQualitySnapshot()

    fun reset(sessionId: String? = null): FrameQualitySnapshot {
        previous = null
        latest = FrameQualitySnapshot(sessionId = sessionId)
        return latest
    }

    fun onFrame(frame: FrameQualityInput): FrameQualitySnapshot {
        if (latest.sessionId != frame.sessionId) reset(frame.sessionId)
        val prior = previous
        val issue = when {
            frame.sessionId.isBlank() -> FrameQualityIssue.NO_SESSION
            frame.frameId <= 0L || frame.epochMs <= 0L || frame.width <= 0 || frame.height <= 0 ||
                frame.rotationDegrees !in setOf(0, 90, 180, 270) -> FrameQualityIssue.INVALID_FRAME
            prior == null -> FrameQualityIssue.NO_FRAMES
            frame.frameId <= prior.frameId || frame.epochMs <= prior.epochMs -> FrameQualityIssue.NON_MONOTONIC
            frame.frameId != prior.frameId + 1L || frame.epochMs - prior.epochMs > maxFrameGapMs -> FrameQualityIssue.FRAME_GAP
            frame.rotationDegrees != prior.rotationDegrees -> FrameQualityIssue.ROTATION_CHANGED
            frame.width != prior.width || frame.height != prior.height -> FrameQualityIssue.GEOMETRY_CHANGED
            else -> FrameQualityIssue.NONE
        }
        // Bad frames do not advance transport readiness. A repeated/out-of-order frame must
        // NOT poison the baseline for the next valid frame.
        if (issue == FrameQualityIssue.NON_MONOTONIC || issue == FrameQualityIssue.INVALID_FRAME ||
            issue == FrameQualityIssue.NO_SESSION) {
            latest = latest.copy(anomalyCount = latest.anomalyCount + 1,
                stableFrameStreak = 0, lastIssue = issue)
            return latest
        }
        previous = frame
        latest = latest.copy(
            totalFrames = latest.totalFrames + 1L,
            stableFrameStreak = if (issue == FrameQualityIssue.NONE) {
                (latest.stableFrameStreak + 1).coerceAtMost(requiredStableFrames)
            } else 1,
            anomalyCount = latest.anomalyCount + if (issue == FrameQualityIssue.NONE ||
                issue == FrameQualityIssue.NO_FRAMES) 0 else 1,
            lastFrameAtEpochMs = frame.epochMs,
            lastIssue = issue
        )
        return latest
    }

    fun assess(nowEpochMs: Long, captureActive: Boolean): FrameQualityPresentation {
        return FrameQualityReadiness.assess(latest, nowEpochMs, captureActive,
            requiredStableFrames, staleAfterMs)
    }

    fun snapshot(): FrameQualitySnapshot = latest
}

/** Stateless presentation of the same trusted snapshot on the UI clock. */
object FrameQualityReadiness {
    fun assess(latest: FrameQualitySnapshot, nowEpochMs: Long, captureActive: Boolean,
        requiredStableFrames: Int = 12, staleAfterMs: Long = 1800): FrameQualityPresentation {
        if (!captureActive) return FrameQualityPresentation(FrameQualityLevel.INACTIVE,
            "Observação inativa", "Inicia uma sessão com autorização do Android.", false)
        val last = latest.lastFrameAtEpochMs
            ?: return FrameQualityPresentation(FrameQualityLevel.WAITING,
                "À espera de frames", "Captura iniciada; ainda sem dados de imagem.", false)
        val age = nowEpochMs - last
        if (age < 0L) return FrameQualityPresentation(FrameQualityLevel.DEGRADED,
            "Relógio inconsistente", "Timestamps fora de ordem; sem análise automática.", false)
        if (age > staleAfterMs) return FrameQualityPresentation(FrameQualityLevel.STALE,
            "Frames interrompidos", "Sem frames recentes; verificar jogo ou captura.", false)
        if (latest.stableFrameStreak < requiredStableFrames) {
            val detail = if (latest.lastIssue == FrameQualityIssue.NONE ||
                latest.lastIssue == FrameQualityIssue.NO_FRAMES) {
                "A estabilizar sinal (${latest.stableFrameStreak}/$requiredStableFrames frames)."
            } else "Sinal interrompido (${latest.lastIssue.name}); a recuperar."
            return FrameQualityPresentation(FrameQualityLevel.DEGRADED,
                "Sinal ainda não estável", detail, false)
        }
        return FrameQualityPresentation(FrameQualityLevel.STABLE,
            "Transporte de frames estável",
            "Apenas captura estável; reconhecimento Pokémon ainda indisponível.", true)
    }
}
