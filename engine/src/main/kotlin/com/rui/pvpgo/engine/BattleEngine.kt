package com.rui.pvpgo.engine

import com.rui.pvpgo.domain.BattleOutcome
import java.util.Random
import java.util.ArrayDeque
import kotlin.math.floor

class BattleEngine {
    private data class PendingFast(
        val actor: Int,
        val move: PvpMove,
        val startTurn: Int,
        val resolveTurn: Int
    )

    private data class MutableState(
        var build: BattleBuild,
        var hp: Int,
        val maxHp: Int,
        var energy: Int,
        var shields: Int,
        var attackStage: Int,
        var defenseStage: Int,
        var cooldownTurns: Int = 0,
        var fastMovesUsed: Int = 0,
        var chargedMovesUsed: Int = 0,
        var faintedByCharged: Boolean = false,
        var activeFormId: String = build.species.speciesId,
        var mimikyuDisguiseActive: Boolean = build.species.speciesId == "mimikyu",
        val buffApplyMeters: MutableMap<String, Double> = mutableMapOf()
    )

    private data class ChargeDecision(val move: PvpMove, val reason: String)

    private sealed interface Action {
        val actor: Int
        data class Fast(override val actor: Int, val move: PvpMove) : Action
        data class Charged(override val actor: Int, val move: PvpMove, val reason: String) : Action
    }

    fun simulate(a: BattleBuild, b: BattleBuild, scenario: BattleScenario = BattleScenario()): BattleSimulationResult {
        val states = arrayOf(
            MutableState(
                a, a.stats.hp, a.stats.hp, a.initialEnergy, scenario.shieldsA,
                a.initialAttackStage, nativeDefenseStage(a)
            ),
            MutableState(
                b, b.stats.hp, b.stats.hp, b.initialEnergy, scenario.shieldsB,
                b.initialAttackStage, nativeDefenseStage(b)
            )
        )
        // Normalize form-specific Fast Moves at battle entry. Aegislash Shield uses the
        // special 0-power/6-energy variants; Blade uses the ordinary Fast Move values.
        // Keeping this on MutableState means a later stance change can update future Fast Moves
        // without mutating any already-queued Fast Move.
        for (s in states) s.build = normalizeAegislashFastMove(s.build, s.activeFormId)

        val policies = arrayOf(scenario.policyA, scenario.policyB)
        val pending = mutableListOf<PendingFast>()
        val events = mutableListOf<BattleTimelineEvent>()
        val decisionTrace = mutableListOf<BattleDecisionTrace>()
        val rng = Random(scenario.randomSeed)
        var turn = 0
        var previousTurnHadCharged = false

        while (turn < scenario.maxTurns && states[0].hp > 0 && states[1].hp > 0) {
            turn++
            for (s in states) s.cooldownTurns = (s.cooldownTurns - 1).coerceAtLeast(0)
            decisionTrace += trace(turn, BattleDecisionType.TURN_STATE, states[0], states[1], reason = "Start of turn")
            decisionTrace += trace(turn, BattleDecisionType.TURN_STATE, states[1], states[0], reason = "Start of turn")

            val actions = mutableListOf<Action>()
            if (!previousTurnHadCharged) {
                for (i in 0..1) {
                    val s = states[i]
                    if (s.hp <= 0 || s.cooldownTurns != 0) continue
                    val opponent = states[1 - i]
                    val queuedFastMoves = pending.count { it.actor == i }
                    val charged = chooseCharged(s, opponent, policies[i], queuedFastMoves)
                    if (charged != null) {
                        actions += Action.Charged(i, charged.move, charged.reason)
                        decisionTrace += trace(turn, BattleDecisionType.CHARGED_SELECTED, s, opponent, charged.move.moveId, charged.reason)
                    } else {
                        actions += Action.Fast(i, s.build.fastMove)
                        decisionTrace += trace(
                            turn, BattleDecisionType.FAST_SELECTED, s, opponent, s.build.fastMove.moveId,
                            "No Charged Move selected by ${policies[i].chargeStrategy}; continue Fast Move cycle."
                        )
                    }
                }
            }

            // Start Fast Moves now; they resolve on their damage turn. Charged Moves resolve immediately.
            actions.filterIsInstance<Action.Fast>().forEach { action ->
                val s = states[action.actor]
                s.cooldownTurns += action.move.turns
                pending += PendingFast(action.actor, action.move, turn, turn + action.move.turns - 1)
                decisionTrace += trace(
                    turn, BattleDecisionType.FAST_STARTED, s, states[1 - action.actor], action.move.moveId,
                    "Started ${action.move.turns}-turn Fast Move; scheduled resolution T${turn + action.move.turns - 1}."
                )
            }

            val chargedActions = actions.filterIsInstance<Action.Charged>().sortedWith { x, y ->
                val ax = cmpAttack(states[x.actor])
                val ay = cmpAttack(states[y.actor])
                when {
                    ax > ay -> -1
                    ay > ax -> 1
                    else -> x.actor.compareTo(y.actor) // deterministic tie; timeline marks true CMP tie.
                }
            }

            if (chargedActions.size == 2) {
                val atkA = cmpAttack(states[chargedActions[0].actor])
                val atkB = cmpAttack(states[chargedActions[1].actor])
                val tied = atkA == atkB
                events += BattleTimelineEvent(
                    turn, null, BattleEventType.CMP,
                    note = if (tied) "CMP Attack tie; deterministic side-order used for reproducible simulation." else "Higher unmodified Attack wins CMP."
                )
                decisionTrace += BattleDecisionTrace(
                    turn = turn, actorKey = null, type = BattleDecisionType.CMP,
                    reason = if (tied) "CMP tie: deterministic side-order." else "CMP winner=${states[chargedActions[0].actor].build.key}; higher unmodified Attack."
                )
            }

            var chargedOccurred = false
            for (action in chargedActions) {
                val attacker = states[action.actor]
                val defender = states[1 - action.actor]
                if (attacker.hp <= 0 || defender.hp <= 0 || attacker.energy < action.move.energyCost) continue
                chargedOccurred = true
                executeCharged(
                    turn, attacker, defender, action.move, action.reason,
                    policies[1 - action.actor].shieldStrategy, scenario, rng, events, decisionTrace
                )
            }

            // Fast moves whose damage window lands this turn resolve after Charged priority.
            // Eligible Fast Moves on the same turn are applied simultaneously so list order cannot decide a Fast-Move tie.
            val due = pending.filter {
                it.resolveTurn <= turn || (previousTurnHadCharged && it.startTurn < turn)
            }.toList()
            due.forEach(pending::remove)
            data class DueHit(val pending: PendingFast, val damage: Int)
            val dueHits = due.mapNotNull { p ->
                val attacker = states[p.actor]
                val defender = states[1 - p.actor]
                // A floating Fast Move is lost if its user was KO'd by a Charged Move before its damage window.
                if (attacker.hp <= 0 && attacker.faintedByCharged) {
                    decisionTrace += trace(
                        turn, BattleDecisionType.FAST_DROPPED, attacker, defender, p.move.moveId,
                        "Queued Fast Move invalidated because actor fainted to Charged Move before resolution."
                    )
                    return@mapNotNull null
                }
                if (defender.hp <= 0) {
                    decisionTrace += trace(
                        turn, BattleDecisionType.FAST_DROPPED, attacker, defender, p.move.moveId,
                        "Queued Fast Move has no live defender."
                    )
                    return@mapNotNull null
                }
                DueHit(
                    p,
                    damage(attacker, defender, p.move)
                )
            }
            for (hit in dueHits) {
                val attacker = states[hit.pending.actor]
                attacker.energy = (attacker.energy + hit.pending.move.energyGain).coerceAtMost(100)
                attacker.fastMovesUsed++
            }
            for (hit in dueHits) {
                val attacker = states[hit.pending.actor]
                val defender = states[1 - hit.pending.actor]
                defender.hp = (defender.hp - hit.damage).coerceAtLeast(0)
                events += BattleTimelineEvent(turn, attacker.build.key, BattleEventType.FAST_MOVE, hit.pending.move.moveId, hit.damage, attacker.energy)
                decisionTrace += trace(
                    turn, BattleDecisionType.FAST_RESOLVED, attacker, defender, hit.pending.move.moveId,
                    "Fast Move resolved for ${hit.damage} damage; energy=${attacker.energy}."
                )
            }
            for (i in 0..1) {
                if (states[i].hp == 0 && dueHits.any { 1 - it.pending.actor == i }) {
                    events += BattleTimelineEvent(turn, states[i].build.key, BattleEventType.FAINT, note = "Fainted to Fast Move")
                }
            }

            if (chargedOccurred) {
                // After a Charged sequence, both sides resume with a one-turn cooldown. Existing floating Fast Moves stay queued.
                for (i in 0..1) {
                    states[i].cooldownTurns = 1
                    decisionTrace += trace(
                        turn, BattleDecisionType.POST_CHARGED_LOCK, states[i], states[1 - i],
                        reason = "Post-Charged one-turn lock; queued floating Fast Moves remain eligible."
                    )
                }
            }
            previousTurnHadCharged = chargedOccurred
        }

        val outcome = when {
            states[0].hp > 0 && states[1].hp <= 0 -> BattleOutcome.WIN
            states[1].hp > 0 && states[0].hp <= 0 -> BattleOutcome.LOSS
            states[0].hp <= 0 && states[1].hp <= 0 -> BattleOutcome.DRAW
            else -> {
                val rA = battleRating(states[0], states[1])
                val rB = battleRating(states[1], states[0])
                when {
                    rA > rB -> BattleOutcome.WIN
                    rB > rA -> BattleOutcome.LOSS
                    else -> BattleOutcome.DRAW
                }
            }
        }
        val ratingA = battleRating(states[0], states[1])
        val ratingB = battleRating(states[1], states[0])

        return BattleSimulationResult(
            outcomeForA = outcome,
            turns = turn,
            battleRatingA = ratingA,
            battleRatingB = ratingB,
            finalA = snapshot(states[0]),
            finalB = snapshot(states[1]),
            timeline = events.toList(),
            decisionTrace = decisionTrace.toList(),
            assumptions = listOf(
                "0.5 s turns; charged moves resolve with CMP priority.",
                "Charged mini-game assumes full charge.",
                "Chance stat effects follow ${scenario.statEffectPolicy}; PVPOKE_DETERMINISTIC_METER uses a cumulative per-move apply meter.",
                "Shield and bait behavior is explicit policy, not a hidden win-probability model.",
                "PVPOKE_DEFAULT follows the pinned Battle.js default: ordinary Charged Moves are shielded when a shield is available; wouldShield() is only consulted in delegated stat-effect/special cases.",
                "PVPOKE_BASELINE V1.9 includes the pinned pre-DP branches, DP core and common post-plan bait/reorder/defer policy; special-form branches and same-revision external outputs remain parity-gated.",
                "Mimikyu Disguise is modeled for standard 1v1: first unshielded non-instant Charged hit deals 1, changes to Busted, and applies native -1 Defense.",
                "Aegislash stance core is modeled locally: Shield -> Blade before Charged damage and Blade -> Shield after Protect Shield; Shield uses the pinned 0-power/6-energy Fast variants and Blade restores ordinary Fast Move values. Switch reset and exact-pin action-policy parity remain trust-gated.",
                "Morpeko toggle is modeled to game mechanics rather than the known a93147b reference bug: every Charged Move alternates Full Belly/Hangry and Aura Wheel Electric/Dark follows the active form; exact-pin mismatch is tracked as a known upstream divergence.",
                "Cramorant core is modeled locally: Surf/Dive loads Gulping above 50% HP or Gorging at/below 50%; an unshielded non-instant Charged hit triggers a 15%-max-HP Gulp Missile, guaranteed form-specific debuff and reset to normal, including on a KO trade. Pinned weak-hit shield delegation is modeled locally; exact-pin trace parity, switch reset and broader special action-policy remain trust-gated.",
                "Queued Fast Moves can float on the first turn after a Charged sequence, matching the current source-rule timing; live/device behavior is validated separately."
            )
        )
    }

