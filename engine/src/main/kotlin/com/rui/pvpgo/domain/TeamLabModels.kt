package com.rui.pvpgo.domain

/** F95 hard filter for Shadow usage. */
enum class ShadowPolicy { ANY, NORMAL_ONLY, SHADOW_ONLY }

/** F96 soft preference only. Never overrides hard ruleset/trust constraints. */
enum class TeamStyleBias { BALANCED, BULKY, AGGRESSIVE }

data class PlaystyleProfile(
    val styleBias: TeamStyleBias = TeamStyleBias.BALANCED,
    val preferredOwnedPokemonIds: Set<String> = emptySet(),
    val avoidedOwnedPokemonIds: Set<String> = emptySet(),
    val safeSwitchComfortOwnedPokemonIds: Set<String> = emptySet()
) {
    init {
        require(preferredOwnedPokemonIds.intersect(avoidedOwnedPokemonIds).isEmpty())
    }
}

enum class CoverageState { STRONG, COVERED, CLOSE, THREAT, SEVERE_THREAT, UNRESOLVED }

data class TeamCoverageMatrixRow(
    val metaEntryKey: String,
    val opponentSpeciesId: String,
    val metaWeight: Double,
    val memberRatings: Map<String, Int?>,
    val bestOwnedPokemonId: String?,
    val bestBattleRating: Int?,
    val state: CoverageState
) {
    init {
        require(metaEntryKey.isNotBlank())
        require(opponentSpeciesId.isNotBlank())
        require(metaWeight > 0.0)
        require(memberRatings.size == 3)
        require(bestBattleRating == null || bestBattleRating in 0..1000)
    }
}

data class TeamCoverageMatrix(
    val metaGroupId: String,
    val memberOwnedPokemonIds: List<String>,
    val rows: List<TeamCoverageMatrixRow>
) {
    init {
        require(memberOwnedPokemonIds.size == 3 && memberOwnedPokemonIds.distinct().size == 3)
    }
}

enum class SuggestedTeamRole { LEAD, SAFE_SWITCH, CLOSER }

data class TeamRoleSuggestion(
    val ownedPokemonId: String,
    val role: SuggestedTeamRole,
    /** This is a transparent heuristic, not a 3v3 battle simulation. */
    val basis: String
)

enum class TeamAlternativeKind {
    BEST_COVERAGE,
    SAFEST,
    BULKY,
    AGGRESSIVE,
    COMFORT,
    CHEAPEST,
    NO_XL
}

data class TeamLabAlternative(
    val kind: TeamAlternativeKind,
    val memberOwnedPokemonIds: List<String>,
    val coverageScorePercent: Double,
    /** Internal ranking aid; not win probability. */
    val labScore: Double,
    val reason: String
) {
    init {
        require(memberOwnedPokemonIds.size == 3 && memberOwnedPokemonIds.distinct().size == 3)
        require(coverageScorePercent in 0.0..100.0)
        require(labScore.isFinite())
    }
}

data class WhyThisTeam(
    val summary: String,
    val selectionReasons: List<String>,
    val strengths: List<String>,
    val risks: List<String>,
    val constraintsApplied: List<String>,
    val caveats: List<String>
)

enum class TeamLabStatus { READY, REFUSED, INSUFFICIENT_CANDIDATES }

data class TeamLabResult(
    val status: TeamLabStatus,
    val metaGroupId: String,
    val rulesetId: String,
    val rulesetVersion: String,
    val certificationRoute: String?,
    val primary: TeamLineupRecommendation?,
    /** Heuristic score used only to rank already-valid lineups. */
    val primaryLabScore: Double?,
    val roleSuggestions: List<TeamRoleSuggestion>,
    val coverageMatrix: TeamCoverageMatrix?,
    val alternatives: List<TeamLabAlternative>,
    val why: WhyThisTeam?,
    val exclusions: List<TeamCandidateExclusion>,
    val refusalReason: String? = null
) {
    init {
        if (status == TeamLabStatus.READY) {
            require(primary != null && coverageMatrix != null && why != null && primaryLabScore != null)
            require(roleSuggestions.size == 3)
            require(roleSuggestions.map { it.ownedPokemonId }.toSet() == primary.memberOwnedPokemonIds.toSet())
        }
        if (status == TeamLabStatus.REFUSED) require(!refusalReason.isNullOrBlank())
    }
}
