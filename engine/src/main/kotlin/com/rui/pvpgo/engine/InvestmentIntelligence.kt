package com.rui.pvpgo.engine

import com.rui.pvpgo.domain.*
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.ArrayDeque
import kotlin.math.max
import kotlin.math.min

object InvestmentCostPackValidator {
    fun contentHash(
        id: String,
        version: String,
        sourceName: String,
        sourceVersion: String,
        generatedAtEpochMs: Long,
        powerUpSteps: List<PowerUpStepCost>,
        speciesCosts: List<SpeciesInvestmentCost>,
        evolutionCosts: List<EvolutionInvestmentCost>
    ): String = sha256(canonical(id, version, sourceName, sourceVersion, generatedAtEpochMs, powerUpSteps, speciesCosts, evolutionCosts))

    fun validate(pack: InvestmentCostPack): List<String> {
        val errors = mutableListOf<String>()
        val stepKeys = pack.powerUpSteps.map { it.fromLevel to it.toLevel }
        if (stepKeys.distinct().size != stepKeys.size) errors += "Duplicate power-up step"
        if (pack.speciesCosts.map { it.speciesId }.distinct().size != pack.speciesCosts.size) errors += "Duplicate species investment cost"
        val edgeKeys = pack.evolutionCosts.map { it.fromSpeciesId to it.toSpeciesId }
        if (edgeKeys.distinct().size != edgeKeys.size) errors += "Duplicate evolution cost edge"
        val expected = contentHash(pack.id, pack.version, pack.sourceName, pack.sourceVersion, pack.generatedAtEpochMs, pack.powerUpSteps, pack.speciesCosts, pack.evolutionCosts)
        if (expected != pack.contentHashSha256) errors += "Investment cost pack hash mismatch"
        return errors
    }

    fun build(
        id: String,
        version: String,
        sourceName: String,
        sourceVersion: String,
        generatedAtEpochMs: Long,
        powerUpSteps: List<PowerUpStepCost>,
        speciesCosts: List<SpeciesInvestmentCost>,
        evolutionCosts: List<EvolutionInvestmentCost>
    ): InvestmentCostPack {
        val hash = contentHash(id, version, sourceName, sourceVersion, generatedAtEpochMs, powerUpSteps, speciesCosts, evolutionCosts)
        return InvestmentCostPack(id, version, sourceName, sourceVersion, generatedAtEpochMs, powerUpSteps, speciesCosts, evolutionCosts, hash)
    }

    private fun canonical(
        id: String, version: String, sourceName: String, sourceVersion: String, generatedAtEpochMs: Long,
        steps: List<PowerUpStepCost>, species: List<SpeciesInvestmentCost>, evolutions: List<EvolutionInvestmentCost>
    ) = buildString {
        append("id=").append(id).append('\n')
        append("version=").append(version).append('\n')
        append("source=").append(sourceName).append('|').append(sourceVersion).append('\n')
        append("generated=").append(generatedAtEpochMs).append('\n')
        steps.sortedBy { it.fromLevel }.forEach { append("p=").append(it.fromLevel).append('>').append(it.toLevel).append('|').append(it.stardust).append('|').append(it.candy).append('|').append(it.xlCandy).append('\n') }
        species.sortedBy { it.speciesId }.forEach { append("s=").append(it.speciesId).append('|').append(it.familyId).append('|').append(it.secondChargedMoveStardust).append('|').append(it.secondChargedMoveCandy).append('|').append(it.purificationStardust).append('|').append(it.purificationCandy).append('\n') }
        evolutions.sortedWith(compareBy<EvolutionInvestmentCost> { it.fromSpeciesId }.thenBy { it.toSpeciesId }).forEach { append("e=").append(it.fromSpeciesId).append('>').append(it.toSpeciesId).append('|').append(it.familyId).append('|').append(it.candy).append('\n') }
    }