    private fun chooseCharged(
        state: MutableState,
        defender: MutableState,
        policy: BattleSidePolicy,
        queuedFastMoves: Int = 0
    ): ChargeDecision? {
        val strategy = policy.chargeStrategy
        val affordable = activeChargedMoves(state).filter { state.energy >= it.energyCost }
        if (affordable.isEmpty()) return null
        fun dmg(m: PvpMove) = damage(state, defender, m)
        return when (strategy) {
            ChargeStrategy.MAX_DAMAGE -> affordable.maxWithOrNull(compareBy<PvpMove> { dmg(it) }.thenBy { -it.energyCost })
                ?.let { ChargeDecision(it, "MAX_DAMAGE selected highest immediate damage among affordable moves.") }
            ChargeStrategy.BEST_DPE -> affordable.maxWithOrNull(compareBy<PvpMove> { dmg(it).toDouble() / it.energyCost }.thenBy { dmg(it) })
                ?.let { ChargeDecision(it, "BEST_DPE selected highest damage-per-energy among affordable moves.") }
            ChargeStrategy.CHEAPEST -> affordable.minWithOrNull(compareBy<PvpMove> { it.energyCost }.thenByDescending { dmg(it) })
                ?.let { ChargeDecision(it, "CHEAPEST selected lowest-energy affordable move.") }
            ChargeStrategy.BAIT_IF_SHIELDED -> if (defender.shields > 0 && affordable.size > 1) {
                affordable.minWithOrNull(compareBy<PvpMove> { it.energyCost }.thenByDescending { dmg(it) })
                    ?.let { ChargeDecision(it, "BAIT_IF_SHIELDED selected cheapest move because defender still has a shield.") }
            } else {
                affordable.maxWithOrNull(compareBy<PvpMove> { dmg(it) }.thenBy { -it.energyCost })
                    ?.let { ChargeDecision(it, "BAIT_IF_SHIELDED selected max damage because no bait condition remains.") }
            }
            ChargeStrategy.PVPOKE_BASELINE -> choosePvpokeBaselineCharged(state, defender, queuedFastMoves, policy.baitShields)
        }
    }

    /**
     * Reproduces the public PvPoke policy at the level we can currently verify from its
     * documented Battle Algorithm. It is deliberately kept separate from generic strategies
     * so Matchup Advisor can state exactly which policy produced a result.
     */
    private data class SurvivalQueueState(
        val hp: Int,
        val opponentEnergy: Int,
        val turn: Int,
        val shields: Int
    )

