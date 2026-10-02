package com.rui.pvpgo

/** A Pokémon GO PvP team contains exactly three slots. */
internal object TeamLockPolicy {
    fun hasValidLockCount(lockedCount: Int): Boolean = lockedCount in 0..3
}
