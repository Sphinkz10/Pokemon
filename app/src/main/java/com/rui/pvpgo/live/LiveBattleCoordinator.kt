package com.rui.pvpgo.live

import com.rui.pvpgo.engine.LiveBattleReducer
import com.rui.pvpgo.engine.LiveBattleEvent
import com.rui.pvpgo.engine.LiveBattleState

class LiveBattleCoordinator(
    private val capture: ScreenCaptureGateway,
    private val analyzer: BattleFrameAnalyzer,
    private val overlay: CompanionOverlayGateway,
    private val reducer: LiveBattleReducer,
    /** Default is DENY-ALL. No live visual detector has device approval yet. */
    private val admission: LiveSemanticAdmissionGate<LiveBattleEvent> = LiveSemanticAdmissionGate()
) {
    private val frameQuality = CaptureFrameQualityGate()
    private var lastSessionId: String? = null
    var state: LiveBattleState = LiveBattleState()
        private set

    suspend fun processOneFrame(): LiveBattleState {
        val frame = capture.nextFrame() ?: return state
        val session = frame.sessionId
        if (session.isNullOrBlank()) {
            // Cannot attach an event to a verified MediaProjection capture session.
            overlay.showCollapsed(state)
            return state
        }
        if (session != lastSessionId) {
            state = LiveBattleState()
            frameQuality.reset(session)
            admission.reset(session)
            lastSessionId = session
        }
        frameQuality.onFrame(FrameQualityInput(session, frame.frameId,
            frame.capturedAtEpochMs, frame.width, frame.height, frame.rotationDegrees))
        val quality = frameQuality.assess(frame.capturedAtEpochMs, true)
        // A future certified detector may produce candidates; their score alone is insufficient.
        val candidates = analyzer.analyze(frame, state)
        for (candidate in candidates) {
            val trusted = admission.consider(candidate, frame.capturedAtEpochMs, quality, session).admitted
            if (trusted != null) state = reducer.reduce(state, trusted)
        }
        overlay.showCollapsed(state)
        return state
    }

    suspend fun reset() {
        state = LiveBattleState()
        lastSessionId = null
        frameQuality.reset()
        admission.reset()
        overlay.hide()
    }
}
