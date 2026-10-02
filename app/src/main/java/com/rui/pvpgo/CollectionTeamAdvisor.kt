package com.rui.pvpgo

import com.rui.pvpgo.domain.BuildStatus
import com.rui.pvpgo.domain.MatchupEvaluation
import com.rui.pvpgo.domain.InvestmentPlan
import com.rui.pvpgo.domain.MatchupScenario
import com.rui.pvpgo.domain.MoveAwareMatchupEvaluator
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.domain.PvPRuleset
import com.rui.pvpgo.domain.PvpBuildPlan
import com.rui.pvpgo.domain.TeamAdvisorConstraints
import com.rui.pvpgo.domain.TeamAdvisorResult
import com.rui.pvpgo.domain.TeamAdvisorStatus
import com.rui.pvpgo.domain.TeamCandidateExclusion
import com.rui.pvpgo.domain.TeamCandidateExclusionReason
import com.rui.pvpgo.domain.TeamCoverageReport
import com.rui.pvpgo.domain.TeamLineupRecommendation
import com.rui.pvpgo.domain.TeamThreat
import com.rui.pvpgo.domain.ShadowPolicy
import com.rui.pvpgo.domain.VersionedMetaGroup
import com.rui.pvpgo.engine.BattleAdviceCertificationGate
import com.rui.pvpgo.engine.PokemonMath
import com.rui.pvpgo.engine.PokemonSpecies
import com.rui.pvpgo.engine.RulesetEngine
import com.rui.pvpgo.engine.SpecialMechanicsRegistry

