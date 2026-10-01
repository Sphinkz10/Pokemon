package com.rui.pvpgo

import com.rui.pvpgo.domain.BattleOutcome
import com.rui.pvpgo.domain.MatchupAdvisorExclusion
import com.rui.pvpgo.domain.MatchupAdvisorRecommendation
import com.rui.pvpgo.domain.MatchupAdvisorResult
import com.rui.pvpgo.domain.MatchupAdvisorStatus
import com.rui.pvpgo.domain.MatchupBand
import com.rui.pvpgo.domain.MatchupExclusionReason
import com.rui.pvpgo.domain.MatchupScenario
import com.rui.pvpgo.domain.MoveAwareMatchupEvaluator
import com.rui.pvpgo.domain.OpponentBuild
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.domain.PvPRuleset
import com.rui.pvpgo.engine.BattleAdviceCertificationGate
import com.rui.pvpgo.engine.PokemonMath
import com.rui.pvpgo.engine.PokemonSpecies
import com.rui.pvpgo.engine.SpecialMechanicsRegistry
import com.rui.pvpgo.engine.RulesetEngine

/**
 * F49 — Collection-aware Matchup Advisor.
 *
 * The advisor intentionally does not invent opponent IVs/level/moves. F50 will later provide a
 * versioned meta build when the user asks for a meta assumption. F49 only ranks exact owned builds
 * against an explicit opponent build and an explicit shield/energy scenario.
 */