    /**
     * V1.9 includes the verified pre-DP branches, core pinned dynamic-programming search and common post-plan policy.
     * Bait-specific post-plan reordering, opponent turnsToKO coupling and special-form branches
     * remain intentionally parity-gated.
     */
    private fun choosePvpokeBaselineCharged(
        state: MutableState,
        defender: MutableState,
        queuedFastMoves: Int,
        baitShields: Boolean
    ): ChargeDecision? {
        fun dmg(m: PvpMove) = damage(state, defender, m)
        val moves = activeChargedMoves(state)
        if (moves.isEmpty()) return null

        val affordable = moves.filter { state.energy >= it.energyCost }
        if (affordable.isEmpty()) return null

        val main = moves.maxWithOrNull(
            compareBy<PvpMove> { dmg(it).toDouble() / it.energyCost.coerceAtLeast(1) }
                .thenBy { dmg(it) }
        ) ?: return null
        val fastest = moves.minWithOrNull(compareBy<PvpMove> { it.energyCost }.thenByDescending { dmg(it) }) ?: main

        val turnsToLive = estimatePvpokeTurnsToLive(state, defender)
        val ownFastDamage = damage(state, defender, state.build.fastMove)
        val oppFastDamage = damage(defender, state, defender.build.fastMove)
        val winsCmp = cmpAttack(state) >= cmpAttack(defender)

        // Pinned ActionLogic lines ~129-190: if another Fast Move is unsafe, throw the highest
        // immediate damage move that is already available. If CMP is won and two copies are
        // banked, upstream values the two-move path when it exceeds the single-move alternative.
        if (turnsToLive != Int.MAX_VALUE) {
            val cannotLiveThroughOwnFast =
                turnsToLive < state.build.fastMove.turns ||
                    (turnsToLive == state.build.fastMove.turns && !winsCmp) ||
                    (turnsToLive == state.build.fastMove.turns && state.hp <= oppFastDamage)
            if (cannotLiveThroughOwnFast) {
                var selected: PvpMove? = null
                var selectedValue = -1
                for (move in affordable) {
                    val moveDamage = dmg(move)
                    var value = moveDamage
                    if (state.energy >= move.energyCost * 2 && winsCmp) {
                        value = maxOf(value, moveDamage * 2)
                    }
                    if (value > selectedValue) {
                        selected = move
                        selectedValue = value
                    }
                }
                if (selected != null) {
                    return ChargeDecision(
                        selected,
                        "PVPOKE_BASELINE/V1.9: survival-urgency branch; $turnsToLive turn(s) to live, throw highest available damage path."
                    )
                }
            }
        }

        // Pinned ActionLogic lines ~192-214: throw a clean lethal Charged Move when shields are
        // gone, avoiding non-lethal self-debuffing lines and avoiding a wasteful Charged when the
        // opponent is already in Fast-Move KO range.
        if (defender.shields == 0) {
            for (move in moves) {
                if (state.energy < move.energyCost) continue
                val moveDamage = dmg(move)
                if (moveDamage >= defender.hp && !isSelfDebuffing(state, move) && defender.hp > ownFastDamage) {
                    return ChargeDecision(
                        move,
                        "PVPOKE_BASELINE/V1.9: immediate lethal Charged Move with shields down."
                    )
                }
            }
        }

        // Pinned optimizeMoveTiming block (~232-332). A true return means upstream would continue
        // the Fast cycle rather than throw now.
        if (state.energy >= main.energyCost && shouldDelayForPvpokeMoveTiming(
                state, defender, main, turnsToLive, queuedFastMoves
            )) {
            return null
        }

        // Pinned simple long-cycle branch (~354-398). If defeating the opponent will require many
        // full cycles, upstream uses the best efficient move rather than entering its full DP search.
        val bestChargedDamage = dmg(main)
        val fastMovesForMain = kotlin.math.ceil(
            main.energyCost.toDouble() / state.build.fastMove.energyGain.coerceAtLeast(1)
        ).toInt()
        val bestCycleDamage = bestChargedDamage + ownFastDamage * fastMovesForMain
        var minimumCycleThreshold = 2.0
        if (isSelfDebuffing(state, main) && main.energyCost > fastest.energyCost) {
            val mainDpe = bestChargedDamage.toDouble() / main.energyCost.coerceAtLeast(1)
            val fastestDpe = dmg(fastest).toDouble() / fastest.energyCost.coerceAtLeast(1)
            if (fastestDpe > 0.0 && mainDpe / fastestDpe < 2.0) minimumCycleThreshold = 1.1
        }
        if (bestCycleDamage > 0 && defender.hp.toDouble() / bestCycleDamage > minimumCycleThreshold) {
            var selected = main
            if (moves.size > 1 && isSelfDebuffing(state, main)) {
                for (candidate in moves) {
                    if (!isSelfDebuffing(state, candidate)) {
                        val selectedDpe = dmg(selected).toDouble() / selected.energyCost.coerceAtLeast(1)
                        val candidateDpe = dmg(candidate).toDouble() / candidate.energyCost.coerceAtLeast(1)
                        if (candidateDpe > 0.0 && selectedDpe / candidateDpe < 2.0) selected = candidate
                    }
                }
            }
            if (state.energy < selected.energyCost) return null
            if (isSelfDebuffing(state, selected)) {
                val energyReachableWithoutOverflow = state.energy +
                    ((100 - state.energy) / state.build.fastMove.energyGain.coerceAtLeast(1)) * state.build.fastMove.energyGain
                if (state.energy < energyReachableWithoutOverflow) return null
            }
            return ChargeDecision(
                selected,
                "PVPOKE_BASELINE/V1.9: long-cycle simple-move branch; full DP planner not required for this state."
            )
        }

        // V1.8: core dynamic-programming search from pinned ActionLogic lines ~400-735.
        // This replaces the old "use Main" fallback with a state search over energy, HP, shields,
        // Fast farming and guaranteed outgoing-damage stage changes. Post-plan bait/reorder/special
        // heuristics remain separately parity-gated.
        val dpPlan = PvpokeDpPlanner.plan(
            attacker = effectiveBuild(state),
            defender = effectiveBuild(defender),
            attackerAttackStage = state.attackStage,
            defenderDefenseStage = defender.defenseStage,
            energy = state.energy,
            opponentHp = defender.hp,
            opponentShields = defender.shields
        ) ?: return null

        if (dpPlan.farmDown || dpPlan.moves.isEmpty()) {
            // Pinned post-plan: a pure farm-down can still force a guaranteed self-buffing move
            // instead of farming if getBoostMove() returns one.
            val boostMove = activeChargedMoves(state).firstOrNull {
                it.isSelfBuffing() && it.buffApplyChance >= 1.0 && state.energy >= it.energyCost
            }
            return boostMove?.let {
                ChargeDecision(
                    it,
                    "PVPOKE_BASELINE/V1.9 post-plan: DP prefers farm-down but a guaranteed boost move is available, so force-throw boost."
                )
            }
        }

        val selected = choosePvpokeDpFirstMove(
            state = state,
            defender = defender,
            plannedMoves = dpPlan.moves,
            baitShields = baitShields
        ) ?: return null

        if (state.energy < selected.energyCost) return null
        return ChargeDecision(
            selected,
            "PVPOKE_BASELINE/V1.9 DP: fastest guaranteed KO plan=${dpPlan.moves.joinToString("→") { it.moveId }} " +
                "at +${dpPlan.koTurn} turn(s), visited=${dpPlan.statesVisited}; post-plan V1.9 policy applied."
        )
    }

    private fun PvpMove.isSelfDebuffing(): Boolean = attackerAttackStageDelta < 0 || attackerDefenseStageDelta < 0
    private fun PvpMove.isSelfBuffing(): Boolean = attackerAttackStageDelta > 0 || attackerDefenseStageDelta > 0

    /**
     * PvPoke treats Aegislash Shield-form Charged Moves as self-debuffing for ActionLogic because
     * throwing them moves the user into Blade's much lower Defense profile. Keep that policy
     * classification separate from literal move stage deltas.
     */
    private fun isSelfDebuffing(state: MutableState, move: PvpMove): Boolean =
        move.isSelfDebuffing() || state.activeFormId == "aegislash_shield"

