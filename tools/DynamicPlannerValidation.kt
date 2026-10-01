import com.rui.pvpgo.engine.*

fun main() {
    val matrix = BattleParityFixtureSuite.run()
    val ids = listOf(
        "PVPOKE-DP-FARM-DOWN",
        "PVPOKE-DP-SHIELD-PATH",
        "PVPOKE-DP-GUARANTEED-BUFF",
        "PVPOKE-DP-STATE-CAP",
        "PVPOKE-DP-ENGINE-INTEGRATION"
    )
    val byId = matrix.results.associateBy { it.id }
    ids.forEach { id ->
        val row = byId.getValue(id)
        check(row.status == FixtureStatus.PASS) { "$id expected=${row.expected} actual=${row.actual}" }
        check(row.evidenceKind == ParityEvidenceKind.SOURCE_RULE)
    }
    val parity = BattleParityGate.current()
    check(parity.capabilities.first { it.id == "action-dp-core" }.status == ParityStatus.PARTIAL)
    check(parity.capabilities.first { it.id == "action-policy" }.status == ParityStatus.PARTIAL)
    check(parity.matchupAdviceGateOpen)
    println("DYNAMIC_PLANNER_V1_9_COMPAT_OK")
    println("fixtures=${ids.size} sourceRules=${matrix.sourceRulePassCount}/${matrix.sourceRuleResults.size} verified=${parity.verifiedCount} partial=${parity.partialCount} pending=${parity.pendingCount}")
}
