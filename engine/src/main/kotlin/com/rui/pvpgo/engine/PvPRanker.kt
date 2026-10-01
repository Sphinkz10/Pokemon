package com.rui.pvpgo.engine

object PvPRanker {
    private data class Candidate(
        val iv: IvSpread,
        val level: Double,
        val cp: Int,
        val stats: BattleStats
    )

    private val statProductComparator =
        compareByDescending<Candidate> { it.stats.statProduct }
            // Deterministic tie-break aligned with current PvP IV tools:
            // higher real Attack (CMP), then HP, CP and Stamina IV.
            .thenByDescending { it.stats.attack }
            .thenByDescending { it.stats.hp }
            .thenByDescending { it.cp }
            .thenByDescending { it.iv.stamina }
            .thenByDescending { it.stats.defense }
            .thenByDescending { it.iv.defense }
            .thenByDescending { it.iv.attack }
            .thenByDescending { it.level }

    private val mirrorComparator =
        compareByDescending<Candidate> { it.stats.attack }
            .thenByDescending { it.stats.statProduct }
            .thenByDescending { it.stats.hp }
            .thenByDescending { it.cp }
            .thenByDescending { it.iv.stamina }
            .thenByDescending { it.stats.defense }
            .thenByDescending { it.iv.defense }
            .thenByDescending { it.iv.attack }

    fun rank(
        species: PokemonSpecies,
        league: League,
        settings: RankSettings = RankSettings()
    ): List<PvPRankEntry> {
        val candidates = ArrayList<Candidate>(settings.spreadCount)
        val cap = league.cpCap

        for (atk in settings.ivFloor..15) {
            for (def in settings.ivFloor..15) {
                for (sta in settings.ivFloor..15) {
                    val iv = IvSpread(atk, def, sta)
                    val level = if (cap == null) {
                        settings.maxLevel
                    } else {
                        PokemonMath.highestLevelAtOrBelowCap(
                            species = species,
                            iv = iv,
                            cap = cap,
                            minLevel = settings.minLevel,
                            maxLevel = settings.maxLevel
                        ) ?: continue
                    }
                    candidates += Candidate(
                        iv = iv,
                        level = level,
                        cp = PokemonMath.cp(species, iv, level),
                        stats = PokemonMath.battleStats(species, iv, level)
                    )
                }
            }
        }
        return toRanked(candidates)
    }

    fun find(
        species: PokemonSpecies,
        league: League,
        iv: IvSpread,
        settings: RankSettings = RankSettings()
    ): PvPRankEntry? = rank(species, league, settings).firstOrNull { it.iv == iv }

    private fun toRanked(candidates: List<Candidate>): List<PvPRankEntry> {
        val sorted = candidates.sortedWith(statProductComparator)
        val best = sorted.firstOrNull()?.stats?.statProduct ?: return emptyList()
        val mirrorRankByIv = candidates.sortedWith(mirrorComparator)
            .mapIndexed { index, candidate -> candidate.iv to (index + 1) }
            .toMap()

        return sorted.mapIndexed { index, c ->
            PvPRankEntry(
                rank = index + 1,
                mirrorRank = mirrorRankByIv.getValue(c.iv),
                iv = c.iv,
                level = c.level,
                cp = c.cp,
                stats = c.stats,
                percentOfRank1 = c.stats.statProduct / best * 100.0
            )
        }
    }
}