    private fun sha256(text: String) = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

object InvestmentPlanner {
    fun plan(
        owned: OwnedPokemon,
        target: InvestmentTarget,
        catalog: List<PokemonSpecies>,
        movesById: Map<String, PvpMove>,
        pack: InvestmentCostPack,
        ledger: ResourceLedger
    ): InvestmentPlan {
        if (InvestmentCostPackValidator.validate(pack).isNotEmpty()) {
            return invalid(target, pack, "Investment cost pack failed validation")
        }
        if (owned.id != target.ownedPokemonId) return invalid(target, pack, "Target does not belong to supplied OwnedPokemon")
        val currentSpecies = catalog.firstOrNull { it.speciesId == owned.speciesId } ?: return invalid(target, pack, "Current species not found")
        val targetSpecies = catalog.firstOrNull { it.speciesId == target.targetSpeciesId } ?: return invalid(target, pack, "Target species not found")
        val currentLevel = owned.level ?: return invalid(target, pack, "Current level is unknown")
        if (target.targetLevel < currentLevel && target.targetSpeciesId == owned.speciesId) return invalid(target, pack, "Planner does not support de-leveling")
        if (target.targetLevel > 50.0 && !owned.isBestBuddy && !target.bestBuddyAllowed) return invalid(target, pack, "Level >50 requires Best Buddy")

        val requirements = mutableListOf<InvestmentRequirement>()
        val notes = mutableListOf<String>()
        var stardust = 0
        val candy = linkedMapOf<String, Int>()
        val xl = linkedMapOf<String, Int>()
        var fastTm = 0
        var chargedTm = 0
        var eliteFast = 0
        var eliteCharged = 0

        val familyId = targetSpecies.familyId ?: currentSpecies.familyId ?: targetSpecies.speciesId
        val stepMap = pack.powerUpSteps.associateBy { it.fromLevel }
        var level = currentLevel
        while (level + 1e-9 < target.targetLevel) {
            val step = stepMap[level]
            if (step == null || step.toLevel != level + 0.5) {
                requirements += InvestmentRequirement(InvestmentRequirementKind.UNKNOWN_COST, null, familyId, "Missing power-up cost for level $level → ${level + 0.5}")
                level += 0.5
                continue
            }
            stardust += step.stardust
            if (step.candy > 0) candy[familyId] = (candy[familyId] ?: 0) + step.candy
            if (step.xlCandy > 0) xl[familyId] = (xl[familyId] ?: 0) + step.xlCandy
            level = step.toLevel
        }

        val evolving = owned.speciesId != target.targetSpeciesId
        if (evolving) {
            val path = evolutionPath(owned.speciesId, target.targetSpeciesId, pack.evolutionCosts)
            if (path == null) {
                requirements += InvestmentRequirement(InvestmentRequirementKind.UNKNOWN_COST, null, familyId, "Evolution cost/path is not available in the cost pack")
            } else {
                path.forEach { edge ->
                    candy[edge.familyId] = (candy[edge.familyId] ?: 0) + edge.candy
                    requirements += InvestmentRequirement(InvestmentRequirementKind.EVOLUTION, edge.candy, edge.familyId, "Evolve ${edge.fromSpeciesId} → ${edge.toSpeciesId}")
                }
            }
            requirements += InvestmentRequirement(InvestmentRequirementKind.UNKNOWN_POST_EVOLUTION_MOVES, null, familyId, "Evolution rerolls moves; future TM requirements cannot be inferred from the pre-evolution moves")
        } else if (target.desiredFastMoveId != null || target.desiredChargedMoveIds.isNotEmpty()) {
            val audit = MovesetAnalyzer.analyze(targetSpecies, owned, target.desiredFastMoveId, target.desiredChargedMoveIds, movesById)
            audit.changes.forEach { change ->
                when (change.requirement) {
                    MoveRequirement.ALREADY_OWNED -> Unit
                    MoveRequirement.FAST_TM -> { fastTm++; requirements += InvestmentRequirement(InvestmentRequirementKind.FAST_TM, 1, detail = change.explanation) }
                    MoveRequirement.CHARGED_TM -> { chargedTm++; requirements += InvestmentRequirement(InvestmentRequirementKind.CHARGED_TM, 1, detail = change.explanation) }
                    MoveRequirement.ELITE_FAST_TM -> { eliteFast++; requirements += InvestmentRequirement(InvestmentRequirementKind.ELITE_FAST_TM, 1, detail = change.explanation) }
                    MoveRequirement.ELITE_CHARGED_TM -> { eliteCharged++; requirements += InvestmentRequirement(InvestmentRequirementKind.ELITE_CHARGED_TM, 1, detail = change.explanation) }
                    MoveRequirement.SECOND_CHARGED_MOVE -> Unit // accounted exactly below once
                    MoveRequirement.REMOVE_FRUSTRATION_WHEN_ELIGIBLE -> requirements += InvestmentRequirement(InvestmentRequirementKind.REMOVE_FRUSTRATION_EVENT, null, detail = change.explanation)
                    MoveRequirement.UNAVAILABLE, MoveRequirement.UNKNOWN, MoveRequirement.SPECIAL_PURIFIED_MOVE, MoveRequirement.SPECIAL_SHADOW_MOVE -> requirements += InvestmentRequirement(InvestmentRequirementKind.UNKNOWN_COST, null, familyId, change.explanation)
                }
            }
        }

        val needsSecond = target.desiredChargedMoveIds.distinct().size >= 2 && owned.secondChargedMoveUnlocked != true
        if (needsSecond) {
            val sc = pack.speciesCosts.firstOrNull { it.speciesId == targetSpecies.speciesId }
            if (sc?.secondChargedMoveStardust == null || sc.secondChargedMoveCandy == null) {
                requirements += InvestmentRequirement(InvestmentRequirementKind.UNKNOWN_COST, null, familyId, "Second Charged Move cost is unknown for ${targetSpecies.speciesId}")
            } else {
                stardust += sc.secondChargedMoveStardust
                candy[sc.familyId] = (candy[sc.familyId] ?: 0) + sc.secondChargedMoveCandy
                requirements += InvestmentRequirement(InvestmentRequirementKind.SECOND_CHARGED_MOVE, sc.secondChargedMoveCandy, sc.familyId, "Unlock second Charged Move (${sc.secondChargedMoveStardust} Stardust + ${sc.secondChargedMoveCandy} Candy)")
            }
        }

        if (stardust > 0) requirements += InvestmentRequirement(InvestmentRequirementKind.STARDUST, stardust, detail = "Total Stardust")
        candy.forEach { (fam, amount) -> if (amount > 0) requirements += InvestmentRequirement(InvestmentRequirementKind.CANDY, amount, fam, "Total regular Candy for $fam") }
        xl.forEach { (fam, amount) -> if (amount > 0) requirements += InvestmentRequirement(InvestmentRequirementKind.XL_CANDY, amount, fam, "Total XL Candy for $fam") }

        val costs = ResourceCostSummary(stardust, candy.toMap(), xl.toMap(), fastTm, chargedTm, eliteFast, eliteCharged)
        val missing = missing(costs, ledger)
        val hasUnknown = requirements.any { it.kind == InvestmentRequirementKind.UNKNOWN_COST || it.kind == InvestmentRequirementKind.UNKNOWN_POST_EVOLUTION_MOVES }
        val waitEvent = requirements.any { it.kind == InvestmentRequirementKind.REMOVE_FRUSTRATION_EVENT }
        val status = when {
            hasUnknown -> InvestmentPlanStatus.PARTIAL_UNKNOWN_COST
            waitEvent -> InvestmentPlanStatus.WAIT_EVENT
            missing.isNotEmpty() -> InvestmentPlanStatus.MISSING_RESOURCES
            else -> InvestmentPlanStatus.READY_NOW
        }
        if (evolving) notes += "Move costs after evolution are deliberately not guessed."
        if (target.targetLevel > 40.0) notes += "Target uses XL levels."
        if (target.targetLevel > 50.0) notes += "Target depends on Best Buddy level boost."

        return InvestmentPlan(target, status, costs, requirements.distinct(), missing, pack.id, pack.version, pack.contentHashSha256, notes)
    }

