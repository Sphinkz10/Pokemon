package com.rui.pvpgo.domain

import com.rui.pvpgo.engine.IvSpread
import com.rui.pvpgo.engine.League

enum class AcquisitionMethod {
    WILD, RAID, RESEARCH, EGG, TRADE, ROCKET, EVENT, UNKNOWN
}

enum class BuildStatus {
    IDEA, PLANNED, IN_PROGRESS, READY, ACTIVE, ARCHIVED
}

data class OwnedPokemon(
    val id: String,
    val speciesId: String,
    val iv: IvSpread,
    val cp: Int? = null,
    val level: Double? = null,
    val nickname: String? = null,
    val isShadow: Boolean = false,
    val isPurified: Boolean = false,
    val isShiny: Boolean = false,
    val isLucky: Boolean = false,
    val isBestBuddy: Boolean = false,
    val isFavorite: Boolean = false,
    val fastMoveId: String? = null,
    val chargedMoveIds: List<String> = emptyList(),
    val secondChargedMoveUnlocked: Boolean? = null,
    val tags: Set<String> = emptySet(),
    val acquisitionMethod: AcquisitionMethod = AcquisitionMethod.UNKNOWN,
    val caughtAtEpochMs: Long? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long = createdAtEpochMs,
    val externalSource: String? = null,
    val externalId: String? = null,
    val lastVerifiedAtEpochMs: Long? = null,
    val verificationSource: CollectionVerificationSource = CollectionVerificationSource.MANUAL,
    val uncertainFields: Set<OwnedPokemonField> = emptySet()
) {
    init {
        require(id.isNotBlank())
        require(speciesId.isNotBlank())
        require(cp == null || cp > 0)
        require(level == null || (level in 1.0..51.0 && level * 2 == (level * 2).toInt().toDouble()))
        require(chargedMoveIds.size <= 2)
        require(secondChargedMoveUnlocked != false || chargedMoveIds.size <= 1) { "Two charged moves imply the second charged slot is unlocked" }
        require(updatedAtEpochMs >= createdAtEpochMs)
        require(lastVerifiedAtEpochMs == null || lastVerifiedAtEpochMs >= 0)
    }
}

data class PvpBuildPlan(
    val id: String,
    val ownedPokemonId: String,
    val league: League,
    val bestBuddyAllowed: Boolean = false,
    val desiredFastMoveId: String? = null,
    val desiredChargedMoveIds: List<String> = emptyList(),
    val status: BuildStatus = BuildStatus.IDEA,
    val priority: Int = 0,
    val notes: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long = createdAtEpochMs
) {
    init {
        require(id.isNotBlank())
        require(ownedPokemonId.isNotBlank())
        require(desiredChargedMoveIds.size <= 2)
        require(priority in 0..100)
        require(updatedAtEpochMs >= createdAtEpochMs)
    }
}

enum class TeamRole { LEAD, SAFE_SWITCH, CLOSER }

data class TeamMember(
    val ownedPokemonId: String,
    val role: TeamRole
) {
    init { require(ownedPokemonId.isNotBlank()) }
}

data class SavedTeam(
    val id: String,
    val name: String,
    val league: League,
    val members: List<TeamMember>,
    val isPrimary: Boolean = false,
    val notes: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long = createdAtEpochMs
) {
    init {
        require(id.isNotBlank())
        require(name.isNotBlank())
        require(members.size == 3) { "PvP teams must have exactly 3 members" }
        require(members.map { it.ownedPokemonId }.distinct().size == 3) { "A team cannot reuse the same owned Pokémon" }
        require(members.map { it.role }.toSet() == TeamRole.entries.toSet()) { "A team must contain Lead, Safe Switch and Closer roles" }
        require(updatedAtEpochMs >= createdAtEpochMs)
    }
}
