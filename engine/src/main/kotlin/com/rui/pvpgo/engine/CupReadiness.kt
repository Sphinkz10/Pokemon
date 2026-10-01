package com.rui.pvpgo.engine

import com.rui.pvpgo.domain.*

enum class CupReadinessBucket { READY, CANDIDATE, BLOCKED, INELIGIBLE }

enum class CupReadinessStatus {
    READY_NOW,
    AFFORDABLE_UPGRADE,
    MISSING_RESOURCES,
    WAIT_EVENT,
    REVIEW_UNKNOWN_COST,
    NEEDS_BUILD_TARGET,
    INCOMPLETE_OWNED_BUILD,
    RULESET_INELIGIBLE
}

enum class CupTargetSource { BUILD_PLAN, META_BUILD, NONE }

data class CupReadinessItem(
    val ownedPokemonId: String,
    val speciesId: String,
    val bucket: CupReadinessBucket,
    val status: CupReadinessStatus,
    val targetSource: CupTargetSource,
    val targetLevel: Double?,
    val targetFastMoveId: String?,
    val targetChargedMoveIds: List<String>,
    val investmentPlan: InvestmentPlan? = null,
    val rulesetEligibility: RulesetEligibility,
    val reasons: List<String>
)

enum class CupGapReason { MISSING_FROM_COLLECTION, ONLY_BLOCKED_OR_INELIGIBLE }

data class CupReadinessGap(
    val speciesId: String,
    val metaWeight: Double,
    val reason: CupGapReason,
    val detail: String
)

data class CupReadinessReport(
    val rulesetId: String,
    val rulesetVersion: String,
    val ready: List<CupReadinessItem>,
    val candidates: List<CupReadinessItem>,
    val blocked: List<CupReadinessItem>,
    val ineligible: List<CupReadinessItem>,
    val gaps: List<CupReadinessGap>,
    val metaGroupId: String?,
    val notes: List<String>
) {
    val totalCollectionItems: Int get() = ready.size + candidates.size + blocked.size + ineligible.size
}

/**
 * F70: collection readiness for one ruleset/cup. The analyzer never invents a target moveset:
 * BUILD_PLAN wins, then an explicit versioned META_BUILD, otherwise the item is marked NEEDS_BUILD_TARGET.
 */
