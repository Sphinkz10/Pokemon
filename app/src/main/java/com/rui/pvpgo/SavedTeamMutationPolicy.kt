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
}
