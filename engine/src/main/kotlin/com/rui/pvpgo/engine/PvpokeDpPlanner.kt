package com.rui.pvpgo.engine

import java.util.PriorityQueue
import kotlin.math.ceil

/**
 * Source-oriented port of the core dynamic-programming search in the pinned PvPoke ActionLogic.
 *
 * Scope (V1.8): lines ~400-735 of the pinned ActionLogic. The planner searches for the fastest
 * guaranteed path to reduce the opponent to 0 HP using Fast damage, Charged damage, energy,
 * shields and guaranteed attack/defense-stage effects. PvPoke disables non-guaranteed TTK buff
 * branches in this path, so chance effects are intentionally not branched here.
 *
 * The post-plan heuristics that reorder/defer/bait the chosen moves remain in BattleEngine and are
 * still parity-gated separately.
 */
object PvpokeDpPlanner {
    const val MAX_STATES = 500

    data class State(
        val energy: Int,
        val opponentHp: Int,
        val turn: Int,
        val opponentShields: Int,
        val moves: List<PvpMove>,
        val attackBuffDelta: Int,
        val chance: Double = 1.0
    )

    private data class QueueEntry(val state: State, val order: Long)

    data class Plan(
        val koTurn: Int,
        val moves: List<PvpMove>,
        val finalEnergy: Int,
        val attackBuffDelta: Int,
        val opponentShields: Int,
        val statesVisited: Int,
        val farmDown: Boolean
    )

