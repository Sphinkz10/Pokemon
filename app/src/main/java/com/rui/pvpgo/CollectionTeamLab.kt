package com.rui.pvpgo

import com.rui.pvpgo.domain.CoverageState
import com.rui.pvpgo.domain.MatchupEvaluation
import com.rui.pvpgo.domain.InvestmentPlan
import com.rui.pvpgo.domain.MatchupScenario
import com.rui.pvpgo.domain.MoveAwareMatchupEvaluator
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.domain.PlaystyleProfile
import com.rui.pvpgo.domain.PvPRuleset
import com.rui.pvpgo.domain.PvpBuildPlan
import com.rui.pvpgo.domain.SuggestedTeamRole
import com.rui.pvpgo.domain.TeamAdvisorConstraints
import com.rui.pvpgo.domain.TeamAdvisorStatus
import com.rui.pvpgo.domain.TeamAlternativeKind
import com.rui.pvpgo.domain.TeamCoverageMatrix
import com.rui.pvpgo.domain.TeamCoverageMatrixRow
import com.rui.pvpgo.domain.TeamLabAlternative
import com.rui.pvpgo.domain.TeamLabResult
import com.rui.pvpgo.domain.TeamLabStatus
import com.rui.pvpgo.domain.TeamLineupRecommendation
import com.rui.pvpgo.domain.TeamRoleSuggestion
import com.rui.pvpgo.domain.TeamStyleBias
import com.rui.pvpgo.domain.VersionedMetaGroup
import com.rui.pvpgo.domain.WhyThisTeam
import com.rui.pvpgo.engine.PokemonMath
import com.rui.pvpgo.engine.PokemonSpecies
import kotlin.math.sqrt

/**
 * F93–F99 Team Lab.
 *
 * This layer never turns 1v1 Battle Rating coverage into a 3v3 win probability. It composes the
 * certified CollectionTeamAdvisor with soft playstyle preferences, a transparent coverage matrix,
 * role heuristics and distinct alternatives.
 */
