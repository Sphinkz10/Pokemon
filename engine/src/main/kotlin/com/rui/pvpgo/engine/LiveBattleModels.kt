package com.rui.pvpgo.engine

import com.rui.pvpgo.domain.BattleOutcome

enum class LiveBattlePhase { IDLE, ACTIVE, COMPLETE }
enum class ObservationCertainty(val weight: Double) {
    CONFIRMED(1.0),
    LIKELY(0.75),
    UNCERTAIN(0.4)
}

data class EnergyRange(val min: Int, val max: Int) {
    init {
        require(min in 0..100)
        require(max in 0..100)
        require(min <= max)
    }

    fun addConfirmed(amount: Int): EnergyRange = EnergyRange(
        min = (min + amount).coerceAtMost(100),
        max = (max + amount).coerceAtMost(100)
    )

    fun addPossible(amount: Int): EnergyRange = EnergyRange(
        min = min,
        max = (max + amount).coerceAtMost(100)
    )

    fun spend(amount: Int): EnergyRange = EnergyRange(
        min = (min - amount).coerceAtLeast(0),
        max = (max - amount).coerceAtLeast(0)
    )

    fun widenPossibleGain(amount: Int): EnergyRange = addPossible(amount)

    companion object {
        val ZERO = EnergyRange(0, 0)
        val UNKNOWN = EnergyRange(0, 100)
    }
}

data class HpRange(val minPercent: Int, val maxPercent: Int) {
    init {
        require(minPercent in 0..100)
        require(maxPercent in 0..100)
        require(minPercent <= maxPercent)
    }

    companion object { val FULL = HpRange(100, 100) }
}

data class LiveOpponentState(
    val opponentKey: String,
    val speciesId: String,
    val isShadow: Boolean? = null,
    val fastMoveId: String? = null,
    val energy: EnergyRange = EnergyRange.ZERO,
    val hp: HpRange = HpRange.FULL,
    val fastMovesObserved: Int = 0,
    val chargedMoveIdsRevealed: Set<String> = emptySet(),
    val firstSeenTurn: Int,
    val lastSeenTurn: Int,
    val detectionConfidence: Double = 1.0,
    val fainted: Boolean = false
) {
    init {
        require(opponentKey.isNotBlank())
        require(speciesId.isNotBlank())
        require(firstSeenTurn >= 0)
        require(lastSeenTurn >= firstSeenTurn)
        require(detectionConfidence in 0.0..1.0)
        require(fastMovesObserved >= 0)
        require(chargedMoveIdsRevealed.size <= 2)
    }
}

data class LiveBattleState(
    val phase: LiveBattlePhase = LiveBattlePhase.IDLE,
    val league: League? = null,
    val startedAtEpochMs: Long? = null,
    val endedAtEpochMs: Long? = null,
    val currentTurn: Int = 0,
    val ownTeamOwnedPokemonIds: List<String> = emptyList(),
    val ownActiveOwnedPokemonId: String? = null,
    val ownShieldsRemaining: Int = 2,
    val opponentShieldsRemaining: Int = 2,
    val opponentActiveKey: String? = null,
    val opponentRoster: List<LiveOpponentState> = emptyList(),
    val outcome: BattleOutcome? = null,
    val signalConfidence: Double = 1.0,
    val anomalies: List<String> = emptyList()
) {
    init {
        require(currentTurn >= 0)
        require(ownShieldsRemaining in 0..2)
        require(opponentShieldsRemaining in 0..2)
        require(signalConfidence in 0.0..1.0)
        require(ownTeamOwnedPokemonIds.size <= 3)
        require(ownTeamOwnedPokemonIds.distinct().size == ownTeamOwnedPokemonIds.size)
        require(opponentRoster.map { it.opponentKey }.distinct().size == opponentRoster.size)
        require(opponentRoster.size <= 3)
    }

    val activeOpponent: LiveOpponentState?
        get() = opponentActiveKey?.let { key -> opponentRoster.firstOrNull { it.opponentKey == key } }

    val durationSeconds: Double?
        get() = if (startedAtEpochMs != null && endedAtEpochMs != null) {
            (endedAtEpochMs - startedAtEpochMs) / 1000.0
        } else null
}

sealed interface LiveBattleEvent {
    val observedAtTurn: Int
    val certainty: ObservationCertainty

    data class BattleStarted(
        val league: League,
        val ownTeamOwnedPokemonIds: List<String>,
        val startedAtEpochMs: Long,
        override val observedAtTurn: Int = 0,
        override val certainty: ObservationCertainty = ObservationCertainty.CONFIRMED
    ) : LiveBattleEvent

    data class OwnPokemonActivated(
        val ownedPokemonId: String,
        override val observedAtTurn: Int,
        override val certainty: ObservationCertainty = ObservationCertainty.CONFIRMED
    ) : LiveBattleEvent

    data class OpponentAppeared(
        val opponentKey: String,
        val speciesId: String,
        val isShadow: Boolean? = null,
        override val observedAtTurn: Int,
        override val certainty: ObservationCertainty
    ) : LiveBattleEvent

    data class OpponentFastMoveObserved(
        val moveId: String,
        val count: Int = 1,
        override val observedAtTurn: Int,
        override val certainty: ObservationCertainty
    ) : LiveBattleEvent {
        init { require(count >= 1) }
    }

    data class OpponentChargedMoveObserved(
        val moveId: String?,
        override val observedAtTurn: Int,
        override val certainty: ObservationCertainty
    ) : LiveBattleEvent

    data class OpponentShieldUsed(
        override val observedAtTurn: Int,
        override val certainty: ObservationCertainty = ObservationCertainty.CONFIRMED
    ) : LiveBattleEvent

    data class OwnShieldUsed(
        override val observedAtTurn: Int,
        override val certainty: ObservationCertainty = ObservationCertainty.CONFIRMED
    ) : LiveBattleEvent

    data class OpponentHpObserved(
        val percent: Int,
        override val observedAtTurn: Int,
        override val certainty: ObservationCertainty
    ) : LiveBattleEvent {
        init { require(percent in 0..100) }
    }

    data class OpponentFainted(
        override val observedAtTurn: Int,
        override val certainty: ObservationCertainty = ObservationCertainty.CONFIRMED
    ) : LiveBattleEvent

    data class ObservationGap(
        val gapTurns: Int,
        override val observedAtTurn: Int,
        override val certainty: ObservationCertainty = ObservationCertainty.UNCERTAIN
    ) : LiveBattleEvent {
        init { require(gapTurns >= 1) }
    }

    data class BattleEnded(
        val outcome: BattleOutcome,
        val endedAtEpochMs: Long,
        override val observedAtTurn: Int,
        override val certainty: ObservationCertainty = ObservationCertainty.CONFIRMED
    ) : LiveBattleEvent
}

fun interface LiveMoveCatalog {
    fun get(moveId: String): PvpMove?
}
