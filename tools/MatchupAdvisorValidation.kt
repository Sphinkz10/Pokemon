import com.rui.pvpgo.CollectionMatchupAdvisor
import com.rui.pvpgo.LocalBattleMatchupEvaluator
import com.rui.pvpgo.domain.BattleOutcome
import com.rui.pvpgo.domain.MatchupAdvisorStatus
import com.rui.pvpgo.domain.MatchupBand
import com.rui.pvpgo.domain.MatchupEvaluation
import com.rui.pvpgo.domain.MatchupExclusionReason
import com.rui.pvpgo.domain.MatchupScenario
import com.rui.pvpgo.domain.MoveAwareMatchupEvaluator
import com.rui.pvpgo.domain.OpponentBuild
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.engine.IvSpread
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.PokemonSpecies
import com.rui.pvpgo.engine.PvpMove

private fun checkThat(condition: Boolean, message: String) {
    if (!condition) error(message)
}

private fun species(
    id: String,
    types: List<String>,
    atk: Int = 100,
    def: Int = 100,
    hp: Int = 100
) = PokemonSpecies(
    dex = id.hashCode().ushr(1) % 10000,
    name = id,
    speciesId = id,
    types = types,
    baseAttack = atk,
    baseDefense = def,
    baseStamina = hp
)

private fun owned(
    id: String,
    speciesId: String,
    level: Double? = 20.0,
    fast: String? = "FAST_NORMAL",
    charged: List<String> = listOf("CHARGED_NORMAL")
) = OwnedPokemon(
    id = id,
    speciesId = speciesId,
    iv = IvSpread(0, 0, 0),
    level = level,
    fastMoveId = fast,
    chargedMoveIds = charged,
    secondChargedMoveUnlocked = charged.size > 1,
    createdAtEpochMs = 1L
)

private class StubEvaluator(
    private val ratings: Map<String, Int>
) : MoveAwareMatchupEvaluator {
    override fun evaluate(
        pokemon: OwnedPokemon,
        opponent: OpponentBuild,
        scenario: MatchupScenario
    ): MatchupEvaluation? {
        val rating = ratings[pokemon.id] ?: return null
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
            turns = 20,
            explanation = "stub"
        )
    }
}