    /**
     * Common post-DP move ordering rules from the pinned ActionLogic (~736-920).
     *
     * V1.8 intentionally ports the deterministic/common cases only. Bait-specific logic that
     * depends on upstream baitShields configuration, bespoke form mechanics, and opponent
     * turnsToKO coupling remains capability-gated.
     */
    private fun choosePvpokeDpFirstMove(
        state: MutableState,
        defender: MutableState,
        plannedMoves: List<PvpMove>,
        baitShields: Boolean
    ): PvpMove? {
        if (plannedMoves.isEmpty()) return null

        val activeMoves = activeChargedMoves(state)
        val firstActive = activeMoves.first()
        fun damage(move: PvpMove) = damage(state, defender, move)
        fun actualDpe(move: PvpMove): Double = damage(move).toDouble() / move.energyCost.coerceAtLeast(1)
        fun wouldShield(move: PvpMove): Boolean {
            if (defender.shields <= 0) return false
            return pvpokeWouldShieldHeuristic(state, defender, move, damage(move))
        }

        var selected = plannedMoves.first()
        val debuffingMoveInPlan = plannedMoves.any { isSelfDebuffing(state, it) }

        // Pinned ~787-803: when baiting, build to a more expensive/more efficient move in the
        // plan before throwing the cheap move, unless the cheap move is a useful self-buff.
        if (baitShields && defender.shields > 0 && activeMoves.size > 1) {
            for (i in 1 until activeMoves.size) {
                val candidate = activeMoves[i]
                if (state.energy < candidate.energyCost && actualDpe(candidate) > actualDpe(selected)) {
                    var bait = true
                    if (actualDpe(firstActive) > 0.0 &&
                        actualDpe(candidate) / actualDpe(firstActive) <= 1.5 &&
                        firstActive.isSelfBuffing()
                    ) bait = false
                    if (bait) return null
                }
            }
        }

        // Pinned ~804-815: if the opponent is unlikely to shield the larger move, don't bait.
        if (baitShields && defender.shields > 0 && activeMoves.size > 1) {
            for (i in 1 until activeMoves.size) {
                val candidate = activeMoves[i]
                val selectedDpe = actualDpe(selected).coerceAtLeast(1e-9)
                val ratio = actualDpe(candidate) / selectedDpe
                if (state.energy >= candidate.energyCost && ratio > 1.5 && !wouldShield(candidate)) selected = candidate
            }
        }

        // Pinned ~816-826: non-boost-dependent plans may reorder by immediate damage.
        if (!baitShields || (defender.shields == 0 && !debuffingMoveInPlan)) {
            selected = plannedMoves.maxByOrNull(::damage) ?: selected
        }

        // Pinned ~827-830: shields up -> prefer lower/equal-energy, more-efficient non-debuff.
        if (defender.shields > 0 && activeMoves.size > 1 &&
            firstActive.energyCost <= selected.energyCost &&
            actualDpe(firstActive) > actualDpe(selected) &&
            !isSelfDebuffing(state, firstActive)
        ) selected = firstActive

        // Pinned ~831-834: shields down and both sides healthy -> avoid expensive self-debuff.
        if (defender.shields == 0 && activeMoves.size > 1 &&
            isSelfDebuffing(state, selected) && selected.energyCost > 50 &&
            state.hp.toDouble() / state.maxHp > 0.5 &&
            damage(selected).toDouble() / defender.hp.coerceAtLeast(1) < 0.8
        ) selected = firstActive

        // Pinned bandaids ~835-846.
        if (activeMoves.size > 1 && firstActive.energyCost == selected.energyCost &&
            actualDpe(firstActive) > actualDpe(selected) && !isSelfDebuffing(state, firstActive)) selected = firstActive
        if (activeMoves.size > 1 && firstActive.energyCost - 10 <= selected.energyCost &&
            actualDpe(firstActive) > actualDpe(selected) && isSelfDebuffing(state, selected) && !isSelfDebuffing(state, firstActive)) selected = firstActive
        if (activeMoves.size > 1 && firstActive.energyCost - selected.energyCost <= 5 &&
            actualDpe(firstActive) > actualDpe(selected) && firstActive.isSelfBuffing()) selected = firstActive

        // Pinned ~847-855: don't bait with a self-debuff if a ready non-debuff is more efficient.
        if (baitShields && defender.shields > 0 && activeMoves.size > 1) {
            for (i in 1 until activeMoves.size) {
                val candidate = activeMoves[i]
                if (state.energy >= candidate.energyCost && actualDpe(candidate) > actualDpe(selected) &&
                    isSelfDebuffing(state, selected) && !isSelfDebuffing(state, candidate)) selected = candidate
            }
        }

        // Pinned ~857-863: shields down -> don't use a meaningfully less-efficient self-debuff.
        if (defender.shields == 0 && activeMoves.size > 1 && isSelfDebuffing(state, selected)) {
            for (i in 1 until activeMoves.size) {
                val candidate = activeMoves[i]
                if (actualDpe(candidate) > actualDpe(selected) && !isSelfDebuffing(state, candidate)) selected = candidate
            }
        }

        // Pinned ~865-879: shields up -> prefer close non-debuffing alternative.
        if (defender.shields > 0 && activeMoves.size > 1 && isSelfDebuffing(state, firstActive)) {
            for (i in 1 until activeMoves.size) {
                val candidate = activeMoves[i]
                if (!isSelfDebuffing(state, candidate) &&
                    (baitShields || defender.hp - damage(firstActive) > 10) &&
                    candidate.energyCost - firstActive.energyCost <= 10 &&
                    actualDpe(firstActive) > 0.0 && actualDpe(candidate) / actualDpe(firstActive) > 0.7
                ) selected = candidate
            }
        }

        // Pinned ~881-887: no shields -> defer a survivable opponent Charged before self-debuffing.
        if (isSelfDebuffing(state, selected) && state.shields == 0 && state.energy < 100 && !firstActive.isSelfBuffing()) {
            val opponentBest = activeChargedMoves(defender).maxByOrNull { oppMove ->
                damage(defender, state, oppMove).toDouble() /
                    oppMove.energyCost.coerceAtLeast(1)
            }
            if (opponentBest != null && defender.energy >= opponentBest.energyCost) {
                val incomingDamage = damage(defender, state, opponentBest)
                if (incomingDamage < state.hp) return null
            }
        }

        // Pinned ~888-903: stack self-debuffs; once stacked, allow lower-energy bait when justified.
        if (isSelfDebuffing(state, selected)) {
            val targetEnergy = floor(100.0 / selected.energyCost.coerceAtLeast(1)).toInt() * selected.energyCost
            val selectedDamage = damage(selected)
            val oppFastDamage = damage(defender, state, defender.build.fastMove)
            val safeToStack = state.hp > oppFastDamage * 2 ||
                (defender.build.fastMove.cooldownMs - state.build.fastMove.cooldownMs > 500)
            if (state.energy < targetEnergy && (defender.hp > selectedDamage || defender.shields > 0) && safeToStack) {
                return null
            } else if (baitShields && defender.shields > 0 &&
                firstActive.energyCost - selected.energyCost <= 10 && !isSelfDebuffing(state, firstActive) &&
                (firstActive.isSelfBuffing() || wouldShield(selected))
            ) selected = firstActive
        }

        return if (state.energy >= selected.energyCost) selected else null
    }

