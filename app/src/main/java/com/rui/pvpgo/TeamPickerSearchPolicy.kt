package com.rui.pvpgo

import com.rui.pvpgo.domain.OwnedPokemon

/** Search only local, user-owned specimens; no network or fabricated candidates. */
internal object TeamPickerSearchPolicy {
    fun matches(candidate: OwnedPokemon, speciesName: String, dex: Int?, query: String): Boolean {
        val term = query.trim().removePrefix("#").trim()
        if (term.isEmpty()) return true
        return speciesName.contains(term, ignoreCase = true) ||
            candidate.nickname?.contains(term, ignoreCase = true) == true ||
            candidate.speciesId.contains(term, ignoreCase = true) ||
            dex?.toString() == term
    }
}
