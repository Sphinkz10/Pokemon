package com.rui.pvpgo.engine

import com.rui.pvpgo.domain.PvPRuleset
import com.rui.pvpgo.domain.RulesetIneligibilityReason

/**
 * Fail-closed, owned-Pokémon eligibility decision.
 *
 * RulesetEngine.evaluate() intentionally supports species-only queries. Those
 * queries must NEVER be used to mark an owned Pokémon as ready to battle when
 * its CP, level, or event-specific evolution rules are unknown.
 *
 * Cup-specific requirements must come from a versioned event ruleset. In
 * particular, a 500 CP cap alone does not define every Little Cup format.
 */
enum class OwnedEligibilityStatus { CONFIRMED, NEEDS_DATA, INELIGIBLE }

enum class OwnedEligibilityIssue {
    CP_UNKNOWN,
    LEVEL_UNKNOWN,
    INVALID_CP,
    EVOLUTION_UNKNOWN,
    EVOLUTION_NOT_POSSIBLE,
    PRIMAL_NOT_ALLOWED
}

data class OwnedEligibilityDecision(
    val status: OwnedEligibilityStatus,
    val rulesetReasons: List<RulesetIneligibilityReason>,
    val issues: Set<OwnedEligibilityIssue>
) {
    val confirmed: Boolean get() = status == OwnedEligibilityStatus.CONFIRMED
}

object OwnedEligibilityGate {
    fun evaluate(
        ruleset: PvPRuleset,
        species: PokemonSpecies,
        cp: Int?,
        level: Double?,
        requiresEvolutionPotential: Boolean = false,
        canEvolve: Boolean? = null,
        allowPrimal: Boolean = false
    ): OwnedEligibilityDecision {
        val hardReasons = RulesetEngine.evaluate(ruleset, species, level, cp).reasons
        val issues = mutableSetOf<OwnedEligibilityIssue>()

        if (cp == null && ruleset.cpCap != null) issues += OwnedEligibilityIssue.CP_UNKNOWN
        if (cp != null && cp <= 0) issues += OwnedEligibilityIssue.INVALID_CP
        if (level == null) issues += OwnedEligibilityIssue.LEVEL_UNKNOWN

        // The existing "mega" flag does not reliably describe Primal forms.
        val primal = species.tags.any { it.equals("primal", ignoreCase = true) } ||
            species.speciesId.endsWith("_primal", ignoreCase = true) ||
            species.form.startsWith("primal", ignoreCase = true)
        if (primal && !allowPrimal) issues += OwnedEligibilityIssue.PRIMAL_NOT_ALLOWED

        if (ruleset.requiresEvolutionPotential || requiresEvolutionPotential) {
            // A positive evolution link is useful evidence; a missing link
            // is NOT evidence that evolution is impossible in an incomplete pack.
            val resolvedEvolutionPotential = canEvolve ?: species.evolutionSpeciesIds
                .takeIf { it.isNotEmpty() }?.let { true }
            when (resolvedEvolutionPotential) {
                true -> Unit
                false -> issues += OwnedEligibilityIssue.EVOLUTION_NOT_POSSIBLE
                null -> issues += OwnedEligibilityIssue.EVOLUTION_UNKNOWN
            }
        }
        val definitelyIneligible = hardReasons.isNotEmpty() ||
            OwnedEligibilityIssue.INVALID_CP in issues ||
            OwnedEligibilityIssue.PRIMAL_NOT_ALLOWED in issues ||
            OwnedEligibilityIssue.EVOLUTION_NOT_POSSIBLE in issues
        val status = when {
            definitelyIneligible -> OwnedEligibilityStatus.INELIGIBLE
            issues.isNotEmpty() -> OwnedEligibilityStatus.NEEDS_DATA
            else -> OwnedEligibilityStatus.CONFIRMED
        }
        return OwnedEligibilityDecision(status, hardReasons, issues.toSet())
    }
}
