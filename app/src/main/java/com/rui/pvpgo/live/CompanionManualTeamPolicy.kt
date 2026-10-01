package com.rui.pvpgo.live

import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.domain.SavedTeam
import com.rui.pvpgo.domain.TeamRole

/** Explicit user input, NOT an observation from MediaProjection or a detector. */
data class CompanionManualMember(
    val ownedPokemonId: String,
    val speciesId: String,
    val displayName: String,
    val role: TeamRole
)

data class CompanionManualTeam(
    val id: String,
    val name: String,
    val leagueName: String,
    val selectable: Boolean,
    val disabledReason: String?,
    val members: List<CompanionManualMember>
)

/** Transient manual context: never becomes a LiveBattleEvent or persisted BattleRecord. */
data class CompanionTeamSelection(
    val sessionId: String? = null,
    val selectedTeamId: String? = null,
    val activeOwnedPokemonId: String? = null
) {
    fun chooseTeam(id: String?, options: List<CompanionManualTeam>): CompanionTeamSelection {
        val selected = options.singleOrNull { it.id == id && it.selectable }
        return copy(selectedTeamId = selected?.id, activeOwnedPokemonId = null)
    }

    fun chooseActive(id: String?, options: List<CompanionManualTeam>): CompanionTeamSelection {
        val team = options.singleOrNull { it.id == selectedTeamId && it.selectable }
        val valid = team?.members?.any { it.ownedPokemonId == id } == true
        return copy(activeOwnedPokemonId = if (valid) id else null)
    }

    fun validated(options: List<CompanionManualTeam>): CompanionTeamSelection {
        val team = options.singleOrNull { it.id == selectedTeamId && it.selectable }
        if (team == null) return copy(selectedTeamId = null, activeOwnedPokemonId = null)
        if (team.members.none { it.ownedPokemonId == activeOwnedPokemonId })
            return copy(activeOwnedPokemonId = null)
        return this
    }

    /** A capture session boundary invalidates the manual active slot, never infers the lead. */
    fun forSession(nextSessionId: String?): CompanionTeamSelection =
        if (sessionId == nextSessionId) this else copy(sessionId = nextSessionId, activeOwnedPokemonId = null)
}

/** Read-only projection of saved Room state, with strict ownership checks. */
object CompanionManualTeamPolicy {
    fun options(teams: List<SavedTeam>, owned: List<OwnedPokemon>): List<CompanionManualTeam> {
        val byId = owned.associateBy { it.id }
        return teams.sortedWith(compareByDescending<SavedTeam> { it.isPrimary }
            .thenByDescending { it.updatedAtEpochMs }.thenBy { it.id }).map { team ->
            val completeRoles = team.members.size == 3 &&
                team.members.map { it.role }.toSet() == TeamRole.entries.toSet()
            val distinctIds = team.members.map { it.ownedPokemonId }.distinct().size == 3
            val allPresent = team.members.all { it.ownedPokemonId in byId }
            val reason = when {
                !completeRoles || !distinctIds -> "Equipa inválida: revê os três papéis."
                !allPresent -> "Exemplar removido da coleção: revê esta equipa."
                else -> null
            }
            val members = TeamRole.entries.mapNotNull { role ->
                val slot = team.members.firstOrNull { it.role == role } ?: return@mapNotNull null
                val pokemon = byId[slot.ownedPokemonId] ?: return@mapNotNull null
                CompanionManualMember(pokemon.id, pokemon.speciesId,
                    pokemon.nickname?.takeIf { it.isNotBlank() } ?: pokemon.speciesId, role)
            }
            CompanionManualTeam(team.id, team.name, team.league.name,
                reason == null, reason, members)
        }
    }
}

/** Free-text rival name typed by the user. This does not establish species identity. */
data class CompanionManualOpponent(
    val sessionId: String? = null,
    val enteredName: String? = null
) {
    fun enter(name: String): CompanionManualOpponent {
        val normalized = name.trim().replace(Regex("\\s+"), " ").take(48)
        return copy(enteredName = normalized.ifBlank { null })
    }
    fun clear(): CompanionManualOpponent = copy(enteredName = null)
    fun forSession(nextSessionId: String?): CompanionManualOpponent =
        if (nextSessionId == sessionId) this else CompanionManualOpponent(sessionId = nextSessionId)
}
