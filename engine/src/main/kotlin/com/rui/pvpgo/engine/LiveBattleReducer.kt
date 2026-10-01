package com.rui.pvpgo.engine

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class LiveBattleReducer(private val moveCatalog: LiveMoveCatalog) {

    fun reduce(state: LiveBattleState, event: LiveBattleEvent): LiveBattleState {
        require(event.observedAtTurn >= state.currentTurn || state.phase == LiveBattlePhase.IDLE) {
            "Live events must not move backwards in time"
        }

        val base = state.copy(
            currentTurn = max(state.currentTurn, event.observedAtTurn),
            signalConfidence = blendConfidence(state.signalConfidence, event.certainty.weight)
        )

        return when (event) {
            is LiveBattleEvent.BattleStarted -> onStarted(event)
            is LiveBattleEvent.OwnPokemonActivated -> base.copy(ownActiveOwnedPokemonId = event.ownedPokemonId)
            is LiveBattleEvent.OpponentAppeared -> onOpponentAppeared(base, event)
            is LiveBattleEvent.OpponentFastMoveObserved -> onFastMove(base, event)
            is LiveBattleEvent.OpponentChargedMoveObserved -> onChargedMove(base, event)
            is LiveBattleEvent.OpponentShieldUsed -> base.copy(
                opponentShieldsRemaining = (base.opponentShieldsRemaining - 1).coerceAtLeast(0)
            )
            is LiveBattleEvent.OwnShieldUsed -> base.copy(
                ownShieldsRemaining = (base.ownShieldsRemaining - 1).coerceAtLeast(0)
            )
            is LiveBattleEvent.OpponentHpObserved -> onHpObserved(base, event)
            is LiveBattleEvent.OpponentFainted -> updateActive(base) { it.copy(fainted = true, hp = HpRange(0, 0)) }
            is LiveBattleEvent.ObservationGap -> onGap(base, event)
            is LiveBattleEvent.BattleEnded -> base.copy(
                phase = LiveBattlePhase.COMPLETE,
                outcome = event.outcome,
                endedAtEpochMs = event.endedAtEpochMs
            )
        }
    }

    private fun onStarted(event: LiveBattleEvent.BattleStarted): LiveBattleState {
        require(event.ownTeamOwnedPokemonIds.size == 3) { "A live battle requires exactly 3 own Pokémon" }
        require(event.ownTeamOwnedPokemonIds.distinct().size == 3)
        return LiveBattleState(
            phase = LiveBattlePhase.ACTIVE,
            league = event.league,
            startedAtEpochMs = event.startedAtEpochMs,
            currentTurn = event.observedAtTurn,
            ownTeamOwnedPokemonIds = event.ownTeamOwnedPokemonIds,
            ownActiveOwnedPokemonId = event.ownTeamOwnedPokemonIds.first(),
            signalConfidence = event.certainty.weight
        )
    }

    private fun onOpponentAppeared(
        state: LiveBattleState,
        event: LiveBattleEvent.OpponentAppeared
    ): LiveBattleState {
        val existing = state.opponentRoster.indexOfFirst { it.opponentKey == event.opponentKey }
        if (existing >= 0) {
            val roster = state.opponentRoster.toMutableList()
            val old = roster[existing]
            roster[existing] = old.copy(
                speciesId = event.speciesId,
                isShadow = event.isShadow ?: old.isShadow,
                lastSeenTurn = event.observedAtTurn,
                detectionConfidence = max(old.detectionConfidence, event.certainty.weight)
            )
            return state.copy(opponentActiveKey = event.opponentKey, opponentRoster = roster)
        }

        if (state.opponentRoster.size >= 3) {
            return state.withAnomaly("More than 3 opponent identities detected; ignored ${event.opponentKey}")
        }

        val opponent = LiveOpponentState(
            opponentKey = event.opponentKey,
            speciesId = event.speciesId,
            isShadow = event.isShadow,
            firstSeenTurn = event.observedAtTurn,
            lastSeenTurn = event.observedAtTurn,
            detectionConfidence = event.certainty.weight
        )
        return state.copy(
            opponentActiveKey = event.opponentKey,
            opponentRoster = state.opponentRoster + opponent
        )
    }

    private fun onFastMove(
        state: LiveBattleState,
        event: LiveBattleEvent.OpponentFastMoveObserved
    ): LiveBattleState {
        val move = moveCatalog.get(event.moveId)
            ?: return state.withAnomaly("Unknown fast move ${event.moveId}")
        if (move.category != MoveCategory.FAST) {
            return state.withAnomaly("${event.moveId} was observed as fast but is not a fast move")
        }

        return updateActive(state) { active ->
            val totalGain = move.energyGain * event.count
            val energy = when (event.certainty) {
                ObservationCertainty.CONFIRMED -> active.energy.addConfirmed(totalGain)
                ObservationCertainty.LIKELY, ObservationCertainty.UNCERTAIN -> active.energy.addPossible(totalGain)
            }
            active.copy(
                fastMoveId = event.moveId,
                energy = energy,
                fastMovesObserved = active.fastMovesObserved + event.count,
                lastSeenTurn = event.observedAtTurn
            )
        }
    }

    private fun onChargedMove(
        state: LiveBattleState,
        event: LiveBattleEvent.OpponentChargedMoveObserved
    ): LiveBattleState {
        val move = event.moveId?.let(moveCatalog::get)
        var next = state
        if (event.moveId != null && move == null) {
            next = next.withAnomaly("Unknown charged move ${event.moveId}")
        }
        if (move != null && move.category != MoveCategory.CHARGED) {
            return next.withAnomaly("${event.moveId} was observed as charged but is not a charged move")
        }

        val activeBefore = state.activeOpponent
        return updateActive(next) { active ->
            val energy = when {
                move == null -> EnergyRange(0, active.energy.max)
                active.energy.max < move.energyCost -> EnergyRange.ZERO
                else -> active.energy.spend(move.energyCost)
            }
            active.copy(
                energy = energy,
                chargedMoveIdsRevealed = if (event.moveId == null) active.chargedMoveIdsRevealed
                else (active.chargedMoveIdsRevealed + event.moveId).take(2).toSet(),
                lastSeenTurn = event.observedAtTurn
            )
        }.let { after ->
            if (move != null && activeBefore != null && activeBefore.energy.max < move.energyCost) {
                after.withAnomaly("Observed ${move.name} before tracked energy reached its cost; likely missed fast move(s)")
            } else after
        }
    }

    private fun onGap(state: LiveBattleState, event: LiveBattleEvent.ObservationGap): LiveBattleState {
        val active = state.activeOpponent ?: return state.withAnomaly("Observation gap without active opponent")
        val fastMoveId = active.fastMoveId ?: return state.withAnomaly("Observation gap before fast move was identified")
        val move = moveCatalog.get(fastMoveId) ?: return state.withAnomaly("Unknown active fast move $fastMoveId")
        if (move.category != MoveCategory.FAST) return state

        val possibleMoves = floor(event.gapTurns.toDouble() / move.turns).toInt().coerceAtLeast(0)
        val possibleGain = possibleMoves * move.energyGain
        return updateActive(state) {
            it.copy(
                energy = it.energy.widenPossibleGain(possibleGain),
                lastSeenTurn = event.observedAtTurn
            )
        }.copy(signalConfidence = min(state.signalConfidence, 0.65))
    }

    private fun onHpObserved(state: LiveBattleState, event: LiveBattleEvent.OpponentHpObserved): LiveBattleState {
        val margin = when (event.certainty) {
            ObservationCertainty.CONFIRMED -> 2
            ObservationCertainty.LIKELY -> 5
            ObservationCertainty.UNCERTAIN -> 10
        }
        val hp = HpRange(
            minPercent = (event.percent - margin).coerceAtLeast(0),
            maxPercent = (event.percent + margin).coerceAtMost(100)
        )
        return updateActive(state) { it.copy(hp = hp, lastSeenTurn = event.observedAtTurn) }
    }

    private fun updateActive(
        state: LiveBattleState,
        transform: (LiveOpponentState) -> LiveOpponentState
    ): LiveBattleState {
        val key = state.opponentActiveKey ?: return state.withAnomaly("Live event received without active opponent")
        val index = state.opponentRoster.indexOfFirst { it.opponentKey == key }
        if (index < 0) return state.withAnomaly("Active opponent key $key is missing from roster")
        val roster = state.opponentRoster.toMutableList()
        roster[index] = transform(roster[index])
        return state.copy(opponentRoster = roster)
    }

    private fun LiveBattleState.withAnomaly(message: String): LiveBattleState = copy(
        anomalies = (anomalies + message).takeLast(20),
        signalConfidence = min(signalConfidence, 0.7)
    )

    private fun blendConfidence(existing: Double, incoming: Double): Double =
        (existing * 0.8 + incoming * 0.2).coerceIn(0.0, 1.0)
}
