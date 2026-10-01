package com.rui.pvpgo.domain

/** Product state for one collection-vs-opponent recommendation request. */
enum class MatchupAdvisorStatus {
    READY,
    REFUSED,
    NO_ELIGIBLE_CANDIDATES
}

/**
 * A candidate is never silently dropped. Every owned Pokémon that cannot be evaluated is reported
 * with a concrete reason so the UI can tell the user what must be fixed.
 */
enum class MatchupExclusionReason {
    UNKNOWN_SPECIES,
    INCOMPLETE_BUILD,
    LEAGUE_CP_EXCEEDED,
    RULESET_INELIGIBLE,
    SPECIAL_MECHANIC_GUARD,
    EVALUATION_REFUSED
}

data class MatchupAdvisorExclusion(
    val ownedPokemonId: String,
    val speciesId: String,
    val reason: MatchupExclusionReason,
    val detail: String
)

data class MatchupAdvisorRecommendation(
    val position: Int,
    val ownedPokemonId: String,
    val speciesId: String,
    val pokemonCp: Int,
    val evaluation: MatchupEvaluation,
    /** Short user-facing explanation; Battle Rating is never presented as win probability. */
    val why: String
) {
    init {
        require(position > 0)
        require(pokemonCp > 0)
        require(ownedPokemonId == evaluation.ownedPokemonId)
    }
}

data class MatchupAdvisorResult(
    val status: MatchupAdvisorStatus,
    val opponent: OpponentBuild,
    val scenario: MatchupScenario,
    val certificationRoute: String?,
    val recommendations: List<MatchupAdvisorRecommendation>,
    val exclusions: List<MatchupAdvisorExclusion>,
    val refusalReason: String? = null
) {
    init {
        require(recommendations.map { it.position } == (1..recommendations.size).toList()) {
            "Recommendation positions must be contiguous and 1-based"
        }
        if (status == MatchupAdvisorStatus.REFUSED) require(!refusalReason.isNullOrBlank())
    }
}
