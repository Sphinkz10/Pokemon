package com.rui.pvpgo.domain

import com.rui.pvpgo.engine.League

enum class BattleOutcome { WIN, LOSS, DRAW }
enum class BattleSlot { LEAD, SWITCH, CLOSER }

data class OwnBattlePokemon(
    val ownedPokemonId: String,
    val slot: BattleSlot
) {
    init { require(ownedPokemonId.isNotBlank()) }
}

data class OpponentBattlePokemon(
    val speciesId: String,
    val slot: BattleSlot,
    val isShadow: Boolean? = null,
    val fastMoveId: String? = null,
    val chargedMoveIds: List<String> = emptyList()
) {
    init {
        require(speciesId.isNotBlank())
        require(chargedMoveIds.size <= 2)
    }
}

data class BattleRecord(
    val id: String,
    val league: League,
    val outcome: BattleOutcome,
    val playedAtEpochMs: Long,
    val ownTeamId: String? = null,
    val ownPokemon: List<OwnBattlePokemon>,
    val opponentPokemon: List<OpponentBattlePokemon>,
    val ratingBefore: Int? = null,
    val ratingAfter: Int? = null,
    val notes: String? = null,
    val tags: Set<String> = emptySet()
) {
    init {
        require(id.isNotBlank())
        require(ownPokemon.size == 3) { "A recorded own team must contain exactly 3 Pokémon" }
        require(ownPokemon.map { it.ownedPokemonId }.distinct().size == 3)
        require(ownPokemon.map { it.slot }.toSet() == BattleSlot.entries.toSet())
        require(opponentPokemon.size <= 3)
        require(opponentPokemon.map { it.slot }.distinct().size == opponentPokemon.size)
        require(ratingBefore == null || ratingBefore >= 0)
        require(ratingAfter == null || ratingAfter >= 0)
    }
}
