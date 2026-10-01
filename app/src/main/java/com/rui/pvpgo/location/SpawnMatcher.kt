package com.rui.pvpgo.location

import com.rui.pvpgo.RankRepository
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.PokemonSpecies
import com.rui.pvpgo.engine.RankSettings

object SpawnMatcher {
    fun bestMatches(
        species: PokemonSpecies,
        spawns: List<SpawnSight>,
        league: League,
        maxRank: Int = 50,
        settings: RankSettings = RankSettings(),
        nowEpochMs: Long = System.currentTimeMillis()
    ): List<RankedSpawn> {
        require(maxRank >= 1)
        val wantedId = normalizeSpeciesId(species.speciesId)
        return spawns.asSequence()
            .filterNot { it.isExpired(nowEpochMs) }
            .filter { normalizeSpeciesId(it.speciesId) == wantedId }
            .mapNotNull { spawn -> RankRepository.find(species, league, spawn.iv, settings)?.let { RankedSpawn(spawn, league, it) } }
            .filter { it.rank.rank <= maxRank }
            .sortedWith(
                compareBy<RankedSpawn> { it.rank.rank }
                    .thenBy { it.spawn.expiresAtEpochMs ?: Long.MAX_VALUE }
            )
            .toList()
    }

    private fun normalizeSpeciesId(value: String): String = value
        .trim()
        .lowercase()
        .replace('-', '_')
        .replace(' ', '_')
}
