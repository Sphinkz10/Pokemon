package com.rui.pvpgo

import com.rui.pvpgo.domain.MatchupBand
import com.rui.pvpgo.domain.MatchupEvaluation
import com.rui.pvpgo.domain.MatchupScenario
import com.rui.pvpgo.domain.MoveAwareMatchupEvaluator
import com.rui.pvpgo.domain.OpponentBuild
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.engine.BattleAdviceCertificationGate
import com.rui.pvpgo.engine.BattleBuild
import com.rui.pvpgo.engine.BattleEngine
import com.rui.pvpgo.engine.BattleScenario
import com.rui.pvpgo.engine.BattleSidePolicy
import com.rui.pvpgo.engine.ChargeStrategy
import com.rui.pvpgo.engine.PokemonSpecies
import com.rui.pvpgo.engine.PvpMove
import com.rui.pvpgo.engine.ShieldStrategy
import com.rui.pvpgo.engine.StatEffectPolicy
import com.rui.pvpgo.engine.SpecialMechanicsRegistry

/**
 * Local move-aware matchup evaluator.
 * It refuses to simulate incomplete builds rather than silently inventing IVs, level, or moves.
 */
class LocalBattleMatchupEvaluator(
    species: List<PokemonSpecies>,
    moves: List<PvpMove>,
    private val engine: BattleEngine = BattleEngine()
) : MoveAwareMatchupEvaluator {
    private val speciesById = species.associateBy { it.speciesId }
    private val movesById = moves.associateBy { it.moveId }

    override fun evaluate(
        pokemon: OwnedPokemon,
        opponent: OpponentBuild,
        scenario: MatchupScenario
    ): MatchupEvaluation? {
        // F48 trust boundary: authoritative advice requires an active certification route.
        // Standard 1v1 may use the independently certified fallback while bespoke mechanics stay refused.
        val certification = BattleAdviceCertificationGate.current()
        if (!certification.authoritativeStandard1v1AdviceOpen) return null
        if (!SpecialMechanicsRegistry.isStandardAdviceSafe(pokemon.speciesId)) return null
        if (!SpecialMechanicsRegistry.isStandardAdviceSafe(opponent.speciesId)) return null
        val ownBuild = ownedBuildOrNull(pokemon, scenario.ownStartingEnergy) ?: return null
        val opponentBuild = opponentBuildOrNull(opponent, scenario.opponentStartingEnergy) ?: return null

        val result = engine.simulate(
            ownBuild,
            opponentBuild,
            BattleScenario(
                shieldsA = scenario.ownShields,
                shieldsB = scenario.opponentShields,
                policyA = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.PVPOKE_DEFAULT),
                policyB = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.PVPOKE_DEFAULT),
                statEffectPolicy = StatEffectPolicy.PVPOKE_DETERMINISTIC_METER
            )
        )
        return MatchupEvaluation(
            ownedPokemonId = pokemon.id,
            opponentSpeciesId = opponent.speciesId,
            scenario = scenario,
            battleRating = result.battleRatingA,
            opponentBattleRating = result.battleRatingB,
            outcome = result.outcomeForA,
            band = band(result.battleRatingA),
            turns = result.turns,
            explanation = "Battle Rating local 0–1000; não é probabilidade. Estratégia usa PVPOKE_BASELINE + PVPOKE_DEFAULT shields + deterministic buff meter; standard 1v1 certificado por ${certification.selectedRoute}."
        )
    }

    private fun ownedBuildOrNull(pokemon: OwnedPokemon, startingEnergy: Int): BattleBuild? {
        val species = speciesById[pokemon.speciesId] ?: return null
        val level = pokemon.level ?: return null
        val fast = pokemon.fastMoveId?.let(movesById::get) ?: return null
        val charged = pokemon.chargedMoveIds.mapNotNull(movesById::get)
        if (charged.size != pokemon.chargedMoveIds.size || charged.isEmpty()) return null
        return BattleBuild(
            key = pokemon.id,
            species = species,
            iv = pokemon.iv,
            level = level,
            fastMove = fast,
            chargedMoves = charged,
            isShadow = pokemon.isShadow || species.isShadow,
            initialEnergy = startingEnergy
        )
    }

    private fun opponentBuildOrNull(opponent: OpponentBuild, startingEnergy: Int): BattleBuild? {
        val species = speciesById[opponent.speciesId] ?: return null
        val iv = opponent.iv ?: return null
        val level = opponent.level ?: return null
        val fast = opponent.fastMoveId?.let(movesById::get) ?: return null
        val charged = opponent.chargedMoveIds.mapNotNull(movesById::get)
        if (charged.size != opponent.chargedMoveIds.size || charged.isEmpty()) return null
        return BattleBuild(
            key = "opponent:${opponent.speciesId}",
            species = species,
            iv = iv,
            level = level,
            fastMove = fast,
            chargedMoves = charged,
            isShadow = opponent.isShadow ?: species.isShadow,
            initialEnergy = startingEnergy
        )
    }

    private fun band(rating: Int): MatchupBand = when {
        rating >= 650 -> MatchupBand.VERY_FAVORABLE
        rating >= 550 -> MatchupBand.FAVORABLE
        rating >= 450 -> MatchupBand.CLOSE
        rating >= 350 -> MatchupBand.UNFAVORABLE
        else -> MatchupBand.VERY_UNFAVORABLE
    }
}
