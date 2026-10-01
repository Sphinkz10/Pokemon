package com.rui.pvpgo.engine

import com.rui.pvpgo.domain.BattleOutcome
import com.rui.pvpgo.domain.BattleRecord
import com.rui.pvpgo.domain.BattleSlot
import com.rui.pvpgo.domain.OpponentBattlePokemon
import com.rui.pvpgo.domain.OwnBattlePokemon

object LiveBattleLogProjector {
    fun toBattleRecordOrNull(
        state: LiveBattleState,
        battleId: String,
        notes: String? = null,
        tags: Set<String> = setOf("live-companion")
    ): BattleRecord? {
        if (state.phase != LiveBattlePhase.COMPLETE) return null
        val league = state.league ?: return null
        val outcome = state.outcome ?: return null
        val playedAt = state.startedAtEpochMs ?: return null
        if (state.ownTeamOwnedPokemonIds.size != 3) return null

        val slots = listOf(BattleSlot.LEAD, BattleSlot.SWITCH, BattleSlot.CLOSER)
        val own = state.ownTeamOwnedPokemonIds.mapIndexed { index, id ->
            OwnBattlePokemon(ownedPokemonId = id, slot = slots[index])
        }

        val opponent = state.opponentRoster.take(3).mapIndexed { index, observed ->
            OpponentBattlePokemon(
                speciesId = observed.speciesId,
                slot = slots[index],
                isShadow = observed.isShadow,
                fastMoveId = observed.fastMoveId,
                chargedMoveIds = observed.chargedMoveIdsRevealed.toList().take(2)
            )
        }

        return BattleRecord(
            id = battleId,
            league = league,
            outcome = outcome,
            playedAtEpochMs = playedAt,
            ownPokemon = own,
            opponentPokemon = opponent,
            notes = notes,
            tags = tags + confidenceTag(state.signalConfidence)
        )
    }

    fun confidenceLabel(state: LiveBattleState): String = when {
        state.signalConfidence >= 0.9 && state.anomalies.isEmpty() -> "HIGH"
        state.signalConfidence >= 0.65 -> "MEDIUM"
        else -> "LOW"
    }

    private fun confidenceTag(confidence: Double): String = when {
        confidence >= 0.9 -> "capture-confidence:high"
        confidence >= 0.65 -> "capture-confidence:medium"
        else -> "capture-confidence:low"
    }
}