/** F52/F53 — collection-only trio search with explicit, versioned meta coverage. */
class CollectionTeamAdvisor(
    species: List<PokemonSpecies>,
    private val evaluator: MoveAwareMatchupEvaluator
) {
    private val speciesById = species.associateBy { it.speciesId }

    fun recommend(
        collection: List<OwnedPokemon>,
        builds: List<PvpBuildPlan>,
        meta: VersionedMetaGroup,
        ruleset: PvPRuleset,
        scenario: MatchupScenario,
        constraints: TeamAdvisorConstraints = TeamAdvisorConstraints(),
        limit: Int = 10,
        investmentPlansByOwnedId: Map<String, InvestmentPlan> = emptyMap()
    ): TeamAdvisorResult {
        require(limit in 1..50)
        require(meta.league == scenario.league && ruleset.baseLeague == scenario.league) {
            "Meta, ruleset and scenario league must match"
        }
        require(meta.rulesetId == null || (meta.rulesetId == ruleset.id && meta.rulesetVersion == ruleset.version)) {
            "Meta group ruleset reference does not match supplied ruleset"
        }

        val certification = BattleAdviceCertificationGate.current()
        if (!certification.authoritativeStandard1v1AdviceOpen) {
            return refused(meta, ruleset, certification.selectedRoute?.name, "Standard 1v1 intelligence is not certified.")
        }

        val readyOwnedIds = builds.filter {
            it.league == scenario.league && it.status in setOf(BuildStatus.READY, BuildStatus.ACTIVE)
        }.map { it.ownedPokemonId }.toSet()

        val exclusions = mutableListOf<TeamCandidateExclusion>()
        val eligible = collection.mapNotNull { owned ->
            if (owned.id in constraints.excludedOwnedPokemonIds) return@mapNotNull null
            val sp = speciesById[owned.speciesId]
            if (sp == null) {
                exclusions += exclusion(owned, TeamCandidateExclusionReason.UNKNOWN_SPECIES, "Espécie fora do Data Pack carregado.")
                return@mapNotNull null
            }
            if (constraints.allowedSpeciesIds.isNotEmpty() && owned.speciesId !in constraints.allowedSpeciesIds) {
                exclusions += exclusion(owned, TeamCandidateExclusionReason.SPECIES_CONSTRAINT, "Espécie fora da allow-list desta pesquisa.")
                return@mapNotNull null
            }
            if (owned.speciesId in constraints.excludedSpeciesIds) {
                exclusions += exclusion(owned, TeamCandidateExclusionReason.SPECIES_CONSTRAINT, "Espécie excluída pelas constraints desta pesquisa.")
                return@mapNotNull null
            }
            if (constraints.requiredAnyTags.isNotEmpty() && owned.tags.intersect(constraints.requiredAnyTags).isEmpty()) {
                exclusions += exclusion(owned, TeamCandidateExclusionReason.TAG_CONSTRAINT, "Não contém nenhuma das tags exigidas.")
                return@mapNotNull null
            }
            if (owned.tags.any { it in constraints.excludedTags }) {
                exclusions += exclusion(owned, TeamCandidateExclusionReason.TAG_CONSTRAINT, "Contém uma tag excluída pelas constraints.")
                return@mapNotNull null
            }
            val shadowAllowed = when (constraints.shadowPolicy) {
                ShadowPolicy.ANY -> true
                ShadowPolicy.NORMAL_ONLY -> !owned.isShadow
                ShadowPolicy.SHADOW_ONLY -> owned.isShadow
            }
            if (!shadowAllowed) {
                exclusions += exclusion(owned, TeamCandidateExclusionReason.SHADOW_POLICY, "Não cumpre a política Shadow selecionada: ${constraints.shadowPolicy}.")
                return@mapNotNull null
            }
            if (!SpecialMechanicsRegistry.isStandardAdviceSafe(owned.speciesId)) {
                exclusions += exclusion(owned, TeamCandidateExclusionReason.SPECIAL_MECHANIC_GUARD, "Mecânica especial ainda fora do Team Advisor autoritativo.")
                return@mapNotNull null
            }
            val level = owned.level
            if (level == null || owned.fastMoveId == null || owned.chargedMoveIds.isEmpty()) {
                exclusions += exclusion(owned, TeamCandidateExclusionReason.INCOMPLETE_BUILD, "Falta nível/Fast/Charged; não inventamos o build.")
                return@mapNotNull null
            }
            if (!constraints.allowXl && level > 40.0) {
                exclusions += exclusion(owned, TeamCandidateExclusionReason.XL_NOT_ALLOWED, "Build acima do nível 40 e a constraint sem XL está ativa.")
                return@mapNotNull null
            }
            if (constraints.onlyReady && owned.id !in readyOwnedIds) {
                exclusions += exclusion(owned, TeamCandidateExclusionReason.NOT_BATTLE_READY, "Não existe PvpBuildPlan READY/ACTIVE para esta liga.")
                return@mapNotNull null
            }
            if (constraints.maxStardustInvestment != null || !constraints.allowFutureEliteTm) {
                val investment = investmentPlansByOwnedId[owned.id]
                if (investment == null || !investment.exactCostKnown) {
                    exclusions += exclusion(owned, TeamCandidateExclusionReason.INVESTMENT_COST_UNKNOWN, "Falta um InvestmentPlan de custo exato para aplicar esta constraint.")
                    return@mapNotNull null
                }
                val maxStardust = constraints.maxStardustInvestment
                if (maxStardust != null && investment.costs.stardust > maxStardust) {
                    exclusions += exclusion(owned, TeamCandidateExclusionReason.MAX_COST_EXCEEDED, "Custo futuro ${investment.costs.stardust} Stardust excede o máximo $maxStardust.")
                    return@mapNotNull null
                }
                if (!constraints.allowFutureEliteTm && (investment.costs.eliteFastTm > 0 || investment.costs.eliteChargedTm > 0)) {
                    exclusions += exclusion(owned, TeamCandidateExclusionReason.ELITE_TM_NOT_ALLOWED, "Build futuro requer Elite TM e esta pesquisa não permite esse recurso.")
                    return@mapNotNull null
                }
            }
            val cp = PokemonMath.cp(sp, owned.iv, level)
            val eligibility = RulesetEngine.evaluate(ruleset, sp, level, cp)
            if (!eligibility.eligible) {
                exclusions += exclusion(owned, TeamCandidateExclusionReason.RULESET_INELIGIBLE, eligibility.reasons.joinToString())
                return@mapNotNull null
            }
            Candidate(owned, sp)
        }

        if (constraints.lockedOwnedPokemonIds.size > 3) {
            return refused(meta, ruleset, certification.selectedRoute?.name,
                "A team has three slots; at most three Pokémon can be locked.", exclusions)
        }
        val locked = constraints.lockedOwnedPokemonIds.map { lockId ->
            eligible.firstOrNull { it.owned.id == lockId }
                ?: return refused(meta, ruleset, certification.selectedRoute?.name, "Locked Pokémon $lockId is missing or ineligible.", exclusions)
        }
        if (!constraints.allowSameSpecies && locked.map { it.species.speciesId }.distinct().size != locked.size) {
            return refused(meta, ruleset, certification.selectedRoute?.name, "Locked Pokémon violate same-species constraint.", exclusions)
        }

        if (eligible.size < 3) {
            return TeamAdvisorResult(
                status = TeamAdvisorStatus.INSUFFICIENT_CANDIDATES,
                metaGroupId = meta.id,
                rulesetId = ruleset.id,
                rulesetVersion = ruleset.version,
                certificationRoute = certification.selectedRoute?.name,
                recommendations = emptyList(),
                exclusions = exclusions.sortedBy { it.ownedPokemonId }
            )
        }

        // Cache every candidate x meta evaluation once. This also gives an individual pre-score for pool pruning.
        val matrix = LinkedHashMap<String, Map<String, MatchupEvaluation?>>()
        eligible.forEach { candidate ->
            matrix[candidate.owned.id] = meta.entries.associate { entry ->
                entry.key to evaluator.evaluate(candidate.owned, entry.opponent, scenario)
            }
        }
        val individualScore = eligible.associate { candidate ->
            val evals = matrix.getValue(candidate.owned.id)
            var weight = 0.0
            var sum = 0.0
            meta.entries.forEach { entry ->
                evals[entry.key]?.let { ev ->
                    weight += entry.weight
                    sum += entry.weight * ev.battleRating
                }
            }
            candidate.owned.id to if (weight > 0.0) sum / weight else -1.0
        }

        val lockedIds = locked.map { it.owned.id }.toSet()
        val pool = (locked + eligible.filter { it.owned.id !in lockedIds }
            .sortedWith(compareByDescending<Candidate> { individualScore[it.owned.id] ?: -1.0 }
                .thenBy { it.species.speciesId }
                .thenBy { it.owned.id })
            .take((constraints.maxCandidatePool - locked.size).coerceAtLeast(0)))
            .distinctBy { it.owned.id }

        val teams = combinations(pool, lockedIds, constraints.allowSameSpecies)
            .map { team -> ScoredTeam(team, coverage(team, meta, matrix)) }
            .sortedWith(
                compareByDescending<ScoredTeam> { it.coverage.weightedBestBattleRating }
                    .thenBy { it.coverage.threatMetaWeight }
                    .thenBy { it.coverage.severeThreatMetaWeight }
                    .thenByDescending { it.coverage.evaluatedMetaWeight }
                    .thenBy { it.team.joinToString("|") { c -> c.owned.id } }
            )
            .take(limit)

        val recommendations = teams.mapIndexed { index, scored ->
            TeamLineupRecommendation(
                position = index + 1,
                memberOwnedPokemonIds = scored.team.map { it.owned.id },
                memberSpeciesIds = scored.team.map { it.species.speciesId },
                coverage = scored.coverage,
                rationale = rationale(scored.coverage)
            )
        }

        return TeamAdvisorResult(
            status = if (recommendations.isEmpty()) TeamAdvisorStatus.INSUFFICIENT_CANDIDATES else TeamAdvisorStatus.READY,
            metaGroupId = meta.id,
            rulesetId = ruleset.id,
            rulesetVersion = ruleset.version,
            certificationRoute = certification.selectedRoute?.name,
            recommendations = recommendations,
            exclusions = exclusions.sortedBy { it.ownedPokemonId }
        )
    }

    private data class Candidate(val owned: OwnedPokemon, val species: PokemonSpecies)
    private data class ScoredTeam(val team: List<Candidate>, val coverage: TeamCoverageReport)

    private fun combinations(pool: List<Candidate>, lockedIds: Set<String>, allowSameSpecies: Boolean): List<List<Candidate>> {
        val out = mutableListOf<List<Candidate>>()
        for (i in 0 until pool.size - 2) for (j in i + 1 until pool.size - 1) for (k in j + 1 until pool.size) {
            val team = listOf(pool[i], pool[j], pool[k])
            if (!team.map { it.owned.id }.containsAll(lockedIds)) continue
            if (!allowSameSpecies && team.map { it.species.speciesId }.distinct().size != 3) continue
            out += team.sortedBy { it.owned.id }
        }
        return out
    }

    private fun coverage(
        team: List<Candidate>,
        meta: VersionedMetaGroup,
        matrix: Map<String, Map<String, MatchupEvaluation?>>
    ): TeamCoverageReport {
        var evaluatedWeight = 0.0
        var weightedBest = 0.0
        var threatWeight = 0.0
        var severeThreatWeight = 0.0
        val strengths = mutableListOf<TeamThreat>()
        val threats = mutableListOf<TeamThreat>()
        val unresolved = mutableListOf<String>()

        meta.entries.forEach { entry ->
            val resolved = team.mapNotNull { c -> matrix[c.owned.id]?.get(entry.key)?.let { c to it } }
            if (resolved.isEmpty()) {
                unresolved += entry.key
                return@forEach
            }
            val best = resolved.maxWith(compareBy<Pair<Candidate, MatchupEvaluation>> { it.second.battleRating }
                .thenByDescending { it.first.owned.id })
            val rating = best.second.battleRating
            evaluatedWeight += entry.weight
            weightedBest += entry.weight * rating
            val threat = TeamThreat(entry.key, entry.opponent.speciesId, entry.weight, rating, best.first.owned.id)
            if (rating >= 650) strengths += threat
            if (rating < 500) {
                threatWeight += entry.weight
                threats += threat
                if (rating < 350) severeThreatWeight += entry.weight
            }
        }
        val avg = if (evaluatedWeight > 0.0) weightedBest / evaluatedWeight else 0.0
        return TeamCoverageReport(
            metaGroupId = meta.id,
            memberOwnedPokemonIds = team.map { it.owned.id },
            weightedBestBattleRating = avg.coerceIn(0.0, 1000.0),
            coverageScorePercent = (avg / 10.0).coerceIn(0.0, 100.0),
            evaluatedMetaWeight = evaluatedWeight,
            totalMetaWeight = meta.totalWeight,
            threatMetaWeight = threatWeight,
            severeThreatMetaWeight = severeThreatWeight,
            strengths = strengths.sortedWith(compareByDescending<TeamThreat> { it.weight }.thenByDescending { it.bestBattleRating }).take(5),
            threats = threats.sortedWith(compareByDescending<TeamThreat> { it.weight }.thenBy { it.bestBattleRating }).take(5),
            unresolvedMetaEntryKeys = unresolved.sorted()
        )
    }

    private fun rationale(c: TeamCoverageReport): String = buildString {
        append("Coverage score ").append("%.1f".format(c.coverageScorePercent)).append("/100 baseado no melhor Battle Rating disponível por entrada do meta; não é probabilidade de vitória. ")
        append("Cobertura avaliada ").append("%.1f".format(c.evaluatedCoveragePercent)).append("% do peso do snapshot. ")
        if (c.threatMetaWeight > 0.0) append("Peso de ameaças sem resposta favorável: ").append("%.2f".format(c.threatMetaWeight)).append('.')
        else append("Nenhuma entrada avaliada ficou sem pelo menos uma resposta >=500.")
    }

    private fun exclusion(owned: OwnedPokemon, reason: TeamCandidateExclusionReason, detail: String) =
        TeamCandidateExclusion(owned.id, owned.speciesId, reason, detail)

    private fun refused(
        meta: VersionedMetaGroup,
        ruleset: PvPRuleset,
        route: String?,
        reason: String,
        exclusions: List<TeamCandidateExclusion> = emptyList()
    ) = TeamAdvisorResult(
        status = TeamAdvisorStatus.REFUSED,
        metaGroupId = meta.id,
        rulesetId = ruleset.id,
        rulesetVersion = ruleset.version,
        certificationRoute = route,
        recommendations = emptyList(),
        exclusions = exclusions.sortedBy { it.ownedPokemonId },
        refusalReason = reason
    )
}
