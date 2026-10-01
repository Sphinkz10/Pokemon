import com.rui.pvpgo.engine.*

fun main() {
    val report = ExternalActionTraceDiagnostics.run()
    check(report.snapshotVersion == PvPokeReferencePin.HISTORICAL_RESULT_VERSION)
    check(!report.externalTurnTraceAvailable)
    check(report.diagnostics.size == 3)

    val expected = mapOf(
        "EXTERNAL-AZU-FERALIGATR" to (348 to 341),
        "EXTERNAL-AZU-FERALIGATR-SHADOW" to (368 to 470),
        "EXTERNAL-AZU-WALREIN" to (366 to 371)
    )

    for (d in report.diagnostics) {
        val pair = expected.getValue(d.id)
        check(d.expectedBattleRating == pair.first)
        check(d.localBattleRating == pair.second)
        check(d.firstExternalDivergenceTurn == null)
        check(!d.externalActionTraceAvailable)
        check(d.localChargedSelections.isNotEmpty())
        check(d.localShieldDecisions.isNotEmpty())
    }

    check(report.diagnostics[0].severity == ExternalParityGapSeverity.NEAR)
    check(report.diagnostics[1].severity == ExternalParityGapSeverity.LARGE)
    check(report.diagnostics[2].severity == ExternalParityGapSeverity.NEAR)

    println("ACTION_TRACE_V1_9_OK")
    report.diagnostics.forEach { d ->
        println("${d.opponentName}: historical=${d.expectedBattleRating} local=${d.localBattleRating} delta=${d.delta} severity=${d.severity} localFinalOpp=${d.localFinalOpponentHp}/${d.localFinalOpponentMaxHp}")
        println("  charged=${d.localChargedSelections.joinToString(" | ")}")
        println("  shields=${d.localShieldDecisions.joinToString(" | ")}")
        println("  firstExternalDivergenceTurn=${d.firstExternalDivergenceTurn ?: "UNPROVEN"}")
    }
    println("nextEvidence=${report.nextEvidenceNeeded}")
}
