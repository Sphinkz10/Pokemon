package com.rui.pvpgo

import com.rui.pvpgo.domain.BuildStatus
import com.rui.pvpgo.domain.PvpBuildPlan
import com.rui.pvpgo.engine.League

/**
 * A plan is identified by its exact OWNED specimen and exact PvP league.
 * Never display a Great League plan under an Ultra/Master guide selection.
 *
 * Archived plans remain visible for explicit review. If duplicates are
 * present for the same specimen/league, prefer a non-archived plan over an
 * archived one, then highest priority, then latest edit, then stable ID.
 * This is a UI selection only; it never writes or changes saved plans.
 */
internal object CollectionPlanLeaguePolicy {
    fun preferredByOwned(
        plans: List<PvpBuildPlan>,
        league: League
    ): Map<String, PvpBuildPlan> =
        plans.asSequence()
            .filter { it.league == league && it.ownedPokemonId.isNotBlank() }
            .groupBy { it.ownedPokemonId }
            .mapValues { (_, candidates) ->
                candidates.maxWithOrNull(
                    compareBy<PvpBuildPlan> { it.status != BuildStatus.ARCHIVED }
                        .thenBy { it.priority }
                        .thenBy { it.updatedAtEpochMs }
                        .thenBy { it.id }
                )!!
            }

    /**
     * Archived plans remain accessible in the historical review list, but
     * they do not count as an active plan and must not prevent the user
     * from starting a new plan for the same specimen and league.
     */
    fun hasNonArchivedPlanInLeague(
        ownedId: String,
        league: League,
        plans: List<PvpBuildPlan>
    ): Boolean = ownedId.isNotBlank() && plans.any {
        it.ownedPokemonId == ownedId && it.league == league &&
            it.status != BuildStatus.ARCHIVED
    }

    fun hasSavedPlanInLeague(
        ownedId: String,
        league: League,
        plans: List<PvpBuildPlan>
    ): Boolean =
        ownedId.isNotBlank() && plans.any {
            it.ownedPokemonId == ownedId && it.league == league
        }
}