    private fun evolutionPath(from: String, to: String, edges: List<EvolutionInvestmentCost>): List<EvolutionInvestmentCost>? {
        val q = ArrayDeque<String>()
        val previous = mutableMapOf<String, EvolutionInvestmentCost>()
        q += from
        val visited = mutableSetOf(from)
        while (q.isNotEmpty()) {
            val cur = q.removeFirst()
            if (cur == to) break
            edges.filter { it.fromSpeciesId == cur }.forEach { edge ->
                if (visited.add(edge.toSpeciesId)) { previous[edge.toSpeciesId] = edge; q += edge.toSpeciesId }
            }
        }
        if (to !in visited) return null
        val out = mutableListOf<EvolutionInvestmentCost>()
        var cur = to
        while (cur != from) {
            val edge = previous[cur] ?: return null
            out += edge
            cur = edge.fromSpeciesId
        }
        return out.asReversed()
    }

    private fun missing(cost: ResourceCostSummary, ledger: ResourceLedger): List<InvestmentRequirement> = buildList {
        if (ledger.stardust < cost.stardust) add(InvestmentRequirement(InvestmentRequirementKind.STARDUST, cost.stardust - ledger.stardust, detail = "Missing Stardust"))
        cost.candyByFamily.forEach { (fam, required) -> val have = ledger.candyByFamily[fam] ?: 0; if (have < required) add(InvestmentRequirement(InvestmentRequirementKind.CANDY, required - have, fam, "Missing Candy")) }
        cost.xlCandyByFamily.forEach { (fam, required) -> val have = ledger.xlCandyByFamily[fam] ?: 0; if (have < required) add(InvestmentRequirement(InvestmentRequirementKind.XL_CANDY, required - have, fam, "Missing XL Candy")) }
        if (ledger.fastTm < cost.fastTm) add(InvestmentRequirement(InvestmentRequirementKind.FAST_TM, cost.fastTm - ledger.fastTm, detail = "Missing Fast TM"))
        if (ledger.chargedTm < cost.chargedTm) add(InvestmentRequirement(InvestmentRequirementKind.CHARGED_TM, cost.chargedTm - ledger.chargedTm, detail = "Missing Charged TM"))
        if (ledger.eliteFastTm < cost.eliteFastTm) add(InvestmentRequirement(InvestmentRequirementKind.ELITE_FAST_TM, cost.eliteFastTm - ledger.eliteFastTm, detail = "Missing Elite Fast TM"))
        if (ledger.eliteChargedTm < cost.eliteChargedTm) add(InvestmentRequirement(InvestmentRequirementKind.ELITE_CHARGED_TM, cost.eliteChargedTm - ledger.eliteChargedTm, detail = "Missing Elite Charged TM"))
    }

