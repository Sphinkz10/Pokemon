package com.rui.pvpgo

import com.rui.pvpgo.engine.League

/**
 * Shared presentation contract for species, exemplars and comparison.
 * Never represents IV total as PvP rank.
 */
object SpeciesLeaguePolicy {
    val selectable: List<League> = listOf(League.LITTLE, League.GREAT, League.ULTRA, League.MASTER)

    fun shortLabel(league: League): String = when (league) {
        League.LITTLE -> "Little"
        League.GREAT -> "Great"
        League.ULTRA -> "Ultra"
        League.MASTER -> "Master"
    }

    fun title(league: League): String = shortLabel(league) + " League"

    fun cpLimitLabel(league: League): String = when (league) {
        League.LITTLE -> "Até 500 CP"
        League.GREAT -> "Até 1 500 CP"
        League.ULTRA -> "Até 2 500 CP"
        League.MASTER -> "Sem limite CP"
    }

    fun rankLabel(rank: Int?): String = rank?.let { "#$it" } ?: "—"

    fun statusLabel(rank: Int?, computing: Boolean): String =
        if (computing) "A calcular ranking…" else
            rank?.let { "Rank #$it" } ?: "Ranking indisponível"

    fun validSelection(firstId: String?, secondId: String?): Boolean =
        firstId != null && secondId != null && firstId != secondId
}
