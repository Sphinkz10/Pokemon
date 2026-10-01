import com.rui.pvpgo.engine.*

fun main() {
    val matrix = BattleParityFixtureSuite.run()
    val ids = listOf(
        "PVPOKE-POST-BAIT-BUILDUP",
        "PVPOKE-POST-NO-BAIT-REORDER",
        "PVPOKE-POST-DEFER-SELF-DEBUFF",
        "PVPOKE-POST-STACK-SELF-DEBUFF",
        "PVPOKE-POST-FARMDOWN-FORCE-BOOST"
    )
    val byId = matrix.results.associateBy { it.id }
    ids.forEach { id ->
        val row = byId.getValue(id)
        check(row.status == FixtureStatus.PASS) { "$id expected=${row.expected} actual=${row.actual}" }
        check(row.evidenceKind == ParityEvidenceKind.SOURCE_RULE)
    }
    val parity = BattleParityGate.current()
    check(parity.capabilities.first { it.id == "action-post-plan" }.status == ParityStatus.VERIFIED)
    check(parity.capabilities.first { it.id == "action-policy" }.status == ParityStatus.PARTIAL)
    check(parity.matchupAdviceGateOpen)
    println("POST_PLAN_V1_9_OK")
    println("fixtures=${ids.size} sourceRules=${matrix.sourceRulePassCount}/${matrix.sourceRuleResults.size} verified=${parity.verifiedCount} partial=${parity.partialCount} pending=${parity.pendingCount}")
}
