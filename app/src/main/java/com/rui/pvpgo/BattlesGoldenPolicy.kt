package com.rui.pvpgo

import com.rui.pvpgo.domain.BattleOutcome
import com.rui.pvpgo.domain.BattleRecord
import com.rui.pvpgo.domain.MatchupAdvisorResult
import com.rui.pvpgo.domain.MatchupAdvisorStatus
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.domain.SavedTeam
import com.rui.pvpgo.engine.League

/** UI projections only. Battle maths and confidence gates remain owned by the engine/advisor. */
object BattlesGoldenPolicy {
    fun forLeague(records: List<BattleRecord>, league: League): List<BattleRecord> =
        records.asSequence().filter { it.league == league }
            .sortedWith(compareByDescending<BattleRecord> { it.playedAtEpochMs }.thenBy { it.id })
            .toList()

    fun wins(records: List<BattleRecord>): Int = records.count { it.outcome == BattleOutcome.WIN }
    fun losses(records: List<BattleRecord>): Int = records.count { it.outcome == BattleOutcome.LOSS }
    fun draws(records: List<BattleRecord>): Int = records.count { it.outcome == BattleOutcome.DRAW }

    /** Explicitly records only. Never a future win probability or 3v3 forecast. */
    fun historyLabel(records: List<BattleRecord>): String =
        if (records.isEmpty()) "Sem batalhas registadas"
        else "${records.size} registadas · ${wins(records)} V / ${losses(records)} D / ${draws(records)} E"

    fun eligibleTeams(teams: List<SavedTeam>, league: League): List<SavedTeam> =
        teams.filter { it.league == league }
            .sortedWith(compareByDescending<SavedTeam> { it.isPrimary }.thenByDescending { it.updatedAtEpochMs })

    fun completeOwnedCount(owned: List<OwnedPokemon>): Int = owned.count {
        it.level != null && !it.fastMoveId.isNullOrBlank() && it.chargedMoveIds.isNotEmpty()
    }

    fun resultHeadline(result: MatchupAdvisorResult): String = when (result.status) {
        MatchupAdvisorStatus.READY -> "Análise 1v1 disponível"
        MatchupAdvisorStatus.REFUSED -> "Análise recusada"
        MatchupAdvisorStatus.NO_ELIGIBLE_CANDIDATES -> "Sem candidatos elegíveis"
    }

    fun canShowRatings(result: MatchupAdvisorResult): Boolean =
        result.status == MatchupAdvisorStatus.READY && result.recommendations.isNotEmpty()

    const val RATING_NOTE = "Battle Rating 0–1000 (simulação 1v1). Não é probabilidade de vitória nem previsão de equipa 3v3."
}