fun main() {
    val catalog = listOf(
        species("target", listOf("grass")),
        species("best", listOf("fire"), atk = 110, def = 105, hp = 110),
        species("mid", listOf("normal")),
        species("weak", listOf("water")),
        species("over", listOf("normal"), atk = 300, def = 300, hp = 300),
        species("mimikyu", listOf("ghost", "fairy"))
    )
    val opponent = OpponentBuild(
        speciesId = "target",
        fastMoveId = "FAST_NORMAL",
        chargedMoveIds = listOf("CHARGED_NORMAL"),
        iv = IvSpread(0, 0, 0),
        level = 20.0,
        isShadow = false
    )
    val scenario = MatchupScenario(League.GREAT, ownShields = 1, opponentShields = 1)

    val collection = listOf(
        owned("p-best", "best"),
        owned("p-mid", "mid"),
        owned("p-weak", "weak"),
        owned("p-over", "over", level = 50.0),
        owned("p-incomplete", "mid", fast = null),
        owned("p-special", "mimikyu"),
        owned("p-unknown", "missing")
    )
    val advisor = CollectionMatchupAdvisor(
        catalog,
        StubEvaluator(mapOf("p-best" to 760, "p-mid" to 560, "p-weak" to 410))
    )
    val result = advisor.recommend(collection, opponent, scenario, limit = 2)

    checkThat(result.status == MatchupAdvisorStatus.READY, "Expected READY")
    checkThat(result.certificationRoute == "INDEPENDENT_FALLBACK", "Expected F44B route")
    checkThat(result.recommendations.map { it.ownedPokemonId } == listOf("p-best", "p-mid"), "Ranking/limit mismatch")
    checkThat(result.recommendations.map { it.position } == listOf(1, 2), "Positions must be contiguous")
    checkThat(result.recommendations.first().why.contains("não é probabilidade"), "Explanation must reject probability wording")

    val reasons = result.exclusions.associate { it.ownedPokemonId to it.reason }
    checkThat(reasons["p-over"] == MatchupExclusionReason.LEAGUE_CP_EXCEEDED, "Over-cap exclusion missing")
    checkThat(reasons["p-incomplete"] == MatchupExclusionReason.INCOMPLETE_BUILD, "Incomplete exclusion missing")
    checkThat(reasons["p-special"] == MatchupExclusionReason.SPECIAL_MECHANIC_GUARD, "Special guard exclusion missing")
    checkThat(reasons["p-unknown"] == MatchupExclusionReason.UNKNOWN_SPECIES, "Unknown species exclusion missing")

    val incompleteOpponent = opponent.copy(iv = null)
    val refused = advisor.recommend(collection, incompleteOpponent, scenario)
    checkThat(refused.status == MatchupAdvisorStatus.REFUSED, "Incomplete opponent must be refused")
    checkThat(refused.recommendations.isEmpty(), "Refused request must not recommend")
    checkThat(refused.refusalReason?.contains("OpponentBuild incompleto") == true, "Expected explicit refusal reason")

    val specialOpponent = opponent.copy(speciesId = "mimikyu")
    val specialRefused = advisor.recommend(collection, specialOpponent, scenario)
    checkThat(specialRefused.status == MatchupAdvisorStatus.REFUSED, "Special opponent must be refused")

    // Real evaluator integration: proves F49 is connected to the validated Battle Engine rather than
    // only to ranking/filtering scaffolding.
    val moves = listOf(
        PvpMove("FAST_NORMAL", "Fast Normal", type = "normal", power = 4, energyGain = 8, turns = 2),
        PvpMove("CHARGED_NORMAL", "Charged Normal", type = "normal", power = 55, energyCost = 40),
        PvpMove("FAST_FIRE", "Fast Fire", type = "fire", power = 6, energyGain = 8, turns = 2),
        PvpMove("CHARGED_FIRE", "Charged Fire", type = "fire", power = 70, energyCost = 45),
        PvpMove("FAST_GRASS", "Fast Grass", type = "grass", power = 5, energyGain = 8, turns = 2),
        PvpMove("CHARGED_GRASS", "Charged Grass", type = "grass", power = 65, energyCost = 45)
    )
    val realCollection = listOf(
        owned("real-fire", "best", fast = "FAST_FIRE", charged = listOf("CHARGED_FIRE")),
        owned("real-normal", "mid")
    )
    val realOpponent = opponent.copy(fastMoveId = "FAST_GRASS", chargedMoveIds = listOf("CHARGED_GRASS"))
    val realAdvisor = CollectionMatchupAdvisor(catalog, LocalBattleMatchupEvaluator(catalog, moves))
    val real = realAdvisor.recommend(realCollection, realOpponent, scenario, limit = 2)
    checkThat(real.status == MatchupAdvisorStatus.READY, "Real BattleEngine integration must produce advice")
    checkThat(real.recommendations.size == 2, "Real evaluator should evaluate both complete candidates")
    checkThat(real.recommendations.all { it.evaluation.battleRating in 0..1000 }, "Ratings out of range")
    checkThat(real.recommendations.all { it.evaluation.opponentSpeciesId == "target" }, "Opponent mismatch")

    println("MATCHUP_ADVISOR_F49_OK")
    println("route=${result.certificationRoute} top=${result.recommendations.joinToString { "${it.ownedPokemonId}:${it.evaluation.battleRating}" }} exclusions=${result.exclusions.size}")
    println("real=${real.recommendations.joinToString { "${it.ownedPokemonId}:${it.evaluation.outcome}:${it.evaluation.battleRating}" }}")
}