    private fun invalid(target: InvestmentTarget, pack: InvestmentCostPack, note: String) = InvestmentPlan(
        target, InvestmentPlanStatus.INVALID, ResourceCostSummary(), emptyList(), emptyList(), pack.id, pack.version, pack.contentHashSha256, listOf(note)
    )
}

object InvestmentRoiAnalyzer {
    fun analyze(
        plan: InvestmentPlan,
        evidence: InvestmentUtilityEvidence,
        ledger: ResourceLedger,
        weights: InvestmentRoiWeights = InvestmentRoiWeights()
    ): InvestmentRoiReport {
        val matchup = (max(-300.0, min(300.0, evidence.matchupRatingDelta)) + 300.0) / 6.0
        val coverage = (max(-20.0, min(20.0, evidence.teamCoverageDeltaPercent)) + 20.0) * 2.5
        val readiness = max(0.0, min(1.0, evidence.readinessDelta)) * 100.0
        val utilityWeight = weights.matchup + weights.teamCoverage + weights.readiness
        val utility = if (utilityWeight == 0.0) 0.0 else (
            matchup * weights.matchup + coverage * weights.teamCoverage + readiness * weights.readiness
        ) / utilityWeight

        fun fraction(required: Int, have: Int): Double = if (required <= 0) 0.0 else if (have <= 0) 1.0 else min(1.0, required.toDouble() / have)
        val burdenParts = mutableListOf<Double>()
        if (plan.costs.stardust > 0) burdenParts += fraction(plan.costs.stardust, ledger.stardust)
        plan.costs.candyByFamily.forEach { (fam, amount) -> burdenParts += fraction(amount, ledger.candyByFamily[fam] ?: 0) }
        plan.costs.xlCandyByFamily.forEach { (fam, amount) -> burdenParts += fraction(amount, ledger.xlCandyByFamily[fam] ?: 0) }
        if (plan.costs.eliteFastTm > 0) burdenParts += fraction(plan.costs.eliteFastTm, ledger.eliteFastTm)
        if (plan.costs.eliteChargedTm > 0) burdenParts += fraction(plan.costs.eliteChargedTm, ledger.eliteChargedTm)
        val burden = (if (burdenParts.isEmpty()) 0.0 else burdenParts.average() * 100.0).coerceIn(0.0, 100.0)
        val index = (utility - burden * weights.resourceBurden).coerceIn(0.0, 100.0)
        return InvestmentRoiReport(
            plan.target.ownedPokemonId, utility, burden, index, evidence.evidenceLabel,
            "Priority index = transparent utility signal minus resource burden; it is not a win probability or financial return."
        )
    }
}

object BuildQueuePlanner {
    fun build(
        plans: List<Pair<String, InvestmentPlan>>,
        priorities: Map<String, Int>,
        roiByPlanId: Map<String, InvestmentRoiReport> = emptyMap()
    ): List<BuildQueueItem> = plans.map { (planId, plan) ->
        val reqKinds = plan.requirements.map { it.kind }.toSet()
        val state = when {
            plan.status == InvestmentPlanStatus.INVALID -> BuildQueueState.INVALID
            plan.status == InvestmentPlanStatus.PARTIAL_UNKNOWN_COST -> BuildQueueState.REVIEW_UNKNOWN_COST
            plan.status == InvestmentPlanStatus.WAIT_EVENT -> BuildQueueState.WAIT_EVENT
            InvestmentRequirementKind.ELITE_FAST_TM in reqKinds || InvestmentRequirementKind.ELITE_CHARGED_TM in reqKinds -> if (plan.missingResources.any { it.kind == InvestmentRequirementKind.ELITE_FAST_TM || it.kind == InvestmentRequirementKind.ELITE_CHARGED_TM }) BuildQueueState.NEEDS_ELITE_TM else BuildQueueState.DO_NOW
            InvestmentRequirementKind.XL_CANDY in reqKinds && plan.missingResources.any { it.kind == InvestmentRequirementKind.XL_CANDY } -> BuildQueueState.NEEDS_XL
            plan.status == InvestmentPlanStatus.MISSING_RESOURCES -> BuildQueueState.NEEDS_RESOURCES
            else -> BuildQueueState.DO_NOW
        }
        BuildQueueItem(
            planId, plan.target.ownedPokemonId, priorities[planId]?.coerceIn(0,100) ?: 0, state, plan, roiByPlanId[planId],
            when(state) {
                BuildQueueState.DO_NOW -> "Build is currently affordable under the supplied ledger."
                BuildQueueState.WAIT_EVENT -> "A move/event gate prevents completing the build now."
                BuildQueueState.NEEDS_RESOURCES -> "Regular resources are missing."
                BuildQueueState.NEEDS_XL -> "XL Candy is the active blocker."
                BuildQueueState.NEEDS_ELITE_TM -> "An Elite TM is the active blocker."
                BuildQueueState.REVIEW_UNKNOWN_COST -> "At least one factual cost is unknown; manual review required."
                BuildQueueState.INVALID -> "Build target is invalid for this planner."
            }
        )
    }.sortedWith(
        compareBy<BuildQueueItem> { stateOrder(it.state) }
            .thenByDescending { it.priority }
            .thenByDescending { it.roi?.priorityIndex ?: -1.0 }
            .thenBy { it.planId }
    )