class CollectionTeamLab(
    species: List<PokemonSpecies>,
    private val evaluator: MoveAwareMatchupEvaluator
) {
    private val speciesById = species.associateBy { it.speciesId }
    private val baseAdvisor = CollectionTeamAdvisor(species, evaluator)

    fun analyze(
        collection: List<OwnedPokemon>,
        builds: List<PvpBuildPlan>,
        meta: VersionedMetaGroup,
        ruleset: PvPRuleset,
        scenario: MatchupScenario,
        constraints: TeamAdvisorConstraints = TeamAdvisorConstraints(),
        playstyle: PlaystyleProfile = PlaystyleProfile(),
        candidateTeamLimit: Int = 30,
        investmentPlansByOwnedId: Map<String, InvestmentPlan> = emptyMap()
    ): TeamLabResult {
        require(candidateTeamLimit in 3..50)

        val advisor = baseAdvisor.recommend(
            collection = collection,
            builds = builds,
            meta = meta,
            ruleset = ruleset,
            scenario = scenario,
            constraints = constraints,
            limit = candidateTeamLimit,
            investmentPlansByOwnedId = investmentPlansByOwnedId
        )

        if (advisor.status == TeamAdvisorStatus.REFUSED) {
            return TeamLabResult(
                status = TeamLabStatus.REFUSED,
                metaGroupId = meta.id,
                rulesetId = ruleset.id,
                rulesetVersion = ruleset.version,
                certificationRoute = advisor.certificationRoute,
                primary = null,
                primaryLabScore = null,
                roleSuggestions = emptyList(),
                coverageMatrix = null,
                alternatives = emptyList(),
                why = null,
                exclusions = advisor.exclusions,
                refusalReason = advisor.refusalReason
            )
        }
        if (advisor.recommendations.isEmpty()) {
            return TeamLabResult(
                status = TeamLabStatus.INSUFFICIENT_CANDIDATES,
                metaGroupId = meta.id,
                rulesetId = ruleset.id,
                rulesetVersion = ruleset.version,
                certificationRoute = advisor.certificationRoute,
                primary = null,
                primaryLabScore = null,
                roleSuggestions = emptyList(),
                coverageMatrix = null,
                alternatives = emptyList(),
                why = null,
                exclusions = advisor.exclusions
            )
        }

        val ownedById = collection.associateBy { it.id }
        val metrics = advisor.recommendations.associateWith { rec -> teamMetrics(rec, ownedById) }
        val attackRange = range(metrics.values.map { it.attack })
        val bulkRange = range(metrics.values.map { it.bulk })

        val scored = advisor.recommendations.map { rec ->
            val m = metrics.getValue(rec)
            val styleAdjustment = when (playstyle.styleBias) {
                TeamStyleBias.BALANCED -> 0.0
                TeamStyleBias.AGGRESSIVE -> normalized(m.attack, attackRange) * 5.0
                TeamStyleBias.BULKY -> normalized(m.bulk, bulkRange) * 5.0
            }
            val preferred = rec.memberOwnedPokemonIds.count { it in playstyle.preferredOwnedPokemonIds } * 2.0
            val avoided = rec.memberOwnedPokemonIds.count { it in playstyle.avoidedOwnedPokemonIds } * -6.0
            val comfort = rec.memberOwnedPokemonIds.count { it in playstyle.safeSwitchComfortOwnedPokemonIds } * 1.0
            val score = rec.coverage.coverageScorePercent + styleAdjustment + preferred + avoided + comfort
            LabScored(rec, score, m)
        }.sortedWith(
            compareByDescending<LabScored> { it.labScore }
                .thenByDescending { it.recommendation.coverage.coverageScorePercent }
                .thenBy { it.recommendation.coverage.threatMetaWeight }
                .thenBy { it.recommendation.memberOwnedPokemonIds.joinToString("|") }
        )

        val primaryScored = scored.first()
        val primary = primaryScored.recommendation
        val matrix = coverageMatrix(primary, ownedById, meta, scenario)
        val roles = suggestRoles(primary, matrix, meta, playstyle)
        val alternatives = alternatives(
            primary = primaryScored,
            scored = scored,
            collection = collection,
            builds = builds,
            meta = meta,
            ruleset = ruleset,
            scenario = scenario,
            constraints = constraints,
            playstyle = playstyle,
            ownedById = ownedById,
            investmentPlansByOwnedId = investmentPlansByOwnedId
        )
        val why = whyThisTeam(primaryScored, matrix, roles, constraints, playstyle, meta, advisor.certificationRoute)

        return TeamLabResult(
            status = TeamLabStatus.READY,
            metaGroupId = meta.id,
            rulesetId = ruleset.id,
            rulesetVersion = ruleset.version,
            certificationRoute = advisor.certificationRoute,
            primary = primary,
            primaryLabScore = primaryScored.labScore,
            roleSuggestions = roles,
            coverageMatrix = matrix,
            alternatives = alternatives,
            why = why,
            exclusions = advisor.exclusions
        )
    }

    private data class TeamMetrics(val attack: Double, val bulk: Double)
    private data class LabScored(
        val recommendation: TeamLineupRecommendation,
        val labScore: Double,
        val metrics: TeamMetrics
    )

    private fun teamMetrics(rec: TeamLineupRecommendation, ownedById: Map<String, OwnedPokemon>): TeamMetrics {
        val stats = rec.memberOwnedPokemonIds.mapNotNull { id ->
            val owned = ownedById[id] ?: return@mapNotNull null
            val sp = speciesById[owned.speciesId] ?: return@mapNotNull null
            val level = owned.level ?: return@mapNotNull null
            PokemonMath.battleStats(sp, owned.iv, level)
        }
        if (stats.isEmpty()) return TeamMetrics(0.0, 0.0)
        return TeamMetrics(
            attack = stats.map { it.attack }.average(),
            bulk = stats.map { sqrt(it.defense * it.hp.toDouble()) }.average()
        )
    }

    private fun coverageMatrix(
        team: TeamLineupRecommendation,
        ownedById: Map<String, OwnedPokemon>,
        meta: VersionedMetaGroup,
        scenario: MatchupScenario
    ): TeamCoverageMatrix {
        val rows = meta.entries.map { entry ->
            val ratings = team.memberOwnedPokemonIds.associateWith { id ->
                val owned = ownedById[id]
                owned?.let { evaluator.evaluate(it, entry.opponent, scenario)?.battleRating }
            }
            val best = ratings.filterValues { it != null }.maxByOrNull { it.value ?: -1 }
            val rating = best?.value
            TeamCoverageMatrixRow(
                metaEntryKey = entry.key,
                opponentSpeciesId = entry.opponent.speciesId,
                metaWeight = entry.weight,
                memberRatings = ratings,
                bestOwnedPokemonId = best?.key,
                bestBattleRating = rating,
                state = coverageState(rating)
            )
        }
        return TeamCoverageMatrix(meta.id, team.memberOwnedPokemonIds, rows)
    }

    private fun coverageState(rating: Int?): CoverageState = when {
        rating == null -> CoverageState.UNRESOLVED
        rating >= 650 -> CoverageState.STRONG
        rating >= 550 -> CoverageState.COVERED
        rating >= 500 -> CoverageState.CLOSE
        rating >= 350 -> CoverageState.THREAT
        else -> CoverageState.SEVERE_THREAT
    }

    private fun suggestRoles(
        team: TeamLineupRecommendation,
        matrix: TeamCoverageMatrix,
        meta: VersionedMetaGroup,
        playstyle: PlaystyleProfile
    ): List<TeamRoleSuggestion> {
        data class MemberRoleStats(val id: String, val avg: Double, val floor: Int, val strongWeight: Double)
        val weights = meta.entries.associate { it.key to it.weight }
        val stats = team.memberOwnedPokemonIds.map { id ->
            val values = matrix.rows.mapNotNull { row -> row.memberRatings[id]?.let { row to it } }
            val totalWeight = values.sumOf { (row, _) -> weights[row.metaEntryKey] ?: 0.0 }
            val avg = if (totalWeight > 0.0) values.sumOf { (row, r) -> (weights[row.metaEntryKey] ?: 0.0) * r } / totalWeight else 0.0
            val floor = values.minOfOrNull { it.second } ?: 0
            val strongWeight = values.filter { it.second >= 650 }.sumOf { (row, _) -> weights[row.metaEntryKey] ?: 0.0 }
            MemberRoleStats(id, avg, floor, strongWeight)
        }

        val safe = stats.maxWithOrNull(
            compareBy<MemberRoleStats> { it.floor + if (it.id in playstyle.safeSwitchComfortOwnedPokemonIds) 25 else 0 }
                .thenBy { it.avg }
        ) ?: error("Team must contain members")
        val remaining = stats.filter { it.id != safe.id }
        val lead = remaining.maxWithOrNull(compareBy<MemberRoleStats> { it.avg }.thenBy { it.strongWeight })!!
        val closer = remaining.single { it.id != lead.id }

        return listOf(
            TeamRoleSuggestion(
                lead.id,
                SuggestedTeamRole.LEAD,
                "Heurística: melhor Battle Rating médio ponderado entre os dois membros restantes; não é simulação de lead 3v3."
            ),
            TeamRoleSuggestion(
                safe.id,
                SuggestedTeamRole.SAFE_SWITCH,
                "Heurística: melhor piso de Battle Rating contra as entradas resolvidas${if (safe.id in playstyle.safeSwitchComfortOwnedPokemonIds) ", com bónus de conforto indicado pelo utilizador" else ""}."
            ),
            TeamRoleSuggestion(
                closer.id,
                SuggestedTeamRole.CLOSER,
                "Heurística: membro restante após Lead/Safe Switch; validar com cenários 3v3 futuros antes de tratar como papel autoritativo."
            )
        )
    }

    private fun alternatives(
        primary: LabScored,
        scored: List<LabScored>,
        collection: List<OwnedPokemon>,
        builds: List<PvpBuildPlan>,
        meta: VersionedMetaGroup,
        ruleset: PvPRuleset,
        scenario: MatchupScenario,
        constraints: TeamAdvisorConstraints,
        playstyle: PlaystyleProfile,
        ownedById: Map<String, OwnedPokemon>,
        investmentPlansByOwnedId: Map<String, InvestmentPlan>
    ): List<TeamLabAlternative> {
        val picks = mutableListOf<Pair<TeamAlternativeKind, LabScored>>()
        val bestCoverage = scored.maxByOrNull { it.recommendation.coverage.coverageScorePercent }
        if (bestCoverage != null) picks += TeamAlternativeKind.BEST_COVERAGE to bestCoverage
        val safest = scored.minWithOrNull(
            compareBy<LabScored> { it.recommendation.coverage.severeThreatMetaWeight }
                .thenBy { it.recommendation.coverage.threatMetaWeight }
                .thenByDescending { it.recommendation.coverage.coverageScorePercent }
        )
        if (safest != null) picks += TeamAlternativeKind.SAFEST to safest
        scored.maxByOrNull { it.metrics.bulk }?.let { picks += TeamAlternativeKind.BULKY to it }
        scored.maxByOrNull { it.metrics.attack }?.let { picks += TeamAlternativeKind.AGGRESSIVE to it }
        if (playstyle.preferredOwnedPokemonIds.isNotEmpty() || playstyle.safeSwitchComfortOwnedPokemonIds.isNotEmpty()) {
            scored.maxWithOrNull(compareBy<LabScored> {
                it.recommendation.memberOwnedPokemonIds.count { id -> id in playstyle.preferredOwnedPokemonIds || id in playstyle.safeSwitchComfortOwnedPokemonIds }
            }.thenBy { it.labScore })?.let { picks += TeamAlternativeKind.COMFORT to it }
        }

        if (investmentPlansByOwnedId.isNotEmpty()) {
            scored.mapNotNull { item ->
                val plans = item.recommendation.memberOwnedPokemonIds.map { investmentPlansByOwnedId[it] }
                if (plans.any { it == null || !it.exactCostKnown }) null
                else item to plans.filterNotNull().sumOf { it.costs.stardust.toLong() }
            }.minWithOrNull(compareBy<Pair<LabScored, Long>> { it.second }.thenByDescending { it.first.recommendation.coverage.coverageScorePercent })
                ?.first?.let { picks += TeamAlternativeKind.CHEAPEST to it }
        }

        if (constraints.allowXl) {
            val noXl = baseAdvisor.recommend(
                collection, builds, meta, ruleset, scenario,
                constraints = constraints.copy(allowXl = false),
                limit = 1,
                investmentPlansByOwnedId = investmentPlansByOwnedId
            ).recommendations.firstOrNull()
            if (noXl != null) {
                val metrics = teamMetrics(noXl, ownedById)
                picks += TeamAlternativeKind.NO_XL to LabScored(noXl, noXl.coverage.coverageScorePercent, metrics)
            }
        }

        val seen = mutableSetOf<Set<String>>()
        val primarySet = primary.recommendation.memberOwnedPokemonIds.toSet()
        return picks.mapNotNull { (kind, item) ->
            val ids = item.recommendation.memberOwnedPokemonIds
            val key = ids.toSet()
            if (key == primarySet || !seen.add(key)) return@mapNotNull null
            TeamLabAlternative(
                kind = kind,
                memberOwnedPokemonIds = ids,
                coverageScorePercent = item.recommendation.coverage.coverageScorePercent,
                labScore = item.labScore,
                reason = when (kind) {
                    TeamAlternativeKind.BEST_COVERAGE -> "Maior coverage score puro entre as lineups avaliadas."
                    TeamAlternativeKind.SAFEST -> "Menor peso de severe threats/threats; desempate por coverage."
                    TeamAlternativeKind.BULKY -> "Maior índice de bulk médio dos três membros entre as lineups avaliadas."
                    TeamAlternativeKind.AGGRESSIVE -> "Maior Attack médio dos três membros entre as lineups avaliadas."
                    TeamAlternativeKind.COMFORT -> "Maximiza Pokémon marcados como preferidos/confortáveis pelo utilizador."
                    TeamAlternativeKind.CHEAPEST -> "Menor Stardust futuro somado entre lineups com InvestmentPlan de custo exato; não altera o coverage factual."
                    TeamAlternativeKind.NO_XL -> "Melhor lineup encontrada ao repetir a pesquisa com XL desativado."
                }
            )
        }.take(5)
    }

    private fun whyThisTeam(
        primary: LabScored,
        matrix: TeamCoverageMatrix,
        roles: List<TeamRoleSuggestion>,
        constraints: TeamAdvisorConstraints,
        playstyle: PlaystyleProfile,
        meta: VersionedMetaGroup,
        certificationRoute: String?
    ): WhyThisTeam {
        val coverage = primary.recommendation.coverage
        val reasons = mutableListOf<String>()
        reasons += "Coverage score ${"%.1f".format(coverage.coverageScorePercent)}/100 no snapshot ${meta.id}; este valor não é probabilidade de vitória."
        reasons += "Papéis sugeridos: ${roles.joinToString { "${it.role}=${it.ownedPokemonId}" }}; são heurísticas sobre a matriz 1v1."
        if (coverage.severeThreatMetaWeight == 0.0) reasons += "Nenhuma entrada resolvida do meta ficou como severe threat (<350) para o melhor membro disponível."
        if (playstyle.styleBias != TeamStyleBias.BALANCED) reasons += "A ordenação recebeu um ajuste suave de estilo ${playstyle.styleBias}; hard constraints e coverage continuam separados."
        val preferredCount = primary.recommendation.memberOwnedPokemonIds.count { it in playstyle.preferredOwnedPokemonIds }
        if (preferredCount > 0) reasons += "$preferredCount membro(s) pertencem à lista de preferência do utilizador."

        val strengths = matrix.rows.filter { it.state == CoverageState.STRONG }.sortedByDescending { it.metaWeight }.take(4).map {
            "${it.opponentSpeciesId}: melhor resposta ${it.bestOwnedPokemonId} com Battle Rating ${it.bestBattleRating}."
        }
        val risks = matrix.rows.filter { it.state in setOf(CoverageState.THREAT, CoverageState.SEVERE_THREAT, CoverageState.UNRESOLVED) }
            .sortedByDescending { it.metaWeight }.take(4).map {
                if (it.bestBattleRating == null) "${it.opponentSpeciesId}: matchup não resolvido."
                else "${it.opponentSpeciesId}: melhor resposta ${it.bestOwnedPokemonId} fica em ${it.bestBattleRating}."
            }

        val applied = buildList {
            if (constraints.onlyReady) add("Apenas Battle Ready/Active")
            if (!constraints.allowXl) add("Sem XL (>40)")
            if (!constraints.allowSameSpecies) add("Sem repetir espécie")
            if (constraints.lockedOwnedPokemonIds.isNotEmpty()) add("Locks: ${constraints.lockedOwnedPokemonIds.sorted().joinToString()}")
            if (constraints.excludedOwnedPokemonIds.isNotEmpty()) add("Owned IDs excluídos: ${constraints.excludedOwnedPokemonIds.size}")
            if (constraints.allowedSpeciesIds.isNotEmpty()) add("Allow-list de espécies: ${constraints.allowedSpeciesIds.size}")
            if (constraints.excludedSpeciesIds.isNotEmpty()) add("Espécies excluídas: ${constraints.excludedSpeciesIds.size}")
            if (constraints.requiredAnyTags.isNotEmpty()) add("Tags exigidas: ${constraints.requiredAnyTags.sorted().joinToString()}")
            if (constraints.excludedTags.isNotEmpty()) add("Tags excluídas: ${constraints.excludedTags.sorted().joinToString()}")
            constraints.maxStardustInvestment?.let { add("Investimento máximo por Pokémon: $it Stardust") }
            if (!constraints.allowFutureEliteTm) add("Sem Elite TM futuro")
            add("Shadow policy: ${constraints.shadowPolicy}")
        }

        return WhyThisTeam(
            summary = "Team Lab escolheu ${primary.recommendation.memberOwnedPokemonIds.joinToString(" + ")} com lab score ${"%.1f".format(primary.labScore)} e coverage ${"%.1f".format(coverage.coverageScorePercent)}/100.",
            selectionReasons = reasons,
            strengths = strengths,
            risks = risks,
            constraintsApplied = applied,
            caveats = listOf(
                "Certificação usada: ${certificationRoute ?: "nenhuma"}.",
                "A matriz é composta por matchups standard 1v1; Lead/Safe Switch/Closer são heurísticas, não uma simulação 3v3.",
                "Coverage score e lab score não representam probabilidade de vitória.",
                "Mecânicas especiais guardadas continuam excluídas.",
                "Constraints de custo/Elite TM só são aplicadas quando existe InvestmentPlan exato para o candidato; custos desconhecidos são excluídos em vez de estimados."
            )
        )
    }

    private fun range(values: List<Double>): Pair<Double, Double> =
        (values.minOrNull() ?: 0.0) to (values.maxOrNull() ?: 0.0)

    private fun normalized(value: Double, range: Pair<Double, Double>): Double {
        val (min, max) = range
        return if (max <= min) 0.0 else ((value - min) / (max - min)).coerceIn(0.0, 1.0)
    }
}
