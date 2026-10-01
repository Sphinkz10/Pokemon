import com.rui.pvpgo.live.*

private var checks = 0
private fun checkThat(value: Boolean, label: String) {
    checks++
    if (!value) error("FAIL [$checks]: $label")
}
private fun frame(id: Long, ms: Long, session: String = "session-A", width: Int = 390,
    height: Int = 844, rotation: Int = 0) =
    FrameQualityInput(session, id, ms, width, height, rotation)

fun main() {
    val q = CaptureFrameQualityGate(requiredStableFrames = 4, maxFrameGapMs = 650, staleAfterMs = 1800)
    checkThat(q.assess(1_000, false).level == FrameQualityLevel.INACTIVE, "inactive")
    checkThat(q.assess(1_000, true).level == FrameQualityLevel.WAITING, "waiting")
    q.reset("session-A")
    for (n in 1..4) q.onFrame(frame(n.toLong(), 1000L + n * 100L))
    checkThat(q.snapshot().stableFrameStreak == 4, "4 stable frames")
    checkThat(q.assess(1450, true).transportStable, "transport stable only")
    checkThat(q.assess(4000, true).level == FrameQualityLevel.STALE, "staleness detects stopped pixels")
    q.onFrame(frame(5, 4100))
    checkThat(q.snapshot().lastIssue == FrameQualityIssue.FRAME_GAP, "large gap")
    checkThat(!q.assess(4150, true).transportStable, "gap drops readiness")
    for(n in 6..8) q.onFrame(frame(n.toLong(), 4100L + (n-5)*100L))
    checkThat(q.assess(4450, true).transportStable, "recover after consecutive good frames")
    q.onFrame(frame(9, 4500, rotation = 90))
    checkThat(q.snapshot().lastIssue == FrameQualityIssue.ROTATION_CHANGED, "rotation")
    checkThat(q.snapshot().stableFrameStreak == 1, "rotation resets streak")
    q.onFrame(frame(10, 4600, rotation = 90, width=430))
    checkThat(q.snapshot().lastIssue == FrameQualityIssue.GEOMETRY_CHANGED, "geometry")
    q.onFrame(frame(10, 4800, rotation = 90, width=430))
    checkThat(q.snapshot().lastIssue == FrameQualityIssue.NON_MONOTONIC, "duplicate id")
    checkThat(q.snapshot().stableFrameStreak == 0, "duplicate invalidates streak")
    val before = q.snapshot().lastFrameAtEpochMs
    q.onFrame(frame(11, 4700, rotation = 90, width=430))
    checkThat(q.snapshot().lastFrameAtEpochMs != before, "duplicate did not shift baseline")
    val errors = q.snapshot().anomalyCount
    q.onFrame(frame(12, -1))
    checkThat(q.snapshot().lastIssue == FrameQualityIssue.INVALID_FRAME, "bad time")
    checkThat(q.snapshot().anomalyCount == errors+1, "count anomalies")
    q.onFrame(frame(1, 5000, session = "session-B"))
    checkThat(q.snapshot().sessionId == "session-B", "session switch")
    checkThat(q.snapshot().totalFrames == 1L, "session counters reset")
    checkThat(q.snapshot().stableFrameStreak == 1, "session is not instantly stable")
    checkThat(!q.assess(4900, true).transportStable, "future timestamps not trusted")
    checkThat(!q.assess(5100, false).transportStable, "inactive forces no readiness")
    val noSession = q.onFrame(frame(2, 5200, session = ""))
    checkThat(noSession.lastIssue == FrameQualityIssue.NO_SESSION, "reject blank session")
    checkThat(q.snapshot().stableFrameStreak == 0, "blank session resets")
    // Explicitly no recognition, tactical events or persistent data APIs exist in this policy.
    val readout = q.assess(5300, true)
    checkThat(!readout.transportStable, "refusal after blank session")
    println("COMPANION_V148_FRAME_QUALITY_PASS checks=$checks")
}
