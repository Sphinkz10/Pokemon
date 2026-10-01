package com.rui.pvpgo

import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.domain.PvpBuildPlan
import com.rui.pvpgo.engine.PvPRankEntry

/** Pure policy. All ranks are supplied by the engine for the currently selected league. */
object SpeciesCollectionPolicy {
    fun sortedForLeague(owned: List<OwnedPokemon>, ranks: Map<String, PvPRankEntry>?): List<OwnedPokemon> =
        owned.sortedWith(
            compareBy<OwnedPokemon> { ranks?.get(it.id)?.rank ?: Int.MAX_VALUE }
                .thenBy { it.id }
        )

    fun bestForLeague(owned: List<OwnedPokemon>, ranks: Map<String, PvPRankEntry>?): OwnedPokemon? =
        if (ranks == null) null
        else sortedForLeague(owned.filter { it.id in ranks }, ranks).firstOrNull()

    // Backward compatibility for existing callers supplying Great League ranks.
    fun sortedForGreat(owned: List<OwnedPokemon>, ranks: Map<String, PvPRankEntry>?): List<OwnedPokemon> =
        sortedForLeague(owned, ranks)

    fun bestForGreat(owned: List<OwnedPokemon>, ranks: Map<String, PvPRankEntry>?): OwnedPokemon? =
        bestForLeague(owned, ranks)

    fun isProtected(item: OwnedPokemon, plans: List<PvpBuildPlan>): Boolean =
        item.isFavorite || plans.any { it.ownedPokemonId == item.id }

    fun validComparison(owned: List<OwnedPokemon>, firstId: String?, secondId: String?): Boolean {
        if (firstId.isNullOrBlank() || secondId.isNullOrBlank() || firstId == secondId) return false
        val a = owned.firstOrNull { it.id == firstId } ?: return false
        val b = owned.firstOrNull { it.id == secondId } ?: return false
        return a.speciesId == b.speciesId
    }

    fun higherIvTotal(a: OwnedPokemon, b: OwnedPokemon): String = when {
        a.iv.total > b.iv.total -> "A"
        a.iv.total < b.iv.total -> "B"
        else -> "Empate"
    }

    fun betterLeagueRank(a: OwnedPokemon, b: OwnedPokemon, ranks: Map<String, PvPRankEntry>?): String {
        val ra = ranks?.get(a.id)?.rank ?: return "Indisponível"
        val rb = ranks[b.id]?.rank ?: return "Indisponível"
        return when {
            ra < rb -> "A"
            rb < ra -> "B"
            else -> "Empate"
        }
    }

    fun betterGreatRank(a: OwnedPokemon, b: OwnedPokemon, ranks: Map<String, PvPRankEntry>?): String =
        betterLeagueRank(a, b, ranks)
}
