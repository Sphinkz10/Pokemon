package com.rui.pvpgo.engine

/**
 * Versioned registry for intentional local-vs-reference differences.
 *
 * PvPoke is the primary parity reference, not an infallible authority. When the pinned revision
 * has a documented defect and the intended mechanic is independently clear, the local engine may
 * model the intended mechanic while keeping the mismatch explicit, testable and advice-gated.
 */
enum class DivergenceDisposition {
    LOCAL_CORRECTNESS_WINS,
    REFERENCE_BEHAVIOR_RETAINED,
    INVESTIGATE
}

data class KnownDivergence(
    val id: String,
    val affectedSpeciesIds: Set<String> = emptySet(),
    val affectedCapability: String,
    val upstreamCommitSha: String,
    val upstreamIssueUrl: String?,
    val referenceBehavior: String,
    val intendedBehavior: String,
    val disposition: DivergenceDisposition,
    val authoritativeAdviceBlocked: Boolean = true
)

object KnownDivergencesRegistry {
    val entries: List<KnownDivergence> = listOf(
        KnownDivergence(
            id = "a93147b-morpeko-toggle-one-way",
            affectedSpeciesIds = setOf("morpeko_full_belly", "morpeko_hangry"),
            affectedCapability = "special-forms",
            upstreamCommitSha = PvPokeReferencePin.COMMIT_SHA,
            upstreamIssueUrl = "https://github.com/pvpoke/pvpoke/issues/379",
            referenceBehavior = "Pinned Battle.js changes Full Belly -> Hangry once and then remains Hangry because the toggle type is not consumed.",
            intendedBehavior = "Every Charged Move toggles Full Belly <-> Hangry; Aura Wheel Electric/Dark follows the active form; switching resets to Full Belly.",
            disposition = DivergenceDisposition.LOCAL_CORRECTNESS_WINS,
            authoritativeAdviceBlocked = true
        ),
        KnownDivergence(
            id = "a93147b-actionlogic-dead-dominance-pruning",
            affectedCapability = "action-dp-core",
            upstreamCommitSha = PvPokeReferencePin.COMMIT_SHA,
            upstreamIssueUrl = "https://github.com/pvpoke/pvpoke/issues/380",
            referenceBehavior = "Pinned ActionLogic dominance checks read nonexistent hp/shields fields, leaving intended pruning inactive.",
            intendedBehavior = "Dominance pruning compares opponent HP/shields together with turn, energy and buff advantage.",
            disposition = DivergenceDisposition.LOCAL_CORRECTNESS_WINS,
            authoritativeAdviceBlocked = false
        )
    )

    fun forSpecies(speciesId: String): List<KnownDivergence> =
        entries.filter { speciesId.lowercase() in it.affectedSpeciesIds }

    fun forCapability(capability: String): List<KnownDivergence> =
        entries.filter { it.affectedCapability == capability }

    fun expectedForPinnedReference(speciesId: String): Boolean =
        forSpecies(speciesId).any { it.upstreamCommitSha == PvPokeReferencePin.COMMIT_SHA }
}
