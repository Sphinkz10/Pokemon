import com.rui.pvpgo.CollectionTeamAdvisor
import com.rui.pvpgo.domain.BattleOutcome
import com.rui.pvpgo.domain.MatchupBand
import com.rui.pvpgo.domain.MatchupEvaluation
import com.rui.pvpgo.domain.MatchupScenario
import com.rui.pvpgo.domain.MetaBuildEntry
import com.rui.pvpgo.domain.MoveAwareMatchupEvaluator
import com.rui.pvpgo.domain.OpponentBuild
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.domain.PvPRuleset
import com.rui.pvpgo.domain.TeamAdvisorConstraints
import com.rui.pvpgo.domain.TeamAdvisorStatus
import com.rui.pvpgo.domain.TeamCandidateExclusionReason
import com.rui.pvpgo.domain.VersionedMetaGroup
import com.rui.pvpgo.engine.IvSpread
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.PokemonSpecies

private fun checkThat(condition: Boolean, message: String) { if (!condition) error(message) }

private fun species(id: String, dex: Int, type: String, tags: Set<String> = emptySet()) = PokemonSpecies(
    dex = dex, name = id, speciesId = id, types = listOf(type), tags = tags,
    fastMoveIds = listOf("FAST"), chargedMoveIds = listOf("CHARGED"),
    baseAttack = 100, baseDefense = 100, baseStamina = 100
)

private fun owned(id: String, speciesId: String, level: Double = 20.0) = OwnedPokemon(
    id = id, speciesId = speciesId, iv = IvSpread(0,0,0), level = level,
    fastMoveId = "FAST", chargedMoveIds = listOf("CHARGED"), createdAtEpochMs = 1L
)

private fun opponent(id: String) = OpponentBuild(
    speciesId = id, fastMoveId = "FAST", chargedMoveIds = listOf("CHARGED"),
    iv = IvSpread(0,0,0), level = 20.0, isShadow = false
)

private class MatrixEvaluator(private val matrix: Map<Pair<String,String>,Int>) : MoveAwareMatchupEvaluator {
    override fun evaluate(pokemon: OwnedPokemon, opponent: OpponentBuild, scenario: MatchupScenario): MatchupEvaluation? {
        val rating = matrix[pokemon.id to opponent.speciesId] ?: return null
        val outcome = when { rating > 500 -> BattleOutcome.WIN; rating < 500 -> BattleOutcome.LOSS; else -> BattleOutcome.DRAW }
        val band = when {
            rating >= 650 -> MatchupBand.VERY_FAVORABLE
            rating >= 550 -> MatchupBand.FAVORABLE
            rating >= 450 -> MatchupBand.CLOSE
            rating >= 350 -> MatchupBand.UNFAVORABLE
            else -> MatchupBand.VERY_UNFAVORABLE
        }
        return MatchupEvaluation(pokemon.id, opponent.speciesId, scenario, rating, 1000-rating, outcome, band, 20)
    }
}