    /**
     * Source-aligned turns-to-live search from the pinned ActionLogic pre-DP survival branch.
     * It explores opposing Fast/Charged pressure using current HP, energy, shields and cooldown.
     */
    private fun estimatePvpokeTurnsToLive(state: MutableState, defender: MutableState): Int {
        val ownFastDamage = damage(state, defender, state.build.fastMove)
        val oppFast = defender.build.fastMove
        val oppFastDamage = damage(defender, state, oppFast)
        val winsCmp = cmpAttack(state) >= cmpAttack(defender)
        val fastestOppCharge = activeChargedMoves(defender).minByOrNull { it.energyCost }
        val queue = ArrayDeque<SurvivalQueueState>()
        if (defender.cooldownTurns != 0) {
            queue.addFirst(
                SurvivalQueueState(
                    hp = state.hp - oppFastDamage,
                    opponentEnergy = (defender.energy + oppFast.energyGain).coerceAtMost(100),
                    turn = defender.cooldownTurns,
                    shields = state.shields
                )
            )
        } else {
            queue.addFirst(SurvivalQueueState(state.hp, defender.energy, 0, state.shields))
        }

        var turnsToLive = Int.MAX_VALUE
        var explored = 0
        while (queue.isNotEmpty() && explored < 256) {
            explored++
            val curr = queue.removeFirst()
            if (curr.hp > oppFastDamage) {
                if (winsCmp && curr.turn > state.build.fastMove.turns) continue
                if (!winsCmp && curr.turn > state.build.fastMove.turns + 1) continue
            }

            if (curr.shields != 0) {
                if (fastestOppCharge != null && curr.opponentEnergy >= fastestOppCharge.energyCost) {
                    queue.addFirst(
                        SurvivalQueueState(
                            hp = curr.hp - 1,
                            opponentEnergy = curr.opponentEnergy - fastestOppCharge.energyCost,
                            turn = curr.turn + 1,
                            shields = curr.shields - 1
                        )
                    )
                }
            } else {
                for (move in activeChargedMoves(defender)) {
                    if (curr.opponentEnergy < move.energyCost) continue
                    val moveDamage = damage(defender, state, move)
                    if (moveDamage >= curr.hp) {
                        var candidate = curr.turn
                        if (winsCmp && defender.build.fastMove.turns % state.build.fastMove.turns == 0) candidate++
                        turnsToLive = minOf(turnsToLive, candidate)
                        break
                    }
                    queue.addFirst(
                        SurvivalQueueState(
                            hp = curr.hp - moveDamage,
                            opponentEnergy = curr.opponentEnergy - move.energyCost,
                            turn = curr.turn + 1,
                            shields = curr.shields
                        )
                    )
                }
            }

            if (curr.hp - oppFastDamage <= 0) {
                turnsToLive = minOf(turnsToLive, curr.turn + oppFast.turns)
                break
            } else {
                queue.addFirst(
                    SurvivalQueueState(
                        hp = curr.hp - oppFastDamage,
                        opponentEnergy = (curr.opponentEnergy + oppFast.energyGain).coerceAtMost(100),
                        turn = curr.turn + oppFast.turns,
                        shields = curr.shields
                    )
                )
            }
        }

        if (turnsToLive == Int.MAX_VALUE) return Int.MAX_VALUE
        if (state.hp <= oppFastDamage * 2 && oppFast.turns == 1) turnsToLive--
        if (state.hp <= oppFastDamage && defender.cooldownTurns > 0 && oppFast.turns > 1) {
            turnsToLive = defender.cooldownTurns
            if (defender.hp > ownFastDamage) turnsToLive--
        }
        if (state.hp <= oppFastDamage && defender.cooldownTurns == 0 && oppFast.turns <= state.build.fastMove.turns + 1) {
            if (defender.hp > ownFastDamage) turnsToLive--
        }
        return turnsToLive.coerceAtLeast(0)
    }

    /**
     * Source-aligned subset of the pinned optimizeMoveTiming block. Full queue semantics and the
     * downstream DP planner remain parity-gated, but cadence/energy/survival guards are now direct
     * ports rather than heuristic approximations.
     */
    private fun shouldDelayForPvpokeMoveTiming(
        state: MutableState,
        defender: MutableState,
        plannedCharged: PvpMove,
        turnsToLive: Int,
        queuedFastMoves: Int
    ): Boolean {
        val ownFast = state.build.fastMove
        val oppFast = defender.build.fastMove

        var targetCooldown = 1
        if (ownFast.turns >= 4) targetCooldown = 2
        if (ownFast.turns >= 3 && oppFast.turns == 5) targetCooldown = 2
        if (ownFast.turns == 2 && oppFast.turns == 4) targetCooldown = 2
        if (ownFast.turns == oppFast.turns) targetCooldown = 0
        if (ownFast.turns > oppFast.turns && ownFast.turns % oppFast.turns == 0) targetCooldown = 0

        if (targetCooldown == 0) return false
        if (!(defender.cooldownTurns == 0 || defender.cooldownTurns > targetCooldown)) return false

        val oppFastDamage = damage(defender, state, oppFast)
        if (state.hp <= oppFastDamage) return false

        val futureQueued = queuedFastMoves + 1
        if (state.energy + ownFast.energyGain * futureQueued > 100) return false

        val mainEnergy = activeChargedMoves(state).firstOrNull()?.energyCost ?: plannedCharged.energyCost
        var turnsPlanned = ownFast.turns + (state.energy / mainEnergy.coerceAtLeast(1))
        if (cmpAttack(state) < cmpAttack(defender)) turnsPlanned++
        if (turnsToLive != Int.MAX_VALUE && turnsPlanned > turnsToLive) return false

        val plannedDamage = damage(state, defender, plannedCharged)
        if (defender.shields == 0 && state.energy >= plannedCharged.energyCost && plannedDamage >= defender.hp) return false

        val oppFastGain = oppFast.energyGain.coerceAtLeast(1)
        val fastMovesInOwnFast = ownFast.turns / oppFast.turns
        for (move in activeChargedMoves(defender)) {
            val fastsFromCharged = kotlin.math.ceil(
                (move.energyCost - defender.energy).toDouble() / oppFastGain
            ).toInt().coerceAtLeast(0)
            val turnsFromMove = fastsFromCharged * oppFast.turns + 1
            val moveDamageRaw = damage(defender, state, move)
            val moveDamage = if (state.shields > 0) 1 + oppFastDamage * fastMovesInOwnFast
                else moveDamageRaw + oppFastDamage * fastMovesInOwnFast
            if (turnsFromMove <= ownFast.turns && moveDamage >= state.hp) return false
        }

        val fastMovesWithLandingWindow = (ownFast.turns + 1) / oppFast.turns
        if (state.hp <= oppFastDamage * fastMovesWithLandingWindow) return false

        return true
    }

    /** Kept for generic/non-pinned policies; PVPOKE_BASELINE now uses turns-to-live instead. */
    private fun opponentCanKoOnNextAvailableAction(attacker: MutableState, defender: MutableState): Boolean {
        val fastDamage = damage(attacker, defender, attacker.build.fastMove)
        if (fastDamage >= defender.hp) return true
        return activeChargedMoves(attacker)
            .filter { attacker.energy >= it.energyCost }
            .any { damage(attacker, defender, it) >= defender.hp }
    }

