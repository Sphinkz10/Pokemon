package com.rui.pvpgo

import com.rui.pvpgo.domain.BuildStatus
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.domain.PvpBuildPlan
import com.rui.pvpgo.domain.SavedTeam
import com.rui.pvpgo.domain.TeamRole
import com.rui.pvpgo.engine.League

/** Pure display policy; never invents a best team, league eligibility or battle odds. */
object TeamsGoldenPolicy {
    fun forLeague(teams: List<SavedTeam>, league: League): List<SavedTeam> =
        teams.filter { it.league == league }
            .sortedWith(compareByDescending<SavedTeam> { it.isPrimary }.thenByDescending { it.updatedAtEpochMs })

    fun completeCandidates(owned: List<OwnedPokemon>): List<OwnedPokemon> =
        owned.filter { it.level != null && !it.fastMoveId.isNullOrBlank() && it.chargedMoveIds.isNotEmpty() }
            .sortedWith(compareBy<OwnedPokemon> { it.speciesId }.thenBy { it.id })

    fun memberNames(team: SavedTeam, ownedById: Map<String, OwnedPokemon>, names: Map<String, String>): String =
        listOf(TeamRole.LEAD, TeamRole.SAFE_SWITCH, TeamRole.CLOSER).joinToString(" · ") { role ->
            val member = team.members.firstOrNull { it.role == role }
            ownedById[member?.ownedPokemonId]?.let { names[it.speciesId] ?: it.speciesId } ?: "Em falta"
        }

    fun summary(team: SavedTeam, ownedById: Map<String, OwnedPokemon>, plans: List<PvpBuildPlan>): String {
        val missing = team.members.count { ownedById[it.ownedPokemonId] == null }
        val readyIds = plans.filter { it.league == team.league && it.status in setOf(BuildStatus.READY, BuildStatus.ACTIVE) }
            .map { it.ownedPokemonId }.toSet()
        val ready = team.members.count { it.ownedPokemonId in readyIds && ownedById.containsKey(it.ownedPokemonId) }
        return if (missing > 0) "$missing exemplar(es) em falta · $ready/3 planos READY/ACTIVE"
            else "$ready/3 planos READY/ACTIVE · sem cobertura estimada"
    }
}
