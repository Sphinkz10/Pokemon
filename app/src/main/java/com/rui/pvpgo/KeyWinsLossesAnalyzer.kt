package com.rui.pvpgo

import com.rui.pvpgo.domain.BattleOutcome
import com.rui.pvpgo.domain.KeyMatchup
import com.rui.pvpgo.domain.KeyWinsLossesReport
import com.rui.pvpgo.domain.MatchupBand
import com.rui.pvpgo.domain.MatchupScenario
import com.rui.pvpgo.domain.MoveAwareMatchupEvaluator
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.domain.UnresolvedMetaMatchup
import com.rui.pvpgo.domain.VersionedMetaGroup
import com.rui.pvpgo.engine.BattleAdviceCertificationGate

/** F51 — summarizes one exact owned build against a concrete, versioned F50 meta snapshot. */
class KeyWinsLossesAnalyzer(
    private val evaluator: MoveAwareMatchupEvaluator
) {
    fun analyze(
        pokemon: OwnedPokemon,
        meta: VersionedMetaGroup,
        scenario: MatchupScenario,
        limitPerSection: Int = 5
    ): KeyWinsLossesReport {
        require(limitPerSection in 1..50)
        require(meta.league == scenario.league) { "Meta league and scenario league must match" }

        val certification = BattleAdviceCertificationGate.current()
        val resolved = mutableListOf<KeyMatchup>()
        val unresolved = mutableListOf<UnresolvedMetaMatchup>()

        for (entry in meta.entries) {
            val evaluation = evaluator.evaluate(pokemon, entry.opponent, scenario)
            if (evaluation == null) {
                unresolved += UnresolvedMetaMatchup(
                    metaEntryKey = entry.key,
                    opponentSpeciesId = entry.opponent.speciesId,
                    reason = "Evaluator recusou o cenário (build/trust/special-mechanic guard)."
                )
            } else {
                resolved += KeyMatchup(entry, evaluation)
            }
        }

        val wins = resolved.filter { it.evaluation.outcome == BattleOutcome.WIN }
            .sortedWith(compareByDescending<KeyMatchup> { it.metaEntry.weight }
                .thenByDescending { it.evaluation.battleRating }
                .thenBy { it.metaEntry.key })
            .take(limitPerSection)
        val losses = resolved.filter { it.evaluation.outcome == BattleOutcome.LOSS }
            .sortedWith(compareByDescending<KeyMatchup> { it.metaEntry.weight }
                .thenBy { it.evaluation.battleRating }
                .thenBy { it.metaEntry.key })
            .take(limitPerSection)
        val close = resolved.filter {
            it.evaluation.outcome == BattleOutcome.DRAW || it.evaluation.band == MatchupBand.CLOSE
        }.sortedWith(compareByDescending<KeyMatchup> { it.metaEntry.weight }
            .thenBy { kotlin.math.abs(it.evaluation.battleRating - 500) }
            .thenBy { it.metaEntry.key })
            .take(limitPerSection)

        val resolvedKeys = resolved.map { it.metaEntry.key }.toSet()
        val evaluatedWeight = meta.entries.filter { it.key in resolvedKeys }.sumOf { it.weight }

        return KeyWinsLossesReport(
            ownedPokemonId = pokemon.id,
            metaGroupId = meta.id,
            scenario = scenario,
            certificationRoute = certification.selectedRoute?.name,
            evaluatedCount = resolved.size,
            totalCount = meta.entries.size,
            evaluatedMetaWeight = evaluatedWeight,
            totalMetaWeight = meta.totalWeight,
            keyWins = wins,
            keyLosses = losses,
            closeMatchups = close,
            unresolved = unresolved
        )
    }
}