    private fun executeCharged(
        turn: Int,
        attacker: MutableState,
        defender: MutableState,
        move: PvpMove,
        selectionReason: String,
        defenderShieldStrategy: ShieldStrategy,
        scenario: BattleScenario,
        rng: Random,
        events: MutableList<BattleTimelineEvent>,
        decisionTrace: MutableList<BattleDecisionTrace>
    ) {
        applyAegislashPreChargedFormChange(turn, attacker, events)
        val rawDamage = damage(attacker, defender, move)
        // PvPoke's wouldShield() evaluates against the attacker's pre-spend energy. Decide first,
        // then deduct the Charged Move cost. This order matters for cycle-survival estimates.
        // PvPoke marks special retaliation moves as `instant`; instant Charged Moves bypass the
        // normal shield decision entirely. This also prevents a stored Protect Shield from
        // suppressing Cramorant/Mimikyu special interactions incorrectly.
        val shield = "instant" !in move.tags &&
            shouldShield(attacker, defender, move, rawDamage, defenderShieldStrategy, scenario)
        attacker.energy = (attacker.energy - move.energyCost).coerceAtLeast(0)
        attacker.chargedMovesUsed++
        decisionTrace += trace(
            turn, BattleDecisionType.SHIELD_DECISION, defender, attacker, move.moveId,
            "${defenderShieldStrategy}: ${if (shield) "shield" else "no shield"}; incoming raw damage=$rawDamage."
        )
        var damage = if (shield) 1 else rawDamage
        if (shield) {
            defender.shields--
            events += BattleTimelineEvent(turn, defender.build.key, BattleEventType.SHIELD, shielded = true, note = "Protect Shield used")
            applyAegislashProtectShieldFormChange(turn, defender, events)
        }

        // F47/V1.13 — pinned Mimikyu Disguise. PvPoke applies this only when the defender did
        // not use a normal shield and the Charged Move is not tagged instant. The qualifying hit
        // is reduced to 1 damage; Mimikyu changes to Busted and gains its native -1 Defense.
        if (!shield && defender.mimikyuDisguiseActive && "instant" !in move.tags) {
            damage = 1
            defender.mimikyuDisguiseActive = false
            defender.activeFormId = "mimikyu_busted"
            defender.defenseStage = (defender.defenseStage - 1).coerceIn(-4, 4)
            events += BattleTimelineEvent(
                turn = turn,
                actorKey = defender.build.key,
                type = BattleEventType.SPECIAL_MECHANIC,
                moveId = move.moveId,
                damage = 1,
                note = "MIMIKYU_DISGUISE_BUSTED: Charged damage reduced to 1; activeForm=mimikyu_busted; native Defense -1."
            )
        }

        defender.hp = (defender.hp - damage).coerceAtLeast(0)
        events += BattleTimelineEvent(turn, attacker.build.key, BattleEventType.CHARGED_MOVE, move.moveId, damage, attacker.energy, shielded = shield)
        decisionTrace += trace(
            turn, BattleDecisionType.CHARGED_RESOLVED, attacker, defender, move.moveId,
            "$selectionReason Resolved for $damage damage${if (shield) " through shield" else ""}; energy=${attacker.energy}."
        )

        if (shouldApplyStatEffect(attacker, move, scenario.statEffectPolicy, rng)) {
            val beforeA = attacker.attackStage to attacker.defenseStage
            val beforeD = defender.attackStage to defender.defenseStage
            attacker.attackStage = (attacker.attackStage + move.attackerAttackStageDelta).coerceIn(-4, 4)
            attacker.defenseStage = (attacker.defenseStage + move.attackerDefenseStageDelta).coerceIn(-4, 4)
            defender.attackStage = (defender.attackStage + move.opponentAttackStageDelta).coerceIn(-4, 4)
            defender.defenseStage = (defender.defenseStage + move.opponentDefenseStageDelta).coerceIn(-4, 4)
            if (beforeA != (attacker.attackStage to attacker.defenseStage) || beforeD != (defender.attackStage to defender.defenseStage)) {
                events += BattleTimelineEvent(
                    turn, attacker.build.key, BattleEventType.STAT_CHANGE, move.moveId,
                    note = "Stages: self ${attacker.attackStage}/${attacker.defenseStage}, opponent ${defender.attackStage}/${defender.defenseStage}"
                )
            }
        }

        applyCramorantPostChargedFormChange(turn, attacker, move, events)
        applyMorpekoPostChargedToggle(turn, attacker, move, events)
        applyCramorantGulpMissileResponse(
            turn = turn,
            cramorant = defender,
            target = attacker,
            incomingMove = move,
            incomingWasShielded = shield,
            events = events,
            decisionTrace = decisionTrace
        )

        if (defender.hp == 0) {
            defender.faintedByCharged = true
            events += BattleTimelineEvent(turn, defender.build.key, BattleEventType.FAINT, note = "Fainted to Charged Move")
        }
    }

    /**
     * V1.17 strengthens the Aegislash stance model with form-specific Fast Move records. The pinned/current mechanic uses Shield stats until
     * Aegislash activates a Charged Move, then Blade stats for the Charged hit and subsequent
     * battle state. Using a Protect Shield returns Blade -> Shield. Switching/reset is outside
     * the current 1v1 engine and remains a future 3v3 concern.
     */
    private fun applyAegislashPreChargedFormChange(
        turn: Int,
        state: MutableState,
        events: MutableList<BattleTimelineEvent>
    ) {
        if (state.activeFormId != "aegislash_shield") return
        state.activeFormId = "aegislash_blade"
        state.build = normalizeAegislashFastMove(state.build, state.activeFormId)
        events += BattleTimelineEvent(
            turn = turn,
            actorKey = state.build.key,
            type = BattleEventType.SPECIAL_MECHANIC,
            note = "AEGISLASH_BLADE: Shield -> Blade before Charged Move; active Attack/Defense stats changed."
        )
    }

    private fun applyAegislashProtectShieldFormChange(
        turn: Int,
        state: MutableState,
        events: MutableList<BattleTimelineEvent>
    ) {
        if (state.activeFormId != "aegislash_blade") return
        state.activeFormId = "aegislash_shield"
        state.build = normalizeAegislashFastMove(state.build, state.activeFormId)
        events += BattleTimelineEvent(
            turn = turn,
            actorKey = state.build.key,
            type = BattleEventType.SPECIAL_MECHANIC,
            note = "AEGISLASH_SHIELD: Blade -> Shield after Protect Shield; active Attack/Defense stats changed."
        )
    }


    /**
     * Cramorant enters Gulping after Surf/Dive above 50% HP and Gorging at 50% HP or lower.
     * The exact-pin Game Master declares this as a post-Charged variable form change.
     */
    private fun applyCramorantPostChargedFormChange(
        turn: Int,
        state: MutableState,
        move: PvpMove,
        events: MutableList<BattleTimelineEvent>
    ) {
        if (state.activeFormId != "cramorant") return
        if (move.moveId != "DIVE" && move.moveId != "SURF") return

        val nextForm = if (state.hp.toDouble() / state.maxHp.toDouble() > 0.5) {
            "cramorant_gulping"
        } else {
            "cramorant_gorging"
        }
        state.activeFormId = nextForm
        events += BattleTimelineEvent(
            turn = turn,
            actorKey = state.build.key,
            type = BattleEventType.SPECIAL_MECHANIC,
            moveId = move.moveId,
            note = "CRAMORANT_LOAD: activeForm=$nextForm after ${move.moveId}; hp=${state.hp}/${state.maxHp}."
        )
    }

    /**
     * Gulp Missile is an automatic instant Charged response. It cannot be shielded, still fires if
     * Cramorant fainted to the triggering Charged Move, deals floor(15% max HP)+1, applies the
     * form-specific guaranteed debuff, then returns Cramorant to normal form.
     */
    private fun applyCramorantGulpMissileResponse(
        turn: Int,
        cramorant: MutableState,
        target: MutableState,
        incomingMove: PvpMove,
        incomingWasShielded: Boolean,
        events: MutableList<BattleTimelineEvent>,
        decisionTrace: MutableList<BattleDecisionTrace>
    ) {
        val loadedForm = cramorant.activeFormId
        if (loadedForm != "cramorant_gulping" && loadedForm != "cramorant_gorging") return
        if (incomingWasShielded || "instant" in incomingMove.tags) return

        val missileId: String
        val missileName: String
        var attackDelta = 0
        var defenseDelta = 0
        when (loadedForm) {
            "cramorant_gulping" -> {
                missileId = "GULP_MISSILE_ARROKUDA"
                missileName = "Gulp Missile (Arrokuda)"
                defenseDelta = -1
            }
            else -> {
                missileId = "GULP_MISSILE_PIKACHU"
                missileName = "Gulp Missile (Pikachu)"
                attackDelta = -2
            }
        }

        val missileDamage = floor(target.maxHp * 0.15).toInt() + 1
        events += BattleTimelineEvent(
            turn = turn,
            actorKey = cramorant.build.key,
            type = BattleEventType.SPECIAL_MECHANIC,
            moveId = missileId,
            note = "CRAMORANT_GULP_TRIGGER: $loadedForm retaliates after unshielded ${incomingMove.moveId}; ignores faint/shield."
        )

        target.hp = (target.hp - missileDamage).coerceAtLeast(0)
        cramorant.chargedMovesUsed++
        events += BattleTimelineEvent(
            turn = turn,
            actorKey = cramorant.build.key,
            type = BattleEventType.CHARGED_MOVE,
            moveId = missileId,
            damage = missileDamage,
            energyAfter = cramorant.energy,
            shielded = false,
            note = missileName
        )
        decisionTrace += trace(
            turn, BattleDecisionType.CHARGED_RESOLVED, cramorant, target, missileId,
            "Automatic instant Gulp Missile resolved for $missileDamage damage; percentMaxHP=15%."
        )

        val beforeAttack = target.attackStage
        val beforeDefense = target.defenseStage
        target.attackStage = (target.attackStage + attackDelta).coerceIn(-4, 4)
        target.defenseStage = (target.defenseStage + defenseDelta).coerceIn(-4, 4)
        if (beforeAttack != target.attackStage || beforeDefense != target.defenseStage) {
            events += BattleTimelineEvent(
                turn = turn,
                actorKey = cramorant.build.key,
                type = BattleEventType.STAT_CHANGE,
                moveId = missileId,
                note = "Gulp Missile stages: opponent ${target.attackStage}/${target.defenseStage}."
            )
        }

        cramorant.activeFormId = "cramorant"
        events += BattleTimelineEvent(
            turn = turn,
            actorKey = cramorant.build.key,
            type = BattleEventType.SPECIAL_MECHANIC,
            moveId = missileId,
            note = "CRAMORANT_RESET: $loadedForm -> cramorant after Gulp Missile."
        )

        if (target.hp == 0) {
            target.faintedByCharged = true
            events += BattleTimelineEvent(turn, target.build.key, BattleEventType.FAINT, moveId = missileId, note = "Fainted to Gulp Missile")
        }
    }

