package com.rui.pvpgo

import com.rui.pvpgo.domain.SavedTeam

/** Pure transformation: never modifies the original team or its collection specimens. */
internal object SavedTeamMutationPolicy {
    fun duplicate(original: SavedTeam, newId: String, now: Long): SavedTeam {
        require(newId.isNotBlank() && newId != original.id) { "A duplicate needs a fresh identity" }
        require(now >= 0L) { "Timestamp must be non-negative" }
        return original.copy(
            id = newId,
            name = "${original.name.take(52).trimEnd()} (cópia)",
            isPrimary = false,
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )
    }
    /** Swap two role assignments without changing owned specimens or team identity. */
    fun swapRoles(original: SavedTeam, first: com.rui.pvpgo.domain.TeamRole,
                  second: com.rui.pvpgo.domain.TeamRole, now: Long): SavedTeam {
        require(now >= original.updatedAtEpochMs) { "Timestamp must not move backwards" }
        if (first == second) return original
        val swapped = original.members.map { member ->
            when (member.role) {
                first -> member.copy(role = second)
                second -> member.copy(role = first)
                else -> member
            }
        }
        return original.copy(members = swapped, updatedAtEpochMs = now)
    }

    /** Replace exactly one role with a distinct eligible specimen from the user's collection. */
    fun replaceMember(original: SavedTeam, role: com.rui.pvpgo.domain.TeamRole,
                      replacement: com.rui.pvpgo.domain.OwnedPokemon,
                      collection: List<com.rui.pvpgo.domain.OwnedPokemon>, now: Long): SavedTeam {
        require(now >= original.updatedAtEpochMs) { "Timestamp must not move backwards" }
        require(TeamsGoldenPolicy.replacementCandidates(original, role, collection)
            .any { it.id == replacement.id }) {
            "Specimen is missing, incomplete, over the CP cap or already assigned"
        }
        val updated = original.members.map { member ->
            if (member.role == role) member.copy(ownedPokemonId = replacement.id) else member
        }
        return original.copy(members = updated, updatedAtEpochMs = now)
    }

}
