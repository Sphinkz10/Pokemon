package com.rui.pvpgo.live

private var assertions = 0
private fun assertTruth(value: Boolean, message: String) {
    check(value) { message }
    assertions += 1
}

fun main() {
    var state = CompanionQuickLog().forSession("capture-1")
    assertTruth(state.sessionId == "capture-1", "session started")
    assertTruth(state.notes.isEmpty(), "fresh session empty")
    state = state.append(ManualCompanionEvent.OPPONENT_SHIELD, 10)
    assertTruth(state.opponentShieldsNoted == 1, "first shield")
    state = state.append(ManualCompanionEvent.OPPONENT_SHIELD, 12)
    assertTruth(state.opponentShieldsNoted == 2, "second shield")
    state = state.append(ManualCompanionEvent.OPPONENT_SHIELD, 13)
    assertTruth(state.opponentShieldsNoted == 2, "shield cap")
    state = state.append(ManualCompanionEvent.OWN_SHIELD, 20)
    state = state.append(ManualCompanionEvent.OWN_SHIELD, 30)
    state = state.append(ManualCompanionEvent.OWN_SHIELD, 40)
    assertTruth(state.ownShieldsNoted == 2, "own shield cap")
    state = state.append(ManualCompanionEvent.OPPONENT_SWAP, 50)
    state = state.append(ManualCompanionEvent.OPPONENT_CHARGED, 60)
    assertTruth(state.notes.size == 6, "two shield pairs plus swap/charge")
    state = state.undo()
    assertTruth(state.notes.last().kind == ManualCompanionEvent.OPPONENT_SWAP, "undo last note")
    repeat(5) { state = state.append(ManualCompanionEvent.OPPONENT_KO, 70L + it) }
    assertTruth(state.opponentKoNoted == 3, "KO cap")
    assertTruth(state.forSession("capture-1") === state, "same session keeps state")
    assertTruth(state.forSession("capture-2").notes.isEmpty(), "new session isolates notes")
    assertTruth(state.clear().notes.isEmpty(), "clear manual notes")
    assertTruth(CompanionQuickLog().undo().notes.isEmpty(), "empty undo")
    assertTruth(runCatching { state.append(ManualCompanionEvent.OPPONENT_SWAP, -1L) }.isFailure, "invalid negative time")
    var bounded = CompanionQuickLog()
    repeat(120) { bounded = bounded.append(ManualCompanionEvent.OPPONENT_SWAP, it.toLong()) }
    assertTruth(bounded.notes.size == 80, "bounded journal")
    assertTruth(bounded.notes.first().atEpochMs == 40L, "latest observations retained")
    val idle = CompanionCapturePolicy.present("IDLE", 0)
    assertTruth(idle.canStart && !idle.canStop, "idle controls")
    assertTruth(!idle.captureActive && !idle.attention, "idle not observing")
    val starting = CompanionCapturePolicy.present("STARTING", 0)
    assertTruth(!starting.canStart && starting.canStop, "starting controls")
    val empty = CompanionCapturePolicy.present("CAPTURING", 0)
    assertTruth(empty.captureActive && empty.attention, "no frames warning")
    val observing = CompanionCapturePolicy.present("CAPTURING", 55)
    assertTruth(observing.captureActive && !observing.attention, "frames received")
    assertTruth(observing.detail.contains("não está disponível"), "no invented detections")
    val error = CompanionCapturePolicy.present("ERROR", 0, "projection denied")
    assertTruth(error.attention && error.detail.contains("projection denied"), "error visible")
    val stopped = CompanionCapturePolicy.present("STOPPED", 3)
    assertTruth(stopped.canStart && !stopped.canStop, "stopped restart")
    val stopping = CompanionCapturePolicy.present("STOPPING", 3)
    assertTruth(!stopping.canStart && !stopping.canStop, "stopping no actions")
    assertTruth(ManualCompanionEvent.entries.size == 5, "only manual input types")
    println("COMPANION_V147_POLICY_PASS assertions=$assertions")
}