    /**
     * Morpeko's battle form toggles after every Charged Move. The form shares base stats/types,
     * but Aura Wheel's active type/move id changes with the form. The pinned a93147b source has
     * a known one-way-toggle bug; this engine intentionally models the declared/game mechanic.
     */
    private fun activeChargedMoves(state: MutableState): List<PvpMove> = state.build.chargedMoves.map { move ->
        when {
            move.moveId == "AURA_WHEEL_ELECTRIC" || move.moveId == "AURA_WHEEL_DARK" -> when (state.activeFormId) {
                "morpeko_hangry" -> move.copy(moveId = "AURA_WHEEL_DARK", type = "dark")
                "morpeko_full_belly" -> move.copy(moveId = "AURA_WHEEL_ELECTRIC", type = "electric")
                else -> move
            }
            else -> move
        }
    }

    private fun applyMorpekoPostChargedToggle(
        turn: Int,
        state: MutableState,
        move: PvpMove,
        events: MutableList<BattleTimelineEvent>
    ) {
        val nextForm = when (state.activeFormId) {
            "morpeko_full_belly" -> "morpeko_hangry"
            "morpeko_hangry" -> "morpeko_full_belly"
            else -> return
        }
        state.activeFormId = nextForm
        val nextWheel = if (nextForm == "morpeko_hangry") "AURA_WHEEL_DARK" else "AURA_WHEEL_ELECTRIC"
        events += BattleTimelineEvent(
            turn = turn,
            actorKey = state.build.key,
            type = BattleEventType.SPECIAL_MECHANIC,
            moveId = move.moveId,
            note = "MORPEKO_TOGGLE: activeForm=$nextForm; activeAuraWheel=$nextWheel after Charged Move."
        )
    }

    /**
     * Aegislash has form-specific Fast Move records in the pinned Game Master. Shield Forme uses
     * unlisted 0-power/6-energy variants while Blade Forme uses the ordinary move records.
     *
     * The form transition happens while the same battle state remains active, so the simulator
     * must update future Fast Moves as well as Attack/Defense. Already queued Fast Moves retain
     * the move object captured when they started, which is intentional.
     */
    private fun normalizeAegislashFastMove(build: BattleBuild, activeFormId: String): BattleBuild {
        if (activeFormId !in setOf("aegislash_shield", "aegislash_blade")) return build
        val move = build.fastMove
        val normalized = when (activeFormId) {
            "aegislash_shield" -> when (move.moveId) {
                "PSYCHO_CUT", "AEGISLASH_CHARGE_PSYCHO_CUT" -> move.copy(
                    moveId = "AEGISLASH_CHARGE_PSYCHO_CUT",
                    name = "Psycho Cut",
                    abbreviation = "PC",
                    type = "psychic",
                    power = 0,
                    energyCost = 0,
                    energyGain = 6,
                    turns = 2,
                    cooldownMs = 1000
                )
                "AIR_SLASH", "AEGISLASH_CHARGE_AIR_SLASH" -> move.copy(
                    moveId = "AEGISLASH_CHARGE_AIR_SLASH",
                    name = "Air Slash",
                    abbreviation = "AS",
                    type = "flying",
                    power = 0,
                    energyCost = 0,
                    energyGain = 6,
                    turns = 3,
                    cooldownMs = 1500
                )
                else -> move
            }
            "aegislash_blade" -> when (move.moveId) {
                "AEGISLASH_CHARGE_PSYCHO_CUT", "PSYCHO_CUT" -> move.copy(
                    moveId = "PSYCHO_CUT",
                    name = "Psycho Cut",
                    abbreviation = "PC",
                    type = "psychic",
                    power = 4,
                    energyCost = 0,
                    energyGain = 9,
                    turns = 2,
                    cooldownMs = 1000
                )
                "AEGISLASH_CHARGE_AIR_SLASH", "AIR_SLASH" -> move.copy(
                    moveId = "AIR_SLASH",
                    name = "Air Slash",
                    abbreviation = "AS",
                    type = "flying",
                    power = 9,
                    energyCost = 0,
                    energyGain = 9,
                    turns = 3,
                    cooldownMs = 1500
                )
                else -> move
            }
            else -> move
        }
        return if (normalized == move) build else build.copy(fastMove = normalized)
    }

    private fun effectiveBuild(state: MutableState): BattleBuild {
        val original = state.build
        val activeSpecies = when (state.activeFormId) {
            "aegislash_shield" -> original.species.copy(
                speciesId = "aegislash_shield",
                form = "Shield",
                baseAttack = 97,
                baseDefense = 272,
                baseStamina = 155
            )
            "aegislash_blade" -> original.species.copy(
                speciesId = "aegislash_blade",
                form = "Blade",
                baseAttack = 272,
                baseDefense = 97,
                baseStamina = 155
            )
            else -> if (state.activeFormId == original.species.speciesId) original.species
                else original.species.copy(speciesId = state.activeFormId)
        }
        val activeMoves = activeChargedMoves(state)
        val speciesChanged = activeSpecies !== original.species
        val movesChanged = activeMoves != original.chargedMoves
        return if (!speciesChanged && !movesChanged) original else original.copy(species = activeSpecies, chargedMoves = activeMoves)
    }

    private fun damage(attacker: MutableState, defender: MutableState, move: PvpMove): Int =
        damage(attacker, attacker.attackStage, defender, defender.defenseStage, move)

    private fun damage(
        attacker: MutableState,
        attackerAttackStage: Int,
        defender: MutableState,
        defenderDefenseStage: Int,
        move: PvpMove
    ): Int = BattleDamage.damage(
        effectiveBuild(attacker),
        attackerAttackStage,
        effectiveBuild(defender),
        defenderDefenseStage,
        move
    )

    private fun cmpAttack(state: MutableState): Double = BattleDamage.cmpAttack(effectiveBuild(state))

    private fun nativeDefenseStage(build: BattleBuild): Int =
        (build.initialDefenseStage + if (build.species.speciesId == "mimikyu_busted") -1 else 0).coerceIn(-4, 4)

    private fun trace(
        turn: Int,
        type: BattleDecisionType,
        actor: MutableState,
        opponent: MutableState,
        moveId: String? = null,
        reason: String? = null
    ) = BattleDecisionTrace(
        turn = turn,
        actorKey = actor.build.key,
        type = type,
        actorHp = actor.hp,
        opponentHp = opponent.hp,
        actorEnergy = actor.energy,
        opponentEnergy = opponent.energy,
        actorShields = actor.shields,
        opponentShields = opponent.shields,
        actorCooldown = actor.cooldownTurns,
        opponentCooldown = opponent.cooldownTurns,
        moveId = moveId,
        reason = reason
    )

