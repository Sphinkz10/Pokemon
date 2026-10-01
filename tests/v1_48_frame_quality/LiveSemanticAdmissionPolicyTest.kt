import com.rui.pvpgo.live.*

private var checks = 0
private fun ok(value:Boolean, reason:String){checks++;if(!value)error("FAIL $checks $reason")}
private val stable = FrameQualityPresentation(FrameQualityLevel.STABLE, "ok", "transport only", true)
private val weak = FrameQualityPresentation(FrameQualityLevel.DEGRADED, "bad", "weak", false)
private fun cand(frame:Long, confidence:Double = .99, session:String = "A", detector:String = "validated-test", key:String="opponent:a:turn10") =
    SemanticObservationCandidate(detector, session, key, frame, 1000 + frame*100, confidence, "MOCK_EVENT")
fun main(){
    val denied=LiveSemanticAdmissionGate<String>()
    ok(denied.consider(cand(1), 1300, stable, "A").reason=="DETECTOR_NOT_APPROVED","default deny")
    val g=LiveSemanticAdmissionGate<String>(setOf("validated-test"))
    ok(g.consider(cand(1), 1300, weak, "A").reason=="FRAME_QUALITY_INSUFFICIENT","weak")
    ok(g.consider(cand(1), 1300, stable, "B").reason=="SESSION_MISMATCH","session")
    ok(g.consider(cand(1, confidence=.2),1300,stable,"A").reason=="UNTRUSTED_CANDIDATE","low confidence")
    ok(g.consider(cand(1, confidence=Double.NaN),1300,stable,"A").reason=="UNTRUSTED_CANDIDATE","nan")
    ok(g.consider(cand(1),9999,stable,"A").reason=="STALE_EVIDENCE","stale")
    ok(g.consider(cand(1),1100,stable,"A").confirmations==1,"first")
    ok(g.consider(cand(1),1100,stable,"A").reason=="REPEATED_FRAME","duplicates")
    ok(g.consider(cand(2),1200,stable,"A").confirmations==2,"second")
    val accepted=g.consider(cand(3),1300,stable,"A")
    ok(accepted.reason=="ADMITTED"&&accepted.admitted=="MOCK_EVENT","third admitted")
    ok(g.consider(cand(4),1400,stable,"A").reason=="EVENT_ALREADY_ADMITTED","no double charge")
    ok(g.consider(cand(1, key="shield:turn20"),1100,stable,"A").confirmations==1,"fresh event")
    ok(g.consider(cand(2, key="shield:turn20"),1200,weak,"A").reason=="FRAME_QUALITY_INSUFFICIENT","reset on weak")
    ok(g.consider(cand(3, key="shield:turn20"),1300,stable,"A").confirmations==1,"must regain confirmations")
    ok(g.consider(cand(4, key="shield:turn20", detector="untrusted"),1400,stable,"A").reason=="DETECTOR_NOT_APPROVED","unapproved detector")
    ok(g.consider(cand(1,session="B"),1300,stable,"B").confirmations==1,"new session cleans duplicate keys")
    ok(g.consider(cand(2,session="B"),1300,stable,"B").confirmations==2,"session still independent")
    ok(g.consider(cand(3,session="B"),1300,stable,"B").admitted!=null,"admission on new session")
    g.reset()
    ok(g.consider(cand(1),1100,stable,"A").confirmations==1,"manual reset")
    println("COMPANION_V148_SEMANTIC_ADMISSION_PASS checks=$checks")
}
