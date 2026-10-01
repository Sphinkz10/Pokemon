import com.rui.pvpgo.engine.*

fun main() {
    val matrix = BattleParityFixtureSuite.run()
    val ids = listOf(
        "PVPOKE-ACTION-SURVIVAL-URGENCY",
        "PVPOKE-TIMING-ENERGY-CAP-NO-DELAY",
        "PVPOKE-ACTION-IMMEDIATE-LETHAL",
        "PVPOKE-ACTION-LONG-CYCLE",
        "PVPOKE-ACTION-LONG-CYCLE-AVOID-DEBUFF"
    )
    val byId = matrix.results.associateBy { it.id }
    ids.forEach { id ->
        val row = byId.getValue(id)
        check(row.status == FixtureStatus.PASS) { "$id expected=${row.expected} actual=${row.actual}" }
        check(row.evidenceKind == ParityEvidenceKind.SOURCE_RULE)
    }
    val parity = BattleParityGate.current()
    check(parity.capabilities.first { it.id == "action-pre-dp" }.status == ParityStatus.VERIFIED)
    check(parity.capabilities.first { it.id == "action-policy" }.status == ParityStatus.PARTIAL)
    check(parity.matchupAdviceGateOpen)
    println("ACTION_LOGIC_PHASE1_V1_9_COMPAT_OK")
    println("fixtures=${ids.size} sourceRules=${matrix.sourceRulePassCount}/${matrix.sourceRuleResults.size} verified=${parity.verifiedCount} partial=${parity.partialCount} pending=${parity.pendingCount}")
}
