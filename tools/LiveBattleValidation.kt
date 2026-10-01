import com.rui.pvpgo.domain.BattleOutcome
import com.rui.pvpgo.engine.*

fun main() {
    val moves = mapOf(
        "BUBBLE" to PvpMove(
            moveId = "BUBBLE", name = "Bubble", type = "Water", power = 8,
            energyGain = 11, turns = 3, cooldownMs = 1500
        ),
        "ICE_BEAM" to PvpMove(
            moveId = "ICE_BEAM", name = "Ice Beam", type = "Ice", power = 90,
            energyCost = 55
        )
    )
    val reducer = LiveBattleReducer(LiveMoveCatalog { moves[it] })
    var state = LiveBattleState()

    state = reducer.reduce(state, LiveBattleEvent.BattleStarted(
        league = League.GREAT,
        ownTeamOwnedPokemonIds = listOf("mine-1", "mine-2", "mine-3"),
        startedAtEpochMs = 1_000L
    ))
    state = reducer.reduce(state, LiveBattleEvent.OpponentAppeared(
        opponentKey = "opp-1", speciesId = "azumarill", observedAtTurn = 0,
        certainty = ObservationCertainty.CONFIRMED
    ))
    repeat(5) { i ->
        state = reducer.reduce(state, LiveBattleEvent.OpponentFastMoveObserved(
            moveId = "BUBBLE", observedAtTurn = (i + 1) * 3,
            certainty = ObservationCertainty.CONFIRMED
        ))
    }
    check(state.activeOpponent?.energy == EnergyRange(55, 55))
    check(state.activeOpponent?.fastMovesObserved == 5)

    state = reducer.reduce(state, LiveBattleEvent.OpponentChargedMoveObserved(
        moveId = "ICE_BEAM", observedAtTurn = 16,
        certainty = ObservationCertainty.CONFIRMED
    ))
    check(state.activeOpponent?.energy == EnergyRange.ZERO)
    check("ICE_BEAM" in (state.activeOpponent?.chargedMoveIdsRevealed ?: emptySet()))

    state = reducer.reduce(state, LiveBattleEvent.ObservationGap(
        gapTurns = 6, observedAtTurn = 22
    ))
    check(state.activeOpponent?.energy == EnergyRange(0, 22))
    check(state.signalConfidence <= 0.65)

    state = reducer.reduce(state, LiveBattleEvent.OpponentAppeared(
        opponentKey = "opp-2", speciesId = "gastrodon", observedAtTurn = 23,
        certainty = ObservationCertainty.LIKELY
    ))
    check(state.opponentRoster.size == 2)
    check(state.opponentActiveKey == "opp-2")

    state = reducer.reduce(state, LiveBattleEvent.BattleEnded(
        outcome = BattleOutcome.WIN,
        endedAtEpochMs = 181_000L,
        observedAtTurn = 360
    ))
    check(state.phase == LiveBattlePhase.COMPLETE)
    check(state.durationSeconds == 180.0)

    val record = LiveBattleLogProjector.toBattleRecordOrNull(state, "battle-live-1")
        ?: error("Expected projected BattleRecord")
    check(record.outcome == BattleOutcome.WIN)
    check(record.opponentPokemon.first().speciesId == "azumarill")
    check(record.opponentPokemon.first().fastMoveId == "BUBBLE")
    check(record.opponentPokemon.first().chargedMoveIds == listOf("ICE_BEAM"))
    check(record.opponentPokemon.size == 2)

    println("Live Battle validation passed")
    println("Final confidence: ${"%.3f".format(state.signalConfidence)} (${LiveBattleLogProjector.confidenceLabel(state)})")
    println("Opponent 1 energy after gap: ${state.opponentRoster.first().energy}")
    println("Projected opponents: ${record.opponentPokemon.map { it.speciesId }}")
}
