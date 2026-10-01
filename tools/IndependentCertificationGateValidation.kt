import com.rui.pvpgo.engine.*

fun main() {
    val report = BattleAdviceCertificationGate.current()
    check(report.exactPin.status == CertificationRouteStatus.BLOCKED)
    check(report.independentFallback.status == CertificationRouteStatus.CERTIFIED)
    check(report.authoritativeStandard1v1AdviceOpen)
    check(report.selectedRoute == CertificationRoute.INDEPENDENT_FALLBACK)

    val req = report.independentFallback.requirements.associateBy { it.id }
    check(req.getValue("standard-mechanics").passed)
    check(req.getValue("special-mechanics").passed)
    check(req.getValue("randomized-cases").passed)
    check(req.getValue("mutation-sensitivity").passed)
    check(req.getValue("fast-outcome-battles").passed)
    check(req.getValue("charged-outcome-scenarios").passed)
    check(req.getValue("baseline-policy-second-implementation").passed)
    check(req.getValue("queued-action-second-implementation").passed)
    check(req.getValue("unexplained-critical-divergences").passed)


    // Architecture proof: the real V1.29 manifest opens through the independent route while exact-pin remains blocked.
    val certified = BattleAdviceCertificationGate.evaluate(BattleParityGate.current(), IndependentCertificationCurrent.manifest)
    check(certified.exactPin.status == CertificationRouteStatus.BLOCKED)
    check(certified.independentFallback.status == CertificationRouteStatus.CERTIFIED)
    check(certified.authoritativeStandard1v1AdviceOpen)
    check(certified.selectedRoute == CertificationRoute.INDEPENDENT_FALLBACK)

    println("INDEPENDENT_CERTIFICATION_GATE_V1_29_OK")
    println("exact=${report.exactPin.status} fallback=${report.independentFallback.status} adviceOpen=${report.authoritativeStandard1v1AdviceOpen}")
    println("passed=${req.values.count { it.passed }}/${req.size} blockers=none")
    println("certifiedRoute=${certified.selectedRoute} adviceOpen=${certified.authoritativeStandard1v1AdviceOpen}")
}