    private fun stateOrder(s: BuildQueueState) = when(s) {
        BuildQueueState.DO_NOW -> 0
        BuildQueueState.WAIT_EVENT -> 1
        BuildQueueState.NEEDS_ELITE_TM -> 2
        BuildQueueState.NEEDS_XL -> 3
        BuildQueueState.NEEDS_RESOURCES -> 4
        BuildQueueState.REVIEW_UNKNOWN_COST -> 5
        BuildQueueState.INVALID -> 6
    }
}

object EventOpportunityAdvisor {
    fun assess(owned: OwnedPokemon, plan: InvestmentPlan, opportunities: List<EventOpportunity>): EventOpportunityAdvice {
        val needsFrustration = plan.requirements.any { it.kind == InvestmentRequirementKind.REMOVE_FRUSTRATION_EVENT }
        val eliteMoves = buildSet {
            if (plan.requirements.any { it.kind == InvestmentRequirementKind.ELITE_FAST_TM }) plan.target.desiredFastMoveId?.let(::add)
            if (plan.requirements.any { it.kind == InvestmentRequirementKind.ELITE_CHARGED_TM }) addAll(plan.target.desiredChargedMoveIds)
        }
        val matched = opportunities.filter { op ->
            val speciesOk = op.speciesIds.isEmpty() || owned.speciesId in op.speciesIds || plan.target.targetSpeciesId in op.speciesIds
            speciesOk && when(op.kind) {
                EventOpportunityKind.REMOVE_FRUSTRATION -> needsFrustration
                EventOpportunityKind.LEGACY_EVOLUTION_MOVE, EventOpportunityKind.COMMUNITY_DAY_MOVE -> eliteMoves.isNotEmpty() && (op.moveIds.isEmpty() || eliteMoves.any { it in op.moveIds })
                EventOpportunityKind.OTHER -> false
            }
        }
        return EventOpportunityAdvice(
            waitRecommended = matched.isNotEmpty(),
            matchedOpportunityIds = matched.map { it.id }.sorted(),
            explanation = if (matched.isEmpty()) "No supplied event opportunity removes a known blocker." else "A supplied event window may avoid or unlock a currently gated move requirement; verify event terms before acting."
        )
    }
}

object ShadowPurifiedAdvisor {
    fun compare(
        owned: OwnedPokemon,
        species: PokemonSpecies,
        pack: InvestmentCostPack,
        shadowEvidence: ShadowPurifiedScenarioEvidence?,
        purifiedEvidence: ShadowPurifiedScenarioEvidence?
    ): ShadowPurifiedComparison {
        require(owned.isShadow) { "Shadow/Purified comparison requires a Shadow Pokémon" }
        val purifiedIv = IvSpread(min(15, owned.iv.attack + 2), min(15, owned.iv.defense + 2), min(15, owned.iv.stamina + 2))
        val sc = pack.speciesCosts.firstOrNull { it.speciesId == species.speciesId }
        val cost = if (sc?.purificationStardust != null && sc.purificationCandy != null) ResourceCostSummary(
            stardust = sc.purificationStardust,
            candyByFamily = mapOf(sc.familyId to sc.purificationCandy)
        ) else null
        val ratingDelta = if (shadowEvidence?.weightedBattleRating != null && purifiedEvidence?.weightedBattleRating != null) purifiedEvidence.weightedBattleRating - shadowEvidence.weightedBattleRating else null
        val coverageDelta = if (shadowEvidence?.teamCoveragePercent != null && purifiedEvidence?.teamCoveragePercent != null) purifiedEvidence.teamCoveragePercent - shadowEvidence.teamCoveragePercent else null
        return ShadowPurifiedComparison(
            originalIv = owned.iv.toString(), purifiedIv = purifiedIv.toString(), purificationCost = cost,
            shadowEvidence = shadowEvidence, purifiedEvidence = purifiedEvidence,
            battleRatingDeltaPurifiedMinusShadow = ratingDelta, coverageDeltaPurifiedMinusShadow = coverageDelta,
            recommendation = "REVIEW_BOTH",
            caveats = listOf(
                "Purification is irreversible in Pokémon GO.",
                "This foundation reports deltas and cost but does not issue an automatic purify/keep command.",
                "Compare league-specific CP cap, moves and matchups before deciding."
            )
        )
    }
}

object XlBestBuddyPlanner {
    fun plan(
        owned: OwnedPokemon,
        species: PokemonSpecies,
        targetLevel: Double,
        pack: InvestmentCostPack
    ): XlBuddyPlan {
        require(targetLevel in 1.0..51.0 && targetLevel * 2 == (targetLevel * 2).toInt().toDouble())
        val currentLevel = owned.level ?: error("Current level unknown")
        require(targetLevel >= currentLevel)
        val stepMap = pack.powerUpSteps.associateBy { it.fromLevel }
        var l = currentLevel
        var dust = 0
        var xl = 0
        var exact = true
        while (l + 1e-9 < targetLevel) {
            val step = stepMap[l]
            if (step == null || step.toLevel != l + 0.5) { exact = false; l += 0.5; continue }
            dust += step.stardust
            xl += step.xlCandy
            l = step.toLevel
        }
        val stats = PokemonMath.battleStats(species, owned.iv, targetLevel)
        return XlBuddyPlan(
            owned.id, currentLevel, targetLevel,
            xlCandyRequired = if (exact) xl else null,
            stardustRequired = if (exact) dust else null,
            bestBuddyRequired = targetLevel > 50.0,
            targetCp = PokemonMath.cp(species, owned.iv, targetLevel),
            targetAttack = stats.attack, targetDefense = stats.defense, targetHp = stats.hp,
            exactCostKnown = exact,
            notes = buildList {
                if (targetLevel > 40.0) add("Target includes XL power-ups.")
                if (targetLevel > 50.0) add("Level 50.5/51 requires Best Buddy boost and only one active buddy can receive that boost at a time.")
                if (!exact) add("One or more power-up cost steps are missing from the versioned cost pack.")
            }
        )
    }
}
