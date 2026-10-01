package com.rui.pvpgo

import com.rui.pvpgo.engine.IvSpread
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.PokemonSpecies
import com.rui.pvpgo.engine.PvPRankEntry
import com.rui.pvpgo.engine.PvPRanker
import com.rui.pvpgo.engine.RankSettings
import java.util.LinkedHashMap

/**
 * Bounded in-memory rank-table cache.
 *
 * A single rank table contains up to 4,096 entries, so an unbounded cache would
 * grow noticeably after browsing many species. 24 tables keeps roughly the last
 * eight species (Great/Ultra/Master) hot without retaining the whole Pokédex.
 */
object RankRepository {
    private const val MAX_TABLES = 24

    private data class Key(
        val speciesId: String,
        val dex: Int,
        val name: String,
        val league: League,
        val settings: RankSettings
    )

    private data class Table(
        val rows: List<PvPRankEntry>,
        val byIv: Map<IvSpread, PvPRankEntry>
    )

    private val cache = object : LinkedHashMap<Key, Table>(MAX_TABLES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Table>?): Boolean = size > MAX_TABLES
    }

    private fun table(
        species: PokemonSpecies,
        league: League,
        settings: RankSettings
    ): Table {
        val key = Key(species.speciesId, species.dex, species.name, league, settings)
        synchronized(cache) { cache[key] }?.let { return it }

        val rows = PvPRanker.rank(species, league, settings)
        val generated = Table(rows, rows.associateBy { it.iv })

        return synchronized(cache) {
            cache[key] ?: generated.also { cache[key] = it }
        }
    }

    fun ranks(
        species: PokemonSpecies,
        league: League,
        settings: RankSettings = RankSettings()
    ): List<PvPRankEntry> = table(species, league, settings).rows

    fun find(
        species: PokemonSpecies,
        league: League,
        iv: IvSpread,
        settings: RankSettings = RankSettings()
    ): PvPRankEntry? = table(species, league, settings).byIv[iv]

    fun clear() = synchronized(cache) { cache.clear() }

    internal fun cacheSizeForValidation(): Int = synchronized(cache) { cache.size }
}