class CollectionMatchupAdvisor(
    species: List<PokemonSpecies>,
    private val evaluator: MoveAwareMatchupEvaluator
) {
    private val speciesById = species.associateBy { it.speciesId }

    fun recommend(
        collection: List<OwnedPokemon>,
        opponent: OpponentBuild,
        scenario: MatchupScenario,
        limit: Int = 5,
        ruleset: PvPRuleset? = null
    ): MatchupAdvisorResult {
        require(limit in 1..50)
        if (ruleset != null) require(ruleset.baseLeague == scenario.league) { "Ruleset base league and scenario league must match" }

        val certification = BattleAdviceCertificationGate.current()
        if (!certification.authoritativeStandard1v1AdviceOpen) {
            return refused(
                opponent,
                scenario,
                certification.selectedRoute?.name,
                "Standard 1v1 Matchup Advice não tem uma rota de certificação ativa."
            )
        }

        val opponentSpecies = speciesById[opponent.speciesId]
            ?: return refused(opponent, scenario, certification.selectedRoute?.name, "Espécie adversária desconhecida: ${opponent.speciesId}.")
        if (!SpecialMechanicsRegistry.isStandardAdviceSafe(opponent.speciesId)) {
            return refused(
                opponent,
                scenario,
                certification.selectedRoute?.name,
                "O adversário usa uma mecânica especial ainda guardada para advice autoritativo."
            )
        }
        val opponentIv = opponent.iv
        val opponentLevel = opponent.level
        if (opponent.fastMoveId == null || opponent.chargedMoveIds.isEmpty() || opponentIv == null || opponentLevel == null) {
            return refused(
                opponent,
                scenario,
                certification.selectedRoute?.name,
                "OpponentBuild incompleto: F49 exige IVs, nível, Fast Move e pelo menos um Charged Move explícitos."
            )
        }
        val opponentCp = PokemonMath.cp(opponentSpecies, opponentIv, opponentLevel)
        if (ruleset != null) {
            val eligibility = RulesetEngine.evaluate(ruleset, opponentSpecies, opponentLevel, opponentCp)
            if (!eligibility.eligible) {
                return refused(
                    opponent, scenario, certification.selectedRoute?.name,
                    "O build adversário não é elegível no ruleset ${ruleset.id}@${ruleset.version}: ${eligibility.reasons.joinToString()}."
                )
            }
        } else {
            scenario.league.cpCap?.let { cap ->
                if (opponentCp > cap) {
                    return refused(
                        opponent,
                        scenario,
                        certification.selectedRoute?.name,
                        "O build adversário tem $opponentCp CP e excede o limite de $cap CP da liga."
                    )
                }
            }
        }

        val exclusions = mutableListOf<MatchupAdvisorExclusion>()
        val evaluated = collection.mapNotNull { owned ->
            val ownSpecies = speciesById[owned.speciesId]
            if (ownSpecies == null) {
                exclusions += exclusion(owned, MatchupExclusionReason.UNKNOWN_SPECIES, "Espécie não existe no catálogo carregado.")
                return@mapNotNull null
            }
            if (!SpecialMechanicsRegistry.isStandardAdviceSafe(owned.speciesId)) {
                exclusions += exclusion(
                    owned,
                    MatchupExclusionReason.SPECIAL_MECHANIC_GUARD,
                    "Mecânica especial ainda não autorizada para Matchup Advice."
                )
                return@mapNotNull null
            }
            val level = owned.level
            if (level == null || owned.fastMoveId == null || owned.chargedMoveIds.isEmpty()) {
                exclusions += exclusion(
                    owned,
                    MatchupExclusionReason.INCOMPLETE_BUILD,
                    "Falta nível, Fast Move ou Charged Move; o advisor não inventa um build."
                )
                return@mapNotNull null
            }

            val cp = PokemonMath.cp(ownSpecies, owned.iv, level)
            if (ruleset != null) {
                val eligibility = RulesetEngine.evaluate(ruleset, ownSpecies, level, cp)
                if (!eligibility.eligible) {
                    exclusions += exclusion(
                        owned,
                        MatchupExclusionReason.RULESET_INELIGIBLE,
                        "Não elegível em ${ruleset.id}@${ruleset.version}: ${eligibility.reasons.joinToString()}."
                    )
                    return@mapNotNull null
                }
            } else {
                scenario.league.cpCap?.let { cap ->
                    if (cp > cap) {
                        exclusions += exclusion(
                            owned,
                            MatchupExclusionReason.LEAGUE_CP_EXCEEDED,
                            "$cp CP excede o limite de $cap CP da liga."
                        )
                        return@mapNotNull null
                    }
                }
            }

            val evaluation = evaluator.evaluate(owned, opponent, scenario)
            if (evaluation == null) {
                exclusions += exclusion(
                    owned,
                    MatchupExclusionReason.EVALUATION_REFUSED,
                    "O evaluator recusou o cenário; confirma IDs de moves, dados e trust guards."
                )
                return@mapNotNull null
            }
            Candidate(owned, cp, evaluation)
        }.sortedWith(
            compareByDescending<Candidate> { it.evaluation.battleRating }
                .thenByDescending { outcomePriority(it.evaluation.outcome) }
                .thenBy { it.evaluation.turns }
                .thenBy { it.owned.speciesId }
                .thenBy { it.owned.id }
        )

        val recommendations = evaluated.take(limit).mapIndexed { index, candidate ->
            MatchupAdvisorRecommendation(
                position = index + 1,
                ownedPokemonId = candidate.owned.id,
                speciesId = candidate.owned.speciesId,
                pokemonCp = candidate.cp,
                evaluation = candidate.evaluation,
                why = explain(candidate.evaluation.band, candidate.evaluation.battleRating, candidate.evaluation.outcome, scenario)
            )
        }

        return MatchupAdvisorResult(
            status = if (recommendations.isEmpty()) MatchupAdvisorStatus.NO_ELIGIBLE_CANDIDATES else MatchupAdvisorStatus.READY,
            opponent = opponent,
            scenario = scenario,
            certificationRoute = certification.selectedRoute?.name,
            recommendations = recommendations,
            exclusions = exclusions.sortedWith(compareBy({ it.speciesId }, { it.ownedPokemonId })),
            refusalReason = null
        )
    }

    private data class Candidate(
        val owned: OwnedPokemon,
        val cp: Int,
        val evaluation: com.rui.pvpgo.domain.MatchupEvaluation
    )

    private fun exclusion(
        owned: OwnedPokemon,
        reason: MatchupExclusionReason,
        detail: String
    ) = MatchupAdvisorExclusion(owned.id, owned.speciesId, reason, detail)

    private fun refused(
        opponent: OpponentBuild,
        scenario: MatchupScenario,
        route: String?,
        reason: String
    ) = MatchupAdvisorResult(
        status = MatchupAdvisorStatus.REFUSED,
        opponent = opponent,
        scenario = scenario,
        certificationRoute = route,
        recommendations = emptyList(),
        exclusions = emptyList(),
        refusalReason = reason
    )

    private fun outcomePriority(outcome: BattleOutcome): Int = when (outcome) {
        BattleOutcome.WIN -> 2
        BattleOutcome.DRAW -> 1
        BattleOutcome.LOSS -> 0
    }

    private fun explain(
        band: MatchupBand,
        rating: Int,
        outcome: BattleOutcome,
        scenario: MatchupScenario
    ): String {
        val headline = when (band) {
            MatchupBand.VERY_FAVORABLE -> "Vantagem muito forte neste cenário."
            MatchupBand.FAVORABLE -> "Vantagem neste cenário."
            MatchupBand.CLOSE -> "Matchup apertado; pequenas diferenças podem inverter o resultado."
            MatchupBand.UNFAVORABLE -> "Desvantagem neste cenário."
            MatchupBand.VERY_UNFAVORABLE -> "Desvantagem muito forte neste cenário."
        }
        return "$headline Outcome=$outcome · Battle Rating $rating/1000 · shields ${scenario.ownShields}-${scenario.opponentShields} · energia inicial ${scenario.ownStartingEnergy}-${scenario.opponentStartingEnergy}. Battle Rating não é probabilidade de vitória."
    }
}
