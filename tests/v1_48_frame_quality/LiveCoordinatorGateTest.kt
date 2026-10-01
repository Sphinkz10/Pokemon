import com.rui.pvpgo.live.*
import com.rui.pvpgo.engine.*
import kotlin.coroutines.*

private var checks=0
private fun ok(v:Boolean,s:String){checks++;if(!v)error("FAIL $checks $s")}
private fun runSuspend(block:suspend()->Unit){
    var failure:Throwable?=null
    block.startCoroutine(object:Continuation<Unit>{
        override val context: CoroutineContext = EmptyCoroutineContext
        override fun resumeWith(result:Result<Unit>){failure=result.exceptionOrNull()}
    })
    failure?.let { throw it }
}
private class Capture(private val frames: List<CapturedFrame>):ScreenCaptureGateway {
    var idx=0
    override suspend fun start():Result<Unit> = Result.success(Unit)
    override suspend fun stop() {}
    override suspend fun nextFrame():CapturedFrame? = frames.getOrNull(idx++)
}
private class Analyzer:BattleFrameAnalyzer {
    override suspend fun analyze(frame:CapturedFrame,previousState:LiveBattleState):List<SemanticObservationCandidate<LiveBattleEvent>> =
        listOf(SemanticObservationCandidate("certified-in-test",frame.sessionId ?: "",
            "enemy-shield:turn10",frame.frameId,frame.capturedAtEpochMs,0.99,LiveBattleEvent()))
}
private class Overlay:CompanionOverlayGateway {
    var updates=0
    override suspend fun showCollapsed(state:LiveBattleState){updates++}
    override suspend fun showExpanded(state:LiveBattleState){}
    override suspend fun hide(){}
}
fun main(){
    val frames=(1..20).map { CapturedFrame(it.toLong(),1000+it*100L,390,844,0,"session-z") }
    runSuspend {
        val overlay=Overlay()
        val denied=LiveBattleCoordinator(Capture(frames), Analyzer(), overlay, LiveBattleReducer())
        repeat(20) { denied.processOneFrame() }
        ok(denied.state.acceptedEvents==0,"default DENY never advances engine")
        ok(overlay.updates==20,"UI receives no trusted result")
        denied.reset()
        ok(denied.state.acceptedEvents==0,"reset to clean state")
        val enabled=LiveBattleCoordinator(Capture(frames), Analyzer(), Overlay(), LiveBattleReducer(),
            LiveSemanticAdmissionGate(setOf("certified-in-test")))
        repeat(11){enabled.processOneFrame()}
        ok(enabled.state.acceptedEvents==0,"until 12 stable frames")
        repeat(2){enabled.processOneFrame()}
        ok(enabled.state.acceptedEvents==0,"temporal confirmation pending")
        enabled.processOneFrame()
        ok(enabled.state.acceptedEvents==1,"exactly one verified event admitted")
        repeat(6){enabled.processOneFrame()}
        ok(enabled.state.acceptedEvents==1,"no duplicate admission")
        enabled.reset()
        ok(enabled.state.acceptedEvents==0,"reset clears events")
        val missingSession=(1..20).map { CapturedFrame(it.toLong(),1000+it*100L,390,844,0,null) }
        val noSession=LiveBattleCoordinator(Capture(missingSession),Analyzer(),Overlay(),LiveBattleReducer(),
            LiveSemanticAdmissionGate(setOf("certified-in-test")))
        repeat(20){noSession.processOneFrame()}
        ok(noSession.state.acceptedEvents==0,"cannot bridge without session ID")
    }
    println("COMPANION_V148_COORDINATOR_GATE_PASS checks=$checks")
}
