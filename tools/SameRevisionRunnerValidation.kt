import com.rui.pvpgo.engine.*
import java.io.File

fun main() {
    check(PinnedExternalBaseline.SCHEMA_VERSION == 2)
    check(PinnedExternalBaseline.provenanceIsCurrent())

    // Static contract check against the audited exact-pin APIs and V1.29 evidence schema.
    val runner = File("tools/pvpoke_same_revision_runner.js").readText()
    check("schemaVersion: 2" in runner)
    check("winnerObj.pokemon.index" in runner)
    check("time: Number(e.time" in runner)
    check("values: jsonSafe" in runner)
    check("battle.getTurnsToWin()" in runner)
    check("battle.getWinner()" in runner)
    check("battle.getBattleRatings()" in runner)
    check("p.setIV(\"atk\"" in runner)
    check("p.setShields(shields)" in runner)
    check("attackStage:Number(a.statBuffs" in runner)
    PinnedBattleCaseCatalog.validatePinnedData()

    val matrix = BattleParityFixtureSuite.run()
    if (!PinnedExternalBaseline.isCaptured) {
        check(matrix.externalResults.size == 3)
        check(matrix.externalResults.all { it.status == FixtureStatus.BLOCKED })
        check(!matrix.externalBaselineConfirmed)
        println("SAME_REVISION_RUNNER_V1_29_READY_CAPTURE_PENDING")
    } else {
        check(PinnedExternalBaseline.results.size == 3)
        check(matrix.externalResults.none { it.status == FixtureStatus.BLOCKED })
        println("SAME_REVISION_RUNNER_V1_29_CAPTURED")
    }
}
