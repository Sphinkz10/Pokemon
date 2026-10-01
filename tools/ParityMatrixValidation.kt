import com.rui.pvpgo.engine.*

fun main() {
    val matrix = BattleParityFixtureSuite.run()
    check(matrix.results.size == 151)
    check(matrix.passCount == 145)
    check(matrix.diffCount == 3)
    check(matrix.blockedCount == 3)
    check(matrix.sourceRuleResults.size == 142)
    check(matrix.sourceRulePassCount == 142)
    check(matrix.internalResults.size == 3)
    check(matrix.historicalExternalResults.size == 3)
    check(matrix.externalResults.size == 3)
    check(!matrix.externalBaselineConfirmed)
    check(!matrix.recommendationGateOpen)
    println("PARITY_MATRIX_OK")
    println("total=${matrix.results.size} pass=${matrix.passCount} diff=${matrix.diffCount} blocked=${matrix.blockedCount}")
    println("sourceRules=${matrix.sourceRulePassCount}/${matrix.sourceRuleResults.size} internal=${matrix.internalResults.size} historicalDiff=${matrix.historicalExternalResults.count { it.status == FixtureStatus.DIFF }} pinnedBlocked=${matrix.externalResults.count { it.status == FixtureStatus.BLOCKED }}")
}
