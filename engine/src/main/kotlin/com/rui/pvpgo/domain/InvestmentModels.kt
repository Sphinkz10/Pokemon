package com.rui.pvpgo.domain

import com.rui.pvpgo.engine.League

data class ResourceLedger(
    val stardust: Int = 0,
    val candyByFamily: Map<String, Int> = emptyMap(),
    val xlCandyByFamily: Map<String, Int> = emptyMap(),
    val fastTm: Int = 0,
    val chargedTm: Int = 0,
    val eliteFastTm: Int = 0,
    val eliteChargedTm: Int = 0
) {
    init {
        require(stardust >= 0)
        require(candyByFamily.values.all { it >= 0 })
        require(xlCandyByFamily.values.all { it >= 0 })
        require(fastTm >= 0 && chargedTm >= 0 && eliteFastTm >= 0 && eliteChargedTm >= 0)
    }
}

data class PowerUpStepCost(
    val fromLevel: Double,
    val toLevel: Double,
    val stardust: Int,
    val candy: Int = 0,
    val xlCandy: Int = 0
) {
    init {
        require(toLevel == fromLevel + 0.5)
        require(fromLevel in 1.0..50.5 && toLevel in 1.5..51.0)
        require(stardust >= 0 && candy >= 0 && xlCandy >= 0)
        require(candy == 0 || xlCandy == 0) { "A power-up step cannot consume regular and XL Candy simultaneously" }
    }
}

data class SpeciesInvestmentCost(
    val speciesId: String,
    val familyId: String,
    val secondChargedMoveStardust: Int? = null,
    val secondChargedMoveCandy: Int? = null,
    val purificationStardust: Int? = null,
    val purificationCandy: Int? = null
) {
    init {
        require(speciesId.isNotBlank() && familyId.isNotBlank())
        require(secondChargedMoveStardust == null || secondChargedMoveStardust >= 0)
        require(secondChargedMoveCandy == null || secondChargedMoveCandy >= 0)
        require(purificationStardust == null || purificationStardust >= 0)
        require(purificationCandy == null || purificationCandy >= 0)
    }
}

data class EvolutionInvestmentCost(
    val fromSpeciesId: String,
    val toSpeciesId: String,
    val familyId: String,
    val candy: Int
) {
    init {
        require(fromSpeciesId.isNotBlank() && toSpeciesId.isNotBlank() && fromSpeciesId != toSpeciesId)
        require(familyId.isNotBlank())
        require(candy >= 0)
    }
}

data class InvestmentCostPack(
    val id: String,
    val version: String,
    val sourceName: String,
    val sourceVersion: String,
    val generatedAtEpochMs: Long,
    val powerUpSteps: List<PowerUpStepCost>,
    val speciesCosts: List<SpeciesInvestmentCost>,
    val evolutionCosts: List<EvolutionInvestmentCost>,
    val contentHashSha256: String
) {
    init {
        require(id.isNotBlank() && version.isNotBlank() && sourceName.isNotBlank() && sourceVersion.isNotBlank())
        require(generatedAtEpochMs >= 0)
        require(contentHashSha256.length == 64)
    }
}

data class InvestmentTarget(
    val ownedPokemonId: String,
    val league: League,
    val targetSpeciesId: String,
    val targetLevel: Double,
    val desiredFastMoveId: String? = null,
    val desiredChargedMoveIds: List<String> = emptyList(),
    val bestBuddyAllowed: Boolean = false
) {
    init {
        require(ownedPokemonId.isNotBlank() && targetSpeciesId.isNotBlank())
        require(targetLevel in 1.0..51.0 && targetLevel * 2 == (targetLevel * 2).toInt().toDouble())
        require(desiredChargedMoveIds.size <= 2)
        require(targetLevel <= 50.0 || bestBuddyAllowed) { "Level 50.5/51 target requires Best Buddy allowance" }
    }
}

enum class InvestmentRequirementKind {
    STARDUST,
    CANDY,
    XL_CANDY,
    FAST_TM,
    CHARGED_TM,
    ELITE_FAST_TM,
    ELITE_CHARGED_TM,
    SECOND_CHARGED_MOVE,
    EVOLUTION,
    REMOVE_FRUSTRATION_EVENT,
    UNKNOWN_COST,
    UNKNOWN_POST_EVOLUTION_MOVES
}

data class InvestmentRequirement(
    val kind: InvestmentRequirementKind,
    val amount: Int? = null,
    val familyId: String? = null,
    val detail: String
) {
    init { require(amount == null || amount >= 0) }
}

data class ResourceCostSummary(
    val stardust: Int = 0,
    val candyByFamily: Map<String, Int> = emptyMap(),
    val xlCandyByFamily: Map<String, Int> = emptyMap(),
    val fastTm: Int = 0,
    val chargedTm: Int = 0,
    val eliteFastTm: Int = 0,
    val eliteChargedTm: Int = 0
) {
    val hasAnyCost: Boolean get() = stardust > 0 || candyByFamily.values.any { it > 0 } || xlCandyByFamily.values.any { it > 0 } ||
        fastTm > 0 || chargedTm > 0 || eliteFastTm > 0 || eliteChargedTm > 0
}