    private fun shouldShield(
        attacker: MutableState,
        defender: MutableState,
        incomingMove: PvpMove,
        damage: Int,
        strategy: ShieldStrategy,
        scenario: BattleScenario
    ): Boolean {
        if (defender.shields <= 0) return false
        return when (strategy) {
            ShieldStrategy.ALWAYS -> true
            ShieldStrategy.NEVER -> false
            ShieldStrategy.LETHAL_ONLY -> damage >= defender.hp
            ShieldStrategy.CONSERVE -> damage >= defender.hp || damage.toDouble() / defender.maxHp >= scenario.highDamageShieldThreshold
            ShieldStrategy.PVPOKE_DEFAULT -> shouldShieldPvpokeDefault(attacker, defender, incomingMove, damage)
        }
    }

    /**
     * Pinned PvPoke default shielding semantics for ordinary 1v1 simulate mode.
     *
     * Important: ActionLogic.wouldShield() is NOT the global default shield policy in the pinned
     * Battle.js. Battle.useMove() starts with useShield=true for a normal Charged Move and only
     * delegates to wouldShield() for specific stat-effect / special-form situations. V1.5 treated
     * wouldShield() as universal and was therefore too selective.
     *
     * We reproduce the standard cases representable by [PvpMove] and the pinned Cramorant
     * delegation thresholds. Exact behavior still depends on the local wouldShield port and
     * remains external-parity gated.
     */
    private fun shouldShieldPvpokeDefault(
        attacker: MutableState,
        defender: MutableState,
        incomingMove: PvpMove,
        damage: Int
    ): Boolean {
        // Pinned Battle.js default for an ordinary Charged Move: shield if a shield is available.
        // The selective heuristic is consulted for early self-Attack buffs / opponent-Defense
        // debuffs (Power-Up Punch / Acid Spray-style cases) and a few other special states.
        val cramorantLoadedWeakHit =
            defender.activeFormId in setOf("cramorant_gulping", "cramorant_gorging") &&
                damage * 2.2 < defender.hp
        val weakCramorantCharged =
            attacker.build.species.speciesId.startsWith("cramorant") &&
                damage.toDouble() / defender.hp.coerceAtLeast(1) < 0.33
        val delegatesToWouldShield =
            incomingMove.attackerAttackStageDelta > 0 ||
                incomingMove.opponentDefenseStageDelta < 0 ||
                cramorantLoadedWeakHit ||
                weakCramorantCharged

        if (!delegatesToWouldShield) {
            return true
        }

        return pvpokeWouldShieldHeuristic(attacker, defender, incomingMove, damage)
    }

    /** Standard-case port of ActionLogic.wouldShield(), used only where pinned Battle.js delegates. */
    private fun pvpokeWouldShieldHeuristic(
        attacker: MutableState,
        defender: MutableState,
        incomingMove: PvpMove,
        damage: Int
    ): Boolean {
        val postMoveHp = defender.hp - damage

        // wouldShield() temporarily applies relevant buffs before estimating future Fast damage.
        // For the standard delegated cases represented here, approximate that effect explicitly.
        val effectiveAttackerStage = if (incomingMove.attackerAttackStageDelta > 0) {
            (attacker.attackStage + incomingMove.attackerAttackStageDelta).coerceIn(-4, 4)
        } else attacker.attackStage
        val effectiveDefenderStage = if (incomingMove.opponentDefenseStageDelta < 0) {
            (defender.defenseStage + incomingMove.opponentDefenseStageDelta).coerceIn(-4, 4)
        } else defender.defenseStage

        val fastDamage = damage(attacker, effectiveAttackerStage, defender, effectiveDefenderStage, attacker.build.fastMove)

        // Upstream gives itself a small margin when estimating the next cycle.
        // executeCharged() calls this before spending energy, matching Battle.js ordering.
        val spentEquivalent = (attacker.energy - incomingMove.energyCost).coerceAtLeast(0)
        val energyNeeded = (incomingMove.energyCost - spentEquivalent).coerceAtLeast(0)
        val fastAttacks = kotlin.math.ceil(
            energyNeeded.toDouble() / attacker.build.fastMove.energyGain.coerceAtLeast(1)
        ).toInt() + 1
        val fastAttackDamage = fastAttacks * fastDamage
        val cycleDamage = (fastAttackDamage + 1) * defender.shields

        var useShield = postMoveHp <= cycleDamage
        val fastDpt = fastDamage.toDouble() / attacker.build.fastMove.turns.coerceAtLeast(1)

        for (charged in activeChargedMoves(attacker)) {
            val chargedDamage = damage(attacker, effectiveAttackerStage, defender, effectiveDefenderStage, charged)
            if (chargedDamage >= defender.hp / 1.4 && fastDpt > 1.5) {
                useShield = true
            }
            if (chargedDamage >= defender.hp - cycleDamage) {
                useShield = true
            }
        }

        if (incomingMove.attackerAttackStageDelta < 0 && damage.toDouble() / defender.hp.coerceAtLeast(1) > 0.55) {
            useShield = true
        }

        return useShield
    }

    private fun shouldApplyStatEffect(attacker: MutableState, move: PvpMove, policy: StatEffectPolicy, rng: Random): Boolean {
        if (!move.hasStatEffect || move.buffApplyChance <= 0.0) return false
        return when (policy) {
            StatEffectPolicy.GUARANTEED_ONLY -> move.buffApplyChance >= 1.0
            StatEffectPolicy.PVPOKE_DETERMINISTIC_METER -> {
                if (move.buffApplyChance >= 1.0) {
                    true
                } else {
                    val before = attacker.buffApplyMeters[move.moveId] ?: 0.0
                    val after = before + move.buffApplyChance
                    attacker.buffApplyMeters[move.moveId] = after
                    floor(before).toInt() < floor(after).toInt()
                }
            }
            StatEffectPolicy.SEEDED_RANDOM -> rng.nextDouble() < move.buffApplyChance
            StatEffectPolicy.FORCE_APPLY -> true
            StatEffectPolicy.FORCE_NONE -> false
        }
    }

    /** PvPoke-compatible rating: 500 * opponent damage fraction + 500 * own HP fraction. */
    private fun battleRating(self: MutableState, opponent: MutableState): Int = floor(
        500.0 * ((opponent.maxHp - opponent.hp).toDouble() / opponent.maxHp) +
            500.0 * (self.hp.toDouble() / self.maxHp)
    ).toInt().coerceIn(0, 1000)

    private fun snapshot(s: MutableState) = BattleStateSnapshot(
        key = s.build.key,
        hp = s.hp,
        maxHp = s.maxHp,
        energy = s.energy,
        shields = s.shields,
        attackStage = s.attackStage,
        defenseStage = s.defenseStage,
        fastMovesUsed = s.fastMovesUsed,
        chargedMovesUsed = s.chargedMovesUsed,
        activeFormId = s.activeFormId,
        disguiseActive = s.mimikyuDisguiseActive
    )
}

object MatchupBatchAnalyzer {
    fun analyze(
        engine: BattleEngine,
        subject: BattleBuild,
        opponents: List<BattleBuild>,
        scenario: BattleScenario
    ): List<KeyMatchupResult> = opponents.map { opponent ->
        val result = engine.simulate(subject, opponent, scenario)
        KeyMatchupResult(
            opponentKey = opponent.key,
            opponentSpeciesId = opponent.species.speciesId,
            outcome = result.outcomeForA,
            battleRating = result.battleRatingA,
            turns = result.turns,
            subjectHpPercent = result.finalA.hpPercent,
            opponentHpPercent = result.finalB.hpPercent
        )
    }
}
