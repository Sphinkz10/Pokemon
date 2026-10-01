package com.rui.pvpgo.domain

enum class TeamRole { LEAD, SAFE_SWITCH, CLOSER }
enum class League { GREAT, ULTRA, MASTER }
data class OwnedPokemon(val id: String, val speciesId: String, val nickname: String? = null)
data class TeamMember(val ownedPokemonId: String, val role: TeamRole)
data class SavedTeam(val id: String, val name: String, val league: League,
    val members: List<TeamMember>, val isPrimary: Boolean = false,
    val updatedAtEpochMs: Long = 1)
