package com.rui.pvpgo.domain

import com.rui.pvpgo.engine.League

data class MatchupScenario(
    val league: League,
    val ownShields: Int = 1,
    val opponentShields: Int = 1,
    val ownStartingEnergy: Int = 0,
    val opponentStartingEnergy: Int = 0
) {
    init {
        require(ownShields in 0..2)
        require(opponentShields in 0..2)
        require(ownStartingEnergy in 0..100)
        require(opponentStartingEnergy in 0..100)
    }
}

enum class MatchupBand { VERY_FAVORABLE, FAVORABLE, CLOSE, UNFAVORABLE, VERY_UNFAVORABLE }

data class MatchupEvaluation(
    val ownedPokemonId: String,
    val opponentSpeciesId: String,
    val scenario: MatchupScenario,
    /** PvPoke-style 0..1000 Battle Rating. This is not a win probability. */
    val battleRating: Int,
    val opponentBattleRating: Int,
    val outcome: BattleOutcome,
    val band: MatchupBand,
    val turns: Int,
    val explanation: String? = null
) {
    init {
        require(battleRating in 0..1000)
        require(opponentBattleRating in 0..1000)
        require(turns >= 0)
    }
}

/**
 * Contract for a real battle simulator / matchup data provider.
 * Implementations must expose a Battle Rating or other named metric, never a fake win-probability percentage.
 */
interface MatchupEvaluator {
    fun evaluate(
        pokemon: OwnedPokemon,
        opponentSpeciesId: String,
        scenario: MatchupScenario
    ): MatchupEvaluation?
}

data class TeamRecommendation(
    val team: SavedTeam,
    val scorePercent: Double,
    val strengths: List<String>,
    val risks: List<String>,
    val rationale: String
) {
    init { require(scorePercent in 0.0..100.0) }
}

/**
 * Contract for the future Team Advisor. The recommendation engine must score only Pokémon
 * that exist in the user's collection and must keep its battle/meta assumptions explicit.
 */
interface TeamRecommendationEngine {
    fun recommend(
        collection: List<OwnedPokemon>,
        builds: List<PvpBuildPlan>,
        battleHistory: List<BattleRecord>,
        league: League,
        onlyReady: Boolean = true,
        allowXl: Boolean = true
    ): List<TeamRecommendation>
}

/** Move-aware opponent description for real simulation. Unknown fields stay null rather than being guessed. */
data class OpponentBuild(
    val speciesId: String,
    val fastMoveId: String? = null,
    val chargedMoveIds: List<String> = emptyList(),
    val iv: com.rui.pvpgo.engine.IvSpread? = null,
    val level: Double? = null,
    val isShadow: Boolean? = null
) {
    init {
        require(speciesId.isNotBlank())
        require(chargedMoveIds.size <= 2)
    }
}

/**
 * The future simulator should prefer this contract: it receives the exact moves on the owned Pokémon
 * and can receive a concrete opponent moveset instead of silently inventing one.
 */
interface MoveAwareMatchupEvaluator {
    fun evaluate(
        pokemon: OwnedPokemon,
        opponent: OpponentBuild,
        scenario: MatchupScenario
    ): MatchupEvaluation?
}
