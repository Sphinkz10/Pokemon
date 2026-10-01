package com.rui.pvpgo.engine

import com.rui.pvpgo.domain.BattleOutcome

enum class ChargeStrategy {
    /** Never intentionally bait; use the affordable move that deals the most damage. */
    MAX_DAMAGE,
    /** Prefer the best damage-per-energy among affordable moves. */
    BEST_DPE,
    /** Use the cheapest Charged Move while the opponent still has shields. */
    BAIT_IF_SHIELDED,
    /** Always use the cheapest affordable Charged Move. */
    CHEAPEST,
    /**
     * PvPoke-aligned baseline decision policy. V1.9 combines the pinned pre-DP branches,
     * core dynamic-programming KO planner, and standard-form post-plan bait/reorder/defer rules.
     * Exact queued-action timing, special-form branches and same-revision external parity remain trust-gated.
     */
    PVPOKE_BASELINE
}

enum class ShieldStrategy {
    ALWAYS,
    NEVER,
    LETHAL_ONLY,
    /** Shield lethal hits or unshielded hits worth at least [BattleScenario.highDamageShieldThreshold]. */
    CONSERVE,
    /**
     * Mirrors the pinned PvPoke Battle.js default shielding path for standard 1v1 simulate mode.
     * Ordinary Charged Moves are shielded while shields remain; ActionLogic.wouldShield() is only
     * consulted for specific delegated stat-effect/special-form cases.
     */
    PVPOKE_DEFAULT
}

enum class StatEffectPolicy {
    /** Mirrors a conservative deterministic sim: guaranteed effects apply; chance effects do not. */
    GUARANTEED_ONLY,
    /** Mirrors PvPoke simulate-mode chance effects via a cumulative per-move apply meter. */
    PVPOKE_DETERMINISTIC_METER,
    /** Deterministic pseudo-random effects from [BattleScenario.randomSeed]. */
    SEEDED_RANDOM,
    FORCE_APPLY,
    FORCE_NONE
}

data class BattleBuild(
    val key: String,
    val species: PokemonSpecies,
    val iv: IvSpread,
    val level: Double,
    val fastMove: PvpMove,
    val chargedMoves: List<PvpMove>,
    val isShadow: Boolean = species.isShadow,
    val initialEnergy: Int = 0,
    val initialAttackStage: Int = 0,
    val initialDefenseStage: Int = 0
) {
    init {
        require(key.isNotBlank())
        require(level in 1.0..51.0 && level * 2 == (level * 2).toInt().toDouble())
        require(fastMove.category == MoveCategory.FAST)
        require(chargedMoves.size in 1..2)
        require(chargedMoves.all { it.category == MoveCategory.CHARGED })
        require(initialEnergy in 0..100)
        require(initialAttackStage in -4..4)
        require(initialDefenseStage in -4..4)
    }

    val stats: BattleStats get() = PokemonMath.battleStats(species, iv, level)
}

data class BattleSidePolicy(
    val chargeStrategy: ChargeStrategy = ChargeStrategy.MAX_DAMAGE,
    val shieldStrategy: ShieldStrategy = ShieldStrategy.ALWAYS,
    /**
     * Mirrors PvPoke's per-Pokémon `baitShields` switch for PVPOKE_BASELINE. Generic
     * strategies keep their own explicit behavior and ignore this flag.
     */
    val baitShields: Boolean = true
)

data class BattleScenario(
    val shieldsA: Int = 1,
    val shieldsB: Int = 1,
    val policyA: BattleSidePolicy = BattleSidePolicy(),
    val policyB: BattleSidePolicy = BattleSidePolicy(),
    val statEffectPolicy: StatEffectPolicy = StatEffectPolicy.GUARANTEED_ONLY,
    val randomSeed: Long = 1L,
    val highDamageShieldThreshold: Double = 0.35,
    val maxTurns: Int = 480
) {
    init {
        require(shieldsA in 0..2 && shieldsB in 0..2)
        require(highDamageShieldThreshold in 0.0..1.0)
        require(maxTurns in 1..2000)
    }
}

data class BattleStateSnapshot(
    val key: String,
    val hp: Int,
    val maxHp: Int,
    val energy: Int,
    val shields: Int,
    val attackStage: Int,
    val defenseStage: Int,
    val fastMovesUsed: Int,
    val chargedMovesUsed: Int,
    /** Active simulator form when a supported bespoke mechanic changes form. */
    val activeFormId: String? = null,
    /** True only while Mimikyu's Disguise protection is still available. */
    val disguiseActive: Boolean = false
) {
    val hpPercent: Double get() = if (maxHp == 0) 0.0 else hp.toDouble() / maxHp
}

enum class BattleDecisionType {
    TURN_STATE,
    FAST_SELECTED,
    FAST_STARTED,
    FAST_RESOLVED,
    FAST_DROPPED,
    CHARGED_SELECTED,
    CHARGED_RESOLVED,
    SHIELD_DECISION,
    CMP,
    POST_CHARGED_LOCK
}

/**
 * Deterministic, machine-readable action trace used for parity debugging.
 *
 * The product-facing timeline stays compact; this trace captures the state that led to each
 * decision so two simulators can be compared turn-by-turn without inferring behavior from the
 * final Battle Rating.
 */
data class BattleDecisionTrace(
    val turn: Int,
    val actorKey: String?,
    val type: BattleDecisionType,
    val actorHp: Int? = null,
    val opponentHp: Int? = null,
    val actorEnergy: Int? = null,
    val opponentEnergy: Int? = null,
    val actorShields: Int? = null,
    val opponentShields: Int? = null,
    val actorCooldown: Int? = null,
    val opponentCooldown: Int? = null,
    val moveId: String? = null,
    val reason: String? = null
)

enum class BattleEventType {
    FAST_MOVE,
    CHARGED_MOVE,
    SHIELD,
    STAT_CHANGE,
    FAINT,
    CMP,
    INFO,
    /** Explicit event for supported bespoke mechanics (form/protect/etc.). */
    SPECIAL_MECHANIC
}

data class BattleTimelineEvent(
    val turn: Int,
    val actorKey: String?,
    val type: BattleEventType,
    val moveId: String? = null,
    val damage: Int? = null,
    val energyAfter: Int? = null,
    val shielded: Boolean = false,
    val note: String? = null
)

data class BattleSimulationResult(
    val outcomeForA: BattleOutcome,
    val turns: Int,
    val battleRatingA: Int,
    val battleRatingB: Int,
    val finalA: BattleStateSnapshot,
    val finalB: BattleStateSnapshot,
    val timeline: List<BattleTimelineEvent>,
    val decisionTrace: List<BattleDecisionTrace> = emptyList(),
    val assumptions: List<String>
) {
    init {
        require(battleRatingA in 0..1000)
        require(battleRatingB in 0..1000)
    }
}

data class KeyMatchupResult(
    val opponentKey: String,
    val opponentSpeciesId: String,
    val outcome: BattleOutcome,
    val battleRating: Int,
    val turns: Int,
    val subjectHpPercent: Double,
    val opponentHpPercent: Double
)