object CupReadinessAnalyzer {
    fun analyze(
        ruleset: PvPRuleset,
        collection: List<OwnedPokemon>,
        catalog: List<PokemonSpecies>,
        movesById: Map<String, PvpMove>,
        buildPlans: List<PvpBuildPlan> = emptyList(),
        metaGroup: VersionedMetaGroup? = null,
        costPack: InvestmentCostPack? = null,
        ledger: ResourceLedger? = null
    ): CupReadinessReport {
        require((costPack == null) == (ledger == null)) { "costPack and ledger must be supplied together" }
        if (metaGroup != null) {
            require(metaGroup.league == ruleset.baseLeague) { "Meta group league must match ruleset base league" }
            if (metaGroup.rulesetId != null) {
                require(metaGroup.rulesetId == ruleset.id && metaGroup.rulesetVersion == ruleset.version) {
                    "Meta group ruleset provenance does not match selected ruleset"
                }
            }
        }

        val bySpecies = catalog.associateBy { it.speciesId }
        val planByOwned = buildPlans
            .filter { it.league == ruleset.baseLeague }
            .groupBy { it.ownedPokemonId }
            .mapValues { (_, plans) -> plans.maxWithOrNull(compareBy<PvpBuildPlan> { it.priority }.thenBy { it.updatedAtEpochMs })!! }
        val metaBySpecies = metaGroup?.entries?.groupBy { it.opponent.speciesId }.orEmpty()

        val items = collection.map { owned ->
            val species = bySpecies[owned.speciesId]
            if (species == null) {
                return@map CupReadinessItem(
                    owned.id, owned.speciesId, CupReadinessBucket.INELIGIBLE, CupReadinessStatus.RULESET_INELIGIBLE,
                    CupTargetSource.NONE, null, null, emptyList(), null,
                    RulesetEligibility(false, listOf(RulesetIneligibilityReason.UNKNOWN_OR_UNRELEASED)),
                    listOf("Species/form is not present in the supplied factual catalog.")
                )
            }

            val cp = owned.cp ?: owned.level?.let { PokemonMath.cp(species, owned.iv, it) }
            val currentEligibility = RulesetEngine.evaluate(ruleset, species, owned.level, cp)
            if (!currentEligibility.eligible) {
                return@map CupReadinessItem(
                    owned.id, owned.speciesId, CupReadinessBucket.INELIGIBLE, CupReadinessStatus.RULESET_INELIGIBLE,
                    CupTargetSource.NONE, null, null, emptyList(), null, currentEligibility,
                    currentEligibility.reasons.map { "Ruleset: $it" }
                )
            }

            val currentLevel = owned.level
            if (currentLevel == null) {
                return@map CupReadinessItem(
                    owned.id, owned.speciesId, CupReadinessBucket.BLOCKED, CupReadinessStatus.INCOMPLETE_OWNED_BUILD,
                    CupTargetSource.NONE, null, null, emptyList(), null, currentEligibility,
                    listOf("Current level is unknown; target level/cost cannot be calculated safely.")
                )
            }

            val plan = planByOwned[owned.id]
            val metaEntry = metaBySpecies[owned.speciesId]?.maxByOrNull { it.weight }
            val targetSource: CupTargetSource
            val targetFast: String?
            val targetCharged: List<String>
            val bestBuddyAllowed: Boolean
            when {
                plan != null && plan.desiredFastMoveId != null && plan.desiredChargedMoveIds.isNotEmpty() -> {
                    targetSource = CupTargetSource.BUILD_PLAN
                    targetFast = plan.desiredFastMoveId
                    targetCharged = plan.desiredChargedMoveIds
                    bestBuddyAllowed = plan.bestBuddyAllowed
                }
                metaEntry != null -> {
                    targetSource = CupTargetSource.META_BUILD
                    targetFast = metaEntry.opponent.fastMoveId
                    targetCharged = metaEntry.opponent.chargedMoveIds
                    bestBuddyAllowed = (metaEntry.opponent.level ?: 50.0) > 50.0
                }
                else -> {
                    return@map CupReadinessItem(
                        owned.id, owned.speciesId, CupReadinessBucket.BLOCKED, CupReadinessStatus.NEEDS_BUILD_TARGET,
                        CupTargetSource.NONE, null, null, emptyList(), null, currentEligibility,
                        listOf("No explicit build plan or versioned meta build exists for this owned Pokémon.")
                    )
                }
            }

            val maxLevel = if (bestBuddyAllowed || owned.isBestBuddy) ruleset.maxLevel else minOf(ruleset.maxLevel, 50.0)
            val targetLevel = if (ruleset.cpCap != null) {
                PokemonMath.highestLevelAtOrBelowCap(species, owned.iv, ruleset.cpCap, ruleset.minLevel, maxLevel)
            } else maxLevel

            if (targetLevel == null || targetLevel + 1e-9 < currentLevel) {
                val eligibility = RulesetEligibility(false, listOf(RulesetIneligibilityReason.CP_CAP_EXCEEDED))
                return@map CupReadinessItem(
                    owned.id, owned.speciesId, CupReadinessBucket.INELIGIBLE, CupReadinessStatus.RULESET_INELIGIBLE,
                    targetSource, targetLevel, targetFast, targetCharged, null, eligibility,
                    listOf("Current build cannot fit inside the selected CP/level ruleset without de-leveling.")
                )
            }

            if (costPack == null || ledger == null) {
                val alreadyReady = currentLevel == targetLevel && owned.fastMoveId == targetFast && targetCharged.all { it in owned.chargedMoveIds }
                return@map CupReadinessItem(
                    owned.id, owned.speciesId,
                    if (alreadyReady) CupReadinessBucket.READY else CupReadinessBucket.CANDIDATE,
                    if (alreadyReady) CupReadinessStatus.READY_NOW else CupReadinessStatus.REVIEW_UNKNOWN_COST,
                    targetSource, targetLevel, targetFast, targetCharged, null, currentEligibility,
                    listOf(if (alreadyReady) "Build already matches the explicit target." else "Target is known but no versioned cost pack/ledger was supplied.")
                )
            }

            val investment = InvestmentPlanner.plan(
                owned = owned,
                target = InvestmentTarget(
                    ownedPokemonId = owned.id,
                    league = ruleset.baseLeague,
                    targetSpeciesId = owned.speciesId,
                    targetLevel = targetLevel,
                    desiredFastMoveId = targetFast,
                    desiredChargedMoveIds = targetCharged,
                    bestBuddyAllowed = bestBuddyAllowed || targetLevel > 50.0
                ),
                catalog = catalog,
                movesById = movesById,
                pack = costPack,
                ledger = ledger
            )
            val noFutureCost = !investment.costs.hasAnyCost && investment.requirements.none {
                it.kind in setOf(InvestmentRequirementKind.REMOVE_FRUSTRATION_EVENT, InvestmentRequirementKind.UNKNOWN_COST, InvestmentRequirementKind.UNKNOWN_POST_EVOLUTION_MOVES)
            }
            val bucket: CupReadinessBucket
            val status: CupReadinessStatus
            when {
                investment.status == InvestmentPlanStatus.READY_NOW && noFutureCost -> {
                    bucket = CupReadinessBucket.READY; status = CupReadinessStatus.READY_NOW
                }
                investment.status == InvestmentPlanStatus.READY_NOW -> {
                    bucket = CupReadinessBucket.CANDIDATE; status = CupReadinessStatus.AFFORDABLE_UPGRADE
                }
                investment.status == InvestmentPlanStatus.MISSING_RESOURCES -> {
                    bucket = CupReadinessBucket.BLOCKED; status = CupReadinessStatus.MISSING_RESOURCES
                }
                investment.status == InvestmentPlanStatus.WAIT_EVENT -> {
                    bucket = CupReadinessBucket.BLOCKED; status = CupReadinessStatus.WAIT_EVENT
                }
                investment.status == InvestmentPlanStatus.PARTIAL_UNKNOWN_COST -> {
                    bucket = CupReadinessBucket.BLOCKED; status = CupReadinessStatus.REVIEW_UNKNOWN_COST
                }
                else -> {
                    bucket = CupReadinessBucket.BLOCKED; status = CupReadinessStatus.REVIEW_UNKNOWN_COST
                }
            }
            CupReadinessItem(
                owned.id, owned.speciesId, bucket, status, targetSource, targetLevel, targetFast, targetCharged,
                investment, currentEligibility,
                buildList {
                    add("Target source: $targetSource")
                    add("Investment status: ${investment.status}")
                    if (investment.missingResources.isNotEmpty()) add("Missing resources: ${investment.missingResources.joinToString { it.kind.name }}")
                }
            )
        }

        val actionableSpecies = items.filter { it.bucket == CupReadinessBucket.READY || it.bucket == CupReadinessBucket.CANDIDATE }
            .map { it.speciesId }.toSet()
        val collectionSpecies = collection.map { it.speciesId }.toSet()
        val gaps = metaGroup?.entries
            ?.groupBy { it.opponent.speciesId }
            ?.map { (speciesId, entries) ->
                val weight = entries.sumOf { it.weight }
                when {
                    speciesId in actionableSpecies -> null
                    speciesId !in collectionSpecies -> CupReadinessGap(
                        speciesId, weight, CupGapReason.MISSING_FROM_COLLECTION,
                        "Versioned meta contains this species but the Collection does not."
                    )
                    else -> CupReadinessGap(
                        speciesId, weight, CupGapReason.ONLY_BLOCKED_OR_INELIGIBLE,
                        "Species exists in Collection but no READY/CANDIDATE build is currently available for this ruleset."
                    )
                }
            }
            ?.filterNotNull()
            ?.sortedWith(compareByDescending<CupReadinessGap> { it.metaWeight }.thenBy { it.speciesId })
            .orEmpty()

        fun bucket(b: CupReadinessBucket) = items.filter { it.bucket == b }.sortedWith(compareBy<CupReadinessItem> { it.speciesId }.thenBy { it.ownedPokemonId })
        return CupReadinessReport(
            rulesetId = ruleset.id,
            rulesetVersion = ruleset.version,
            ready = bucket(CupReadinessBucket.READY),
            candidates = bucket(CupReadinessBucket.CANDIDATE),
            blocked = bucket(CupReadinessBucket.BLOCKED),
            ineligible = bucket(CupReadinessBucket.INELIGIBLE),
            gaps = gaps,
            metaGroupId = metaGroup?.id,
            notes = listOf(
                "F70 uses explicit build plans first, then versioned meta builds; it never invents a target moveset.",
                "READY means no future investment is required under the supplied target. CANDIDATE may still need an affordable investment.",
                "Meta gaps are collection/readiness gaps, not proof that a team cannot beat that opponent."
            )
        )
    }
}