fun main() {
    val catalog = listOf(
        species("a", 1, "fire"), species("b", 2, "water"), species("c", 3, "grass"),
        species("d", 4, "electric"), species("e", 5, "ice"), species("mimikyu", 778, "ghost")
    )
    val collection = listOf(
        owned("A", "a"), owned("B", "b"), owned("C", "c"), owned("D", "d"), owned("E", "e"),
        owned("SPECIAL", "mimikyu")
    )
    val matrix = mapOf(
        ("A" to "alpha") to 800, ("A" to "beta") to 300, ("A" to "gamma") to 600, ("A" to "delta") to 400,
        ("B" to "alpha") to 400, ("B" to "beta") to 800, ("B" to "gamma") to 550, ("B" to "delta") to 450,
        ("C" to "alpha") to 550, ("C" to "beta") to 450, ("C" to "gamma") to 800, ("C" to "delta") to 300,
        ("D" to "alpha") to 650, ("D" to "beta") to 650, ("D" to "gamma") to 200, ("D" to "delta") to 700,
        ("E" to "alpha") to 300, ("E" to "beta") to 300, ("E" to "gamma") to 300, ("E" to "delta") to 550
    )
    val meta = VersionedMetaGroup(
        id = "meta-team-test", league = League.GREAT, seasonId = "s1", sourceName = "fixture", sourceVersion = "1", generatedAtEpochMs = 1L,
        entries = listOf(
            MetaBuildEntry("alpha", opponent("alpha"), 3.0),
            MetaBuildEntry("beta", opponent("beta"), 3.0),
            MetaBuildEntry("gamma", opponent("gamma"), 2.0),
            MetaBuildEntry("delta", opponent("delta"), 2.0)
        ),
        rulesetId = "standard-great", rulesetVersion = "1", dataPackHashSha256 = "a".repeat(64)
    )
    val ruleset = PvPRuleset(
        id = "standard-great", version = "1", title = "Great", baseLeague = League.GREAT,
        sourceName = "fixture", sourceVersion = "1", generatedAtEpochMs = 1L
    )
    val result = CollectionTeamAdvisor(catalog, MatrixEvaluator(matrix)).recommend(
        collection = collection,
        builds = emptyList(),
        meta = meta,
        ruleset = ruleset,
        scenario = MatchupScenario(League.GREAT),
        constraints = TeamAdvisorConstraints(maxCandidatePool = 5),
        limit = 5
    )

    checkThat(result.status == TeamAdvisorStatus.READY, "Expected READY")
    checkThat(result.certificationRoute == "INDEPENDENT_FALLBACK", "Expected certified route")
    val top = result.recommendations.first()
    checkThat(top.memberOwnedPokemonIds.toSet() == setOf("A","B","D"), "Expected A/B/D as top trio, got ${top.memberOwnedPokemonIds}")
    checkThat(kotlin.math.abs(top.coverage.weightedBestBattleRating - 740.0) < 1e-9, "Weighted best rating mismatch: ${top.coverage.weightedBestBattleRating}")
    checkThat(kotlin.math.abs(top.coverage.coverageScorePercent - 74.0) < 1e-9, "Coverage score mismatch")
    checkThat(top.coverage.threatMetaWeight == 0.0, "Top team should have no <500 best-response threats")
    checkThat(top.rationale.contains("não é probabilidade"), "Rationale must reject probability interpretation")
    checkThat(result.exclusions.single { it.ownedPokemonId == "SPECIAL" }.reason == TeamCandidateExclusionReason.SPECIAL_MECHANIC_GUARD, "Special guard missing")

    val locked = CollectionTeamAdvisor(catalog, MatrixEvaluator(matrix)).recommend(
        collection, emptyList(), meta, ruleset, MatchupScenario(League.GREAT),
        constraints = TeamAdvisorConstraints(lockedOwnedPokemonIds = setOf("C"), maxCandidatePool = 5), limit = 10
    )
    checkThat(locked.recommendations.all { "C" in it.memberOwnedPokemonIds }, "Locked Pokémon must appear in every suggested trio")

    val noXlCollection = collection + owned("XL", "a", level = 41.0)
    val noXl = CollectionTeamAdvisor(catalog, MatrixEvaluator(matrix)).recommend(
        noXlCollection, emptyList(), meta, ruleset, MatchupScenario(League.GREAT),
        constraints = TeamAdvisorConstraints(allowXl = false, maxCandidatePool = 5), limit = 3
    )
    checkThat(noXl.exclusions.any { it.ownedPokemonId == "XL" && it.reason == TeamCandidateExclusionReason.XL_NOT_ALLOWED }, "XL constraint exclusion missing")

    println("TEAM_ADVISOR_F52_F53_OK")
    println("top=${top.memberOwnedPokemonIds.joinToString("+")} coverage=${top.coverage.coverageScorePercent} threats=${top.coverage.threatMetaWeight}")
    println("alternatives=${result.recommendations.size} excluded=${result.exclusions.size}")
}
