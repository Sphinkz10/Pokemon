package com.rui.pvpgo.engine

data class LiveBattleEvent(val label: String = "observed")
data class LiveBattleState(val acceptedEvents: Int = 0)
class LiveBattleReducer {
    fun reduce(state: LiveBattleState, event: LiveBattleEvent): LiveBattleState =
        state.copy(acceptedEvents = state.acceptedEvents + 1)
}
