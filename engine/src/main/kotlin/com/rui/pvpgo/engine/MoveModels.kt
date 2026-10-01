package com.rui.pvpgo.engine

enum class MoveCategory { FAST, CHARGED }
enum class BuffTarget { SELF, OPPONENT }

data class PvpMove(
    val moveId: String,
    val name: String,
    val abbreviation: String? = null,
    val type: String,
    val power: Int,
    val energyCost: Int = 0,
    val energyGain: Int = 0,
    val turns: Int = 1,
    val cooldownMs: Int = 500,
    val attackerAttackStageDelta: Int = 0,
    val attackerDefenseStageDelta: Int = 0,
    val opponentAttackStageDelta: Int = 0,
    val opponentDefenseStageDelta: Int = 0,
    val buffApplyChance: Double = 0.0,
    val archetype: String? = null,
    val isMegaMove: Boolean = false,
    /** Upstream mechanic tags such as "instant". Empty for ordinary moves. */
    val tags: Set<String> = emptySet()
) {
    init {
        require(moveId.isNotBlank())
        require(name.isNotBlank())
        require(type.isNotBlank())
        require(power >= 0)
        require(energyCost >= 0)
        require(energyGain >= 0)
        require(turns >= 1)
        require(cooldownMs >= 0)
        require(buffApplyChance in 0.0..1.0)
        require(attackerAttackStageDelta in -4..4)
        require(attackerDefenseStageDelta in -4..4)
        require(opponentAttackStageDelta in -4..4)
        require(opponentDefenseStageDelta in -4..4)
    }

    val category: MoveCategory get() = if (energyGain > 0) MoveCategory.FAST else MoveCategory.CHARGED
    val durationSeconds: Double get() = turns * 0.5
    val dpt: Double? get() = if (category == MoveCategory.FAST) power.toDouble() / turns else null
    val ept: Double? get() = if (category == MoveCategory.FAST) energyGain.toDouble() / turns else null
    val dpe: Double? get() = if (category == MoveCategory.CHARGED && energyCost > 0) power.toDouble() / energyCost else null
    val hasStatEffect: Boolean get() = attackerAttackStageDelta != 0 || attackerDefenseStageDelta != 0 ||
        opponentAttackStageDelta != 0 || opponentDefenseStageDelta != 0
}

data class RecommendedMoveset(
    val speciesId: String,
    val league: League,
    val fastMoveId: String,
    val chargedMoveIds: List<String>,
    val source: String,
    val metaScore: Double? = null,
    val sourceUpdatedAtEpochMs: Long? = null
) {
    init {
        require(speciesId.isNotBlank())
        require(fastMoveId.isNotBlank())
        require(chargedMoveIds.size in 1..2)
    }
}

interface MovesetRecommendationProvider {
    fun recommended(speciesId: String, league: League): RecommendedMoveset?
}
