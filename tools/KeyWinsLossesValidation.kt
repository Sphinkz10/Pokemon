import com.rui.pvpgo.KeyWinsLossesAnalyzer
import com.rui.pvpgo.domain.BattleOutcome
import com.rui.pvpgo.domain.MatchupBand
import com.rui.pvpgo.domain.MatchupEvaluation
import com.rui.pvpgo.domain.MatchupScenario
import com.rui.pvpgo.domain.MetaBuildEntry
import com.rui.pvpgo.domain.MoveAwareMatchupEvaluator
import com.rui.pvpgo.domain.OpponentBuild
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.domain.VersionedMetaGroup
import com.rui.pvpgo.engine.IvSpread
import com.rui.pvpgo.engine.League

private fun checkThat(condition: Boolean, message: String) { if (!condition) error(message) }

private fun opponent(id: String) = OpponentBuild(
    speciesId = id,
    fastMoveId = "FAST",
    chargedMoveIds = listOf("CHARGED"),
    iv = IvSpread(0, 0, 0),
    level = 20.0,
    isShadow = false
)

private class Stub : MoveAwareMatchupEvaluator {
    private val ratings = mapOf("alpha" to 720, "beta" to 430, "gamma" to 505)
    override fun evaluate(pokemon: OwnedPokemon, opponent: OpponentBuild, scenario: MatchupScenario): MatchupEvaluation? {
        if (opponent.speciesId == "guarded") return null
        val rating = ratings[opponent.speciesId] ?: return null
        val outcome = when {
            rating > 500 -> BattleOutcome.WIN
            rating < 500 -> BattleOutcome.LOSS
            else -> BattleOutcome.DRAW
        }
        val band = when {
            rating >= 650 -> MatchupBand.VERY_FAVORABLE
            rating >= 550 -> MatchupBand.FAVORABLE
            rating >= 450 -> MatchupBand.CLOSE
            rating >= 350 -> MatchupBand.UNFAVORABLE
            else -> MatchupBand.VERY_UNFAVORABLE
        }
        return MatchupEvaluation(
            ownedPokemonId = pokemon.id,
            opponentSpeciesId = opponent.speciesId,
            scenario = scenario,
            battleRating = rating,
            opponentBattleRating = 1000 - rating,
            outcome = outcome,
            band = band,
            turns = 25
        )
    }
}

fun main() {
    val meta = VersionedMetaGroup(
        id = "great-test-s1",
        league = League.GREAT,
        seasonId = "test-s1",
        sourceName = "fixture",
        sourceVersion = "1",
        generatedAtEpochMs = 1L,
        entries = listOf(
            MetaBuildEntry("alpha-standard", opponent("alpha"), weight = 3.0),
            MetaBuildEntry("beta-standard", opponent("beta"), weight = 2.0),
            MetaBuildEntry("gamma-standard", opponent("gamma"), weight = 1.0),
            MetaBuildEntry("guarded-standard", opponent("guarded"), weight = 4.0)
        )
    )
    val owned = OwnedPokemon(
        id = "mine",
        speciesId = "mine-species",
        iv = IvSpread(0, 0, 0),
        level = 20.0,
        fastMoveId = "FAST",
        chargedMoveIds = listOf("CHARGED"),
        createdAtEpochMs = 1L
    )
    val scenario = MatchupScenario(League.GREAT)
    val report = KeyWinsLossesAnalyzer(Stub()).analyze(owned, meta, scenario)

    checkThat(report.certificationRoute == "INDEPENDENT_FALLBACK", "Expected independent route")
    checkThat(report.evaluatedCount == 3 && report.totalCount == 4, "Coverage counts mismatch")
    checkThat(report.evaluatedMetaWeight == 6.0 && report.totalMetaWeight == 10.0, "Meta weights mismatch")
    checkThat(kotlin.math.abs(report.evaluatedMetaCoveragePercent - 60.0) < 1e-9, "Coverage percent mismatch")
    checkThat(report.keyWins.map { it.metaEntry.key } == listOf("alpha-standard", "gamma-standard"), "Wins mismatch")
    checkThat(report.keyLosses.map { it.metaEntry.key } == listOf("beta-standard"), "Losses mismatch")
    checkThat(report.closeMatchups.map { it.metaEntry.key } == listOf("gamma-standard"), "Close mismatch")
    checkThat(report.unresolved.single().metaEntryKey == "guarded-standard", "Unresolved mismatch")

    val duplicateRejected = runCatching {
        VersionedMetaGroup(
            id = "bad",
            league = League.GREAT,
            seasonId = "s",
            sourceName = "fixture",
            sourceVersion = "1",
            generatedAtEpochMs = 1L,
            entries = listOf(
                MetaBuildEntry("dup", opponent("alpha")),
                MetaBuildEntry("dup", opponent("beta"))
            )
        )
    }.isFailure
    checkThat(duplicateRejected, "Duplicate meta keys must be rejected")

    val leagueMismatchRejected = runCatching {
        KeyWinsLossesAnalyzer(Stub()).analyze(owned, meta, MatchupScenario(League.ULTRA))
    }.isFailure
    checkThat(leagueMismatchRejected, "Meta/scenario league mismatch must be rejected")

    println("KEY_WINS_LOSSES_F50_F51_OK")
    println("meta=${meta.id}@${meta.sourceVersion} coverage=${report.evaluatedMetaCoveragePercent}% wins=${report.keyWins.size} losses=${report.keyLosses.size} unresolved=${report.unresolved.size}")
}