    fun plan(
        attacker: BattleBuild,
        defender: BattleBuild,
        attackerAttackStage: Int,
        defenderDefenseStage: Int,
        energy: Int,
        opponentHp: Int,
        opponentShields: Int
    ): Plan? {
        require(energy in 0..100)
        require(opponentHp >= 0)
        require(opponentShields in 0..2)
        if (opponentHp == 0) return Plan(0, emptyList(), energy, 0, opponentShields, 0, true)
        if (attacker.chargedMoves.isEmpty()) return null
        val fastGain = attacker.fastMove.energyGain.coerceAtLeast(1)
        var insertionOrder = 0L
        val queue = PriorityQueue<QueueEntry>(compareBy<QueueEntry> { it.state.turn }.thenBy { it.order })
        queue += QueueEntry(State(energy, opponentHp, 0, opponentShields, emptyList(), 0), insertionOrder++)
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_STATES) {
            val current = queue.poll().state
            visited++
            val buffDelta = current.attackBuffDelta.coerceIn(-4, 4)
            if (current.opponentHp <= 0) {
                return Plan(
                    koTurn = current.turn,
                    moves = current.moves,
                    finalEnergy = current.energy,
                    attackBuffDelta = buffDelta,
                    opponentShields = current.opponentShields,
                    statesVisited = visited,
                    farmDown = current.moves.isEmpty()
                )
            }

            val effectiveAttackStage = (attackerAttackStage + buffDelta).coerceIn(-4, 4)
            val fastDamage = BattleDamage.damage(attacker, effectiveAttackStage, defender, defenderDefenseStage, attacker.fastMove)
                .coerceAtLeast(1)

            // Pinned ActionLogic adds a farm-down terminal state from every search state.
            val fastsToFarm = ceil(current.opponentHp.toDouble() / fastDamage).toInt().coerceAtLeast(1)
            insertionOrder = enqueueDominanceAware(
                queue, insertionOrder,
                State(
                    energy = current.energy + fastGain * fastsToFarm,
                    opponentHp = 0,
                    turn = current.turn + fastsToFarm * attacker.fastMove.turns,
                    opponentShields = current.opponentShields,
                    moves = current.moves,
                    attackBuffDelta = buffDelta,
                    chance = current.chance
                )
            )

            for (move in attacker.chargedMoves) {
                val moveDamage = BattleDamage.damage(attacker, effectiveAttackStage, defender, defenderDefenseStage, move)
                val turnsToReady = if (current.energy >= move.energyCost) 0 else {
                    ceil((move.energyCost - current.energy).toDouble() / fastGain).toInt() * attacker.fastMove.turns
                }
                val fastCount = turnsToReady / attacker.fastMove.turns
                val energyAtThrow = current.energy + fastGain * fastCount
                val newEnergy = energyAtThrow - move.energyCost
                val fastDamageBeforeThrow = fastDamage * fastCount
                var newOpponentHp = current.opponentHp - fastDamageBeforeThrow
                var newShields = current.opponentShields
                if (newOpponentHp > 0) {
                    if (newShields > 0) {
                        newOpponentHp -= 1
                        newShields--
                    } else {
                        newOpponentHp -= moveDamage
                    }
                }
                val attackDeltaAfter = (buffDelta + guaranteedPlannerAttackDelta(move)).coerceIn(-4, 4)
                val next = State(
                    energy = newEnergy,
                    opponentHp = newOpponentHp,
                    turn = current.turn + turnsToReady + 1,
                    opponentShields = newShields,
                    moves = current.moves + move,
                    attackBuffDelta = attackDeltaAfter,
                    chance = current.chance
                )
                insertionOrder = enqueueDominanceAware(queue, insertionOrder, next)

                // Pinned special path: self-Attack-debuffing moves may be stacked to two charges
                // before the first throw to minimize time spent debuffed.
                if (move.attackerAttackStageDelta < 0 && move.energyCost * 2 <= 100) {
                    val stackTurns = ceil((move.energyCost * 2 - current.energy).coerceAtLeast(0).toDouble() / fastGain)
                        .toInt() * attacker.fastMove.turns
                    if (stackTurns > 0) {
                        val stackFastCount = stackTurns / attacker.fastMove.turns
                        val stackedEnergy = current.energy + stackFastCount * fastGain - move.energyCost
                        var stackedHp = current.opponentHp - fastDamage * stackFastCount
                        var stackedShields = current.opponentShields
                        if (stackedHp > 0) {
                            if (stackedShields > 0) {
                                stackedHp -= 1
                                stackedShields--
                            } else {
                                stackedHp -= moveDamage
                            }
                        }
                        insertionOrder = enqueueDominanceAware(
                            queue, insertionOrder,
                            State(
                                energy = stackedEnergy,
                                opponentHp = stackedHp,
                                turn = current.turn + stackTurns + 1,
                                opponentShields = stackedShields,
                                moves = current.moves + move,
                                attackBuffDelta = attackDeltaAfter,
                                chance = current.chance
                            )
                        )
                    }
                }
            }
        }
        return null
    }

    /**
     * PvPoke tracks one scalar "buffs" value in this DP. Self Attack changes and opponent Defense
     * changes both alter effective outgoing damage and are folded into that scalar.
     */
    private fun guaranteedPlannerAttackDelta(move: PvpMove): Int {
        if (move.buffApplyChance != 1.0) return 0
        return move.attackerAttackStageDelta - move.opponentDefenseStageDelta
    }

    /**
     * Lightweight dominance pruning modeled after the pinned queue checks: for the same turn,
     * discard a state when an existing state has at least as much energy/buff advantage, no more
     * opponent HP and no more opponent shields. This keeps the search below the upstream 500-state
     * safety cap without hard-coding species-specific behavior.
     */
    private fun enqueueDominanceAware(queue: PriorityQueue<QueueEntry>, order: Long, candidate: State): Long {
        val entries = queue.toList()
        val dominated = entries.any { entry ->
            val existing = entry.state
            existing.turn == candidate.turn &&
                existing.opponentHp <= candidate.opponentHp &&
                existing.energy >= candidate.energy &&
                existing.attackBuffDelta >= candidate.attackBuffDelta &&
                existing.opponentShields <= candidate.opponentShields
        }
        if (dominated) return order

        val worse = entries.filter { entry ->
            val existing = entry.state
            existing.turn == candidate.turn &&
                candidate.opponentHp <= existing.opponentHp &&
                candidate.energy >= existing.energy &&
                candidate.attackBuffDelta >= existing.attackBuffDelta &&
                candidate.opponentShields <= existing.opponentShields
        }
        worse.forEach(queue::remove)
        queue += QueueEntry(candidate, order)
        return order + 1
    }
}
