package com.rui.pvpgo.domain

enum class TeamAdvisorStatus { READY, REFUSED, INSUFFICIENT_CANDIDATES }

enum class TeamCandidateExclusionReason {
    UNKNOWN_SPECIES,
    INCOMPLETE_BUILD,
    RULESET_INELIGIBLE,
    SPECIAL_MECHANIC_GUARD,
    NOT_BATTLE_READY,
    XL_NOT_ALLOWED,
    SPECIES_CONSTRAINT,
    TAG_CONSTRAINT,
    SHADOW_POLICY,
    INVESTMENT_COST_UNKNOWN,
    MAX_COST_EXCEEDED,
    ELITE_TM_NOT_ALLOWED
}

data class TeamAdvisorConstraints(
    val onlyReady: Boolean = false,
    val allowXl: Boolean = true,
    val allowSameSpecies: Boolean = false,
    val lockedOwnedPokemonIds: Set<String> = emptySet(),
    val excludedOwnedPokemonIds: Set<String> = emptySet(),
    /** Empty means any species. Non-empty is an allow-list, not a lock. */
    val allowedSpeciesIds: Set<String> = emptySet(),
    val excludedSpeciesIds: Set<String> = emptySet(),
    /** Candidate must contain at least one of these tags when non-empty. */
    val requiredAnyTags: Set<String> = emptySet(),
    val excludedTags: Set<String> = emptySet(),
    val shadowPolicy: ShadowPolicy = ShadowPolicy.ANY,
    val maxStardustInvestment: Int? = null,
    val allowFutureEliteTm: Boolean = true,
    val maxCandidatePool: Int = 18
) {
    init {
        require(maxCandidatePool in 3..50)
        require(maxStardustInvestment == null || maxStardustInvestment >= 0)
        require(lockedOwnedPokemonIds.size <= 2) { "At most two Pokémon can be locked before optimizing the remaining slot(s)" }
        require(lockedOwnedPokemonIds.intersect(excludedOwnedPokemonIds).isEmpty())
        require(allowedSpeciesIds.intersect(excludedSpeciesIds).isEmpty())
        require(requiredAnyTags.intersect(excludedTags).isEmpty())
    }
}

data class TeamCandidateExclusion(
    val ownedPokemonId: String,
    val speciesId: String,
    val reason: TeamCandidateExclusionReason,
    val detail: String
)

data class TeamThreat(
    val metaEntryKey: String,
    val opponentSpeciesId: String,
    val weight: Double,
    val bestBattleRating: Int,
    val bestOwnedPokemonId: String?
)

data class TeamCoverageReport(
    val metaGroupId: String,
    val memberOwnedPokemonIds: List<String>,
    /** Weighted best available Battle Rating per meta entry; this is not win probability. */
    val weightedBestBattleRating: Double,
    /** Convenience 0..100 rendering of weighted Battle Rating, explicitly a coverage score. */
    val coverageScorePercent: Double,
    val evaluatedMetaWeight: Double,
    val totalMetaWeight: Double,
    val threatMetaWeight: Double,
    val severeThreatMetaWeight: Double,
    val strengths: List<TeamThreat>,
    val threats: List<TeamThreat>,
    val unresolvedMetaEntryKeys: List<String>
) {
    init {
        require(memberOwnedPokemonIds.size == 3)
        require(memberOwnedPokemonIds.distinct().size == 3)
        require(weightedBestBattleRating in 0.0..1000.0)
        require(coverageScorePercent in 0.0..100.0)
        require(evaluatedMetaWeight in 0.0..totalMetaWeight)
        require(threatMetaWeight >= 0.0 && severeThreatMetaWeight >= 0.0)
    }

    val evaluatedCoveragePercent: Double
        get() = if (totalMetaWeight == 0.0) 0.0 else evaluatedMetaWeight / totalMetaWeight * 100.0
}

data class TeamLineupRecommendation(
    val position: Int,
    val memberOwnedPokemonIds: List<String>,
    val memberSpeciesIds: List<String>,
    val coverage: TeamCoverageReport,
    val rationale: String
) {
    init {
        require(position > 0)
        require(memberOwnedPokemonIds.size == 3 && memberSpeciesIds.size == 3)
    }
}

data class TeamAdvisorResult(
    val status: TeamAdvisorStatus,
    val metaGroupId: String,
    val rulesetId: String,
    val rulesetVersion: String,
    val certificationRoute: String?,
    val recommendations: List<TeamLineupRecommendation>,
    val exclusions: List<TeamCandidateExclusion>,
    val refusalReason: String? = null
) {
    init {
        if (status == TeamAdvisorStatus.REFUSED) require(!refusalReason.isNullOrBlank())
        require(recommendations.map { it.position } == (1..recommendations.size).toList())
    }
}
