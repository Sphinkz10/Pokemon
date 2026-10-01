package com.rui.pvpgo.domain

import com.rui.pvpgo.engine.League

data class OpponentMetaStats(
    val speciesId: String,
    val encounters: Int,
    val wins: Int,
    val losses: Int,
    val draws: Int,
    val leadEncounters: Int
) {
    val winRatePercent: Double
        get() = if (encounters == 0) 0.0 else wins * 100.0 / encounters

    val lossRatePercent: Double
        get() = if (encounters == 0) 0.0 else losses * 100.0 / encounters
}

data class BattleSummary(
    val league: League?,
    val total: Int,
    val wins: Int,
    val losses: Int,
    val draws: Int,
    val opponents: List<OpponentMetaStats>
) {
    val winRatePercent: Double
        get() = if (total == 0) 0.0 else wins * 100.0 / total
}

object BattleAnalytics {
    fun summarize(records: List<BattleRecord>, league: League? = null): BattleSummary {
        val filtered = if (league == null) records else records.filter { it.league == league }
        val wins = filtered.count { it.outcome == BattleOutcome.WIN }
        val losses = filtered.count { it.outcome == BattleOutcome.LOSS }
        val draws = filtered.count { it.outcome == BattleOutcome.DRAW }

        data class MutableStats(
            var encounters: Int = 0,
            var wins: Int = 0,
            var losses: Int = 0,
            var draws: Int = 0,
            var leadEncounters: Int = 0
        )

        val bySpecies = linkedMapOf<String, MutableStats>()
        for (battle in filtered) {
            // A species counts once per battle even if bad/duplicate imported data mentions it twice.
            val uniqueSpecies = battle.opponentPokemon.distinctBy { it.speciesId }
            for (opponent in uniqueSpecies) {
                val stats = bySpecies.getOrPut(opponent.speciesId) { MutableStats() }
                stats.encounters++
                when (battle.outcome) {
                    BattleOutcome.WIN -> stats.wins++
                    BattleOutcome.LOSS -> stats.losses++
                    BattleOutcome.DRAW -> stats.draws++
                }
                if (battle.opponentPokemon.any { it.speciesId == opponent.speciesId && it.slot == BattleSlot.LEAD }) {
                    stats.leadEncounters++
                }
            }
        }

        val opponents = bySpecies.map { (speciesId, stats) ->
            OpponentMetaStats(
                speciesId = speciesId,
                encounters = stats.encounters,
                wins = stats.wins,
                losses = stats.losses,
                draws = stats.draws,
                leadEncounters = stats.leadEncounters
            )
        }.sortedWith(
            compareByDescending<OpponentMetaStats> { it.encounters }
                .thenByDescending { it.losses }
                .thenBy { it.speciesId }
        )

        return BattleSummary(
            league = league,
            total = filtered.size,
            wins = wins,
            losses = losses,
            draws = draws,
            opponents = opponents
        )
    }
}