enum class InvestmentPlanStatus { READY_NOW, MISSING_RESOURCES, WAIT_EVENT, PARTIAL_UNKNOWN_COST, INVALID }

data class InvestmentPlan(
    val target: InvestmentTarget,
    val status: InvestmentPlanStatus,
    val costs: ResourceCostSummary,
    val requirements: List<InvestmentRequirement>,
    val missingResources: List<InvestmentRequirement>,
    val costPackId: String,
    val costPackVersion: String,
    val costPackHashSha256: String,
    val notes: List<String> = emptyList()
) {
    val exactCostKnown: Boolean get() = requirements.none { it.kind == InvestmentRequirementKind.UNKNOWN_COST || it.kind == InvestmentRequirementKind.UNKNOWN_POST_EVOLUTION_MOVES }
}

data class InvestmentUtilityEvidence(
    val matchupRatingDelta: Double = 0.0,
    val teamCoverageDeltaPercent: Double = 0.0,
    val readinessDelta: Double = 0.0,
    val evidenceLabel: String
) {
    init { require(evidenceLabel.isNotBlank()) }
}

data class InvestmentRoiWeights(
    val matchup: Double = 0.45,
    val teamCoverage: Double = 0.35,
    val readiness: Double = 0.20,
    val resourceBurden: Double = 0.35
) {
    init { require(matchup >= 0 && teamCoverage >= 0 && readiness >= 0 && resourceBurden >= 0) }
}

data class InvestmentRoiReport(
    val ownedPokemonId: String,
    val utilityScore: Double,
    val resourceBurdenScore: Double,
    val priorityIndex: Double,
    val evidenceLabel: String,
    val explanation: String
)

enum class BuildQueueState { DO_NOW, WAIT_EVENT, NEEDS_RESOURCES, NEEDS_XL, NEEDS_ELITE_TM, REVIEW_UNKNOWN_COST, INVALID }

data class BuildQueueItem(
    val planId: String,
    val ownedPokemonId: String,
    val priority: Int,
    val state: BuildQueueState,
    val investmentPlan: InvestmentPlan,
    val roi: InvestmentRoiReport? = null,
    val reason: String
)

enum class EventOpportunityKind { REMOVE_FRUSTRATION, LEGACY_EVOLUTION_MOVE, COMMUNITY_DAY_MOVE, OTHER }

data class EventOpportunity(
    val id: String,
    val title: String,
    val kind: EventOpportunityKind,
    val startsAtEpochMs: Long?,
    val endsAtEpochMs: Long?,
    val speciesIds: Set<String> = emptySet(),
    val moveIds: Set<String> = emptySet(),
    val sourceLabel: String
) {
    init {
        require(id.isNotBlank() && title.isNotBlank() && sourceLabel.isNotBlank())
        require(startsAtEpochMs == null || startsAtEpochMs >= 0)
        require(endsAtEpochMs == null || endsAtEpochMs >= 0)
        require(startsAtEpochMs == null || endsAtEpochMs == null || endsAtEpochMs >= startsAtEpochMs)
    }
}

data class EventOpportunityAdvice(
    val waitRecommended: Boolean,
    val matchedOpportunityIds: List<String>,
    val explanation: String
)

data class ShadowPurifiedScenarioEvidence(
    val label: String,
    val weightedBattleRating: Double?,
    val teamCoveragePercent: Double?,
    val dataVersion: String,
    val engineVersion: String
) {
    init {
        require(label.isNotBlank() && dataVersion.isNotBlank() && engineVersion.isNotBlank())
        require(weightedBattleRating == null || weightedBattleRating in 0.0..1000.0)
        require(teamCoveragePercent == null || teamCoveragePercent in 0.0..100.0)
    }
}

data class ShadowPurifiedComparison(
    val originalIv: String,
    val purifiedIv: String,
    val purificationCost: ResourceCostSummary?,
    val shadowEvidence: ShadowPurifiedScenarioEvidence?,
    val purifiedEvidence: ShadowPurifiedScenarioEvidence?,
    val battleRatingDeltaPurifiedMinusShadow: Double?,
    val coverageDeltaPurifiedMinusShadow: Double?,
    val recommendation: String,
    val caveats: List<String>
)

data class XlBuddyPlan(
    val ownedPokemonId: String,
    val currentLevel: Double,
    val targetLevel: Double,
    val xlCandyRequired: Int?,
    val stardustRequired: Int?,
    val bestBuddyRequired: Boolean,
    val targetCp: Int,
    val targetAttack: Double,
    val targetDefense: Double,
    val targetHp: Int,
    val exactCostKnown: Boolean,
    val notes: List<String>
)
