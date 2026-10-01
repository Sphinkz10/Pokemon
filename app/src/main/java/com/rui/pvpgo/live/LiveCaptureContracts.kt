package com.rui.pvpgo.live

import com.rui.pvpgo.engine.LiveBattleEvent
import com.rui.pvpgo.engine.LiveBattleState

/**
 * Capture/analyzer/overlay boundaries. V1.36-dev provides Android MediaProjection lifecycle + device-calibration telemetry,
 * while computer vision and overlay implementations remain device-gated.
 * Raw pixels never enter the battle engine.
 */
data class CapturedFrame(
    val frameId: Long,
    val capturedAtEpochMs: Long,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int = 0,
    /** Explicit capture-session origin; older callers without this remain untrusted. */
    val sessionId: String? = null
)

interface ScreenCaptureGateway {
    suspend fun start(): Result<Unit>
    suspend fun stop()
    suspend fun nextFrame(): CapturedFrame?
}

interface BattleFrameAnalyzer {
    /** Candidate only. The coordinator must gate every event before passing it to the engine. */
    suspend fun analyze(frame: CapturedFrame, previousState: LiveBattleState):
        List<SemanticObservationCandidate<LiveBattleEvent>>
}

interface CompanionOverlayGateway {
    suspend fun showCollapsed(state: LiveBattleState)
    suspend fun showExpanded(state: LiveBattleState)
    suspend fun hide()
}
