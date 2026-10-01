package com.rui.pvpgo.domain

import com.rui.pvpgo.engine.League

enum class BattleOutcome { WIN, LOSS, DRAW }
data class BattleRecord(val id: String, val league: League, val playedAtEpochMs: Long, val outcome: BattleOutcome)
data class SavedTeam(val id: String, val league: League, val isPrimary: Boolean, val updatedAtEpochMs: Long)
data class OwnedPokemon(val id: String, val level: Double?, val fastMoveId: String?, val chargedMoveIds: List<String>)
enum class MatchupAdvisorStatus { READY, REFUSED, NO_ELIGIBLE_CANDIDATES }
data class MatchupAdvisorResult(val status: MatchupAdvisorStatus, val recommendations: List<String>)
