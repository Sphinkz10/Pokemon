package com.rui.pvpgo.engine

import com.rui.pvpgo.domain.BattleOutcome
import kotlin.math.abs
import kotlin.math.floor

enum class ParityEvidenceKind {
    /** Expected value is encoded from a documented/current upstream source rule. */
    SOURCE_RULE,
    /** Internal safety/invariant test; useful but not proof of external simulator parity. */
    INTERNAL_INVARIANT,
    /** Concrete expected output captured from the SAME pinned upstream revision. */
    EXTERNAL_RESULT,
    /** External result from a different/older upstream revision; diagnostic only, never a current parity gate. */
    HISTORICAL_EXTERNAL_RESULT
}

enum class FixtureStatus { PASS, DIFF, BLOCKED }

data class ParityFixtureResult(
    val id: String,
    val family: String,
    val status: FixtureStatus,
    val critical: Boolean,
    val expected: String,
    val actual: String,
    val evidenceKind: ParityEvidenceKind,
    val referenceUrl: String? = null,
    val note: String? = null
)

data class ParityFixtureMatrix(val results: List<ParityFixtureResult>) {
    val passCount: Int get() = results.count { it.status == FixtureStatus.PASS }
    val diffCount: Int get() = results.count { it.status == FixtureStatus.DIFF }
    val blockedCount: Int get() = results.count { it.status == FixtureStatus.BLOCKED }
    val sourceRuleResults: List<ParityFixtureResult> get() = results.filter { it.evidenceKind == ParityEvidenceKind.SOURCE_RULE }
    val sourceRulePassCount: Int get() = sourceRuleResults.count { it.status == FixtureStatus.PASS }
    val internalResults: List<ParityFixtureResult> get() = results.filter { it.evidenceKind == ParityEvidenceKind.INTERNAL_INVARIANT }
    val externalResults: List<ParityFixtureResult> get() = results.filter { it.evidenceKind == ParityEvidenceKind.EXTERNAL_RESULT }
    val historicalExternalResults: List<ParityFixtureResult> get() = results.filter { it.evidenceKind == ParityEvidenceKind.HISTORICAL_EXTERNAL_RESULT }
    val criticalDiffs: List<ParityFixtureResult> get() = results.filter { it.critical && it.status == FixtureStatus.DIFF }
    val externalBaselineConfirmed: Boolean
        get() = externalResults.isNotEmpty() && externalResults.all { it.status == FixtureStatus.PASS }
    val recommendationGateOpen: Boolean
        get() = criticalDiffs.isEmpty() && externalBaselineConfirmed && BattleParityGate.current().matchupAdviceGateOpen
}

/**
 * Reproducible parity fixture suite.
 *
 * It intentionally separates source-rule fixtures from external-result fixtures. Passing our own
 * source-rule fixtures is necessary but is NOT sufficient to claim full PvPoke result parity.
 */
object BattleParityFixtureSuite {
    private const val BATTLE_SOURCE = PvPokeReferencePin.BATTLE_URL
    private const val ACTION_LOGIC_SOURCE = PvPokeReferencePin.ACTION_LOGIC_URL
    private const val DAMAGE_SOURCE = "https://github.com/pvpoke/pvpoke/blob/${PvPokeReferencePin.COMMIT_SHA}/src/js/battle/DamageCalculator.js"

    private val stageExpected = mapOf(
        -4 to 0.5,
        -3 to 4.0 / 7.0,
        -2 to 2.0 / 3.0,
        -1 to 0.8,
        0 to 1.0,
        1 to 1.25,
        2 to 1.5,
        3 to 1.75,
        4 to 2.0
    )

    private fun species(id: String, atk: Int = 200, def: Int = 180, hp: Int = 220, types: List<String> = listOf("normal")) =
        PokemonSpecies(dex = 1, name = id, speciesId = id, types = types, baseAttack = atk, baseDefense = def, baseStamina = hp)

    private fun build(
        key: String,
        species: PokemonSpecies,
        fast: PvpMove,
        charged: List<PvpMove>,
        energy: Int = 0,
        shadow: Boolean = false,
        iv: IvSpread = IvSpread(0, 15, 15),
        level: Double = 30.0
    ) = BattleBuild(key, species, iv, level, fast, charged, isShadow = shadow, initialEnergy = energy)

    private fun result(
        id: String,
        family: String,
        expected: Any,
        actual: Any,
        pass: Boolean,
        critical: Boolean = true,
        evidence: ParityEvidenceKind = ParityEvidenceKind.SOURCE_RULE,
        reference: String? = null,
        note: String? = null
    ) = ParityFixtureResult(
        id = id,
        family = family,
        status = if (pass) FixtureStatus.PASS else FixtureStatus.DIFF,
        critical = critical,
        expected = expected.toString(),
        actual = actual.toString(),
        evidenceKind = evidence,
        referenceUrl = reference,
        note = note
    )

    private fun blocked(
        id: String,
        family: String,
        expected: String,
        actual: String,
        critical: Boolean,
        evidence: ParityEvidenceKind,
        reference: String? = null,
        note: String? = null
    ) = ParityFixtureResult(
        id = id,
        family = family,
        status = FixtureStatus.BLOCKED,
        critical = critical,
        expected = expected,
        actual = actual,
        evidenceKind = evidence,
        referenceUrl = reference,
        note = note
    )

    private fun close(a: Double, b: Double, eps: Double = 1e-9) = abs(a - b) <= eps

    fun run(): ParityFixtureMatrix {
        val out = mutableListOf<ParityFixtureResult>()
        val engine = BattleEngine()

        // 9 explicit stage-multiplier source fixtures.
        stageExpected.forEach { (stage, expected) ->
            val actual = BattleDamage.stageMultiplier(stage)
            out += result("STAGE-$stage", "stat-stages", expected, actual, close(expected, actual), reference = DAMAGE_SOURCE)
        }

        // 81 damage fixtures: every attacker/defender stage pair, using an independent expected reconstruction.
        val neutralA = species("neutral_a", atk = 205, def = 170, hp = 230, types = listOf("normal"))
        val neutralB = species("neutral_b", atk = 180, def = 195, hp = 240, types = listOf("normal"))
        val fastOne = PvpMove("FAST_ONE", "Fast One", type = "normal", power = 1, energyGain = 1, turns = 1)
        val neutralCharge = PvpMove("NEUTRAL_CHARGE", "Neutral Charge", type = "normal", power = 77, energyCost = 45)
        val ba = build("damage-a", neutralA, fastOne, listOf(neutralCharge))
        val bb = build("damage-b", neutralB, fastOne, listOf(neutralCharge))
        for (atkStage in -4..4) for (defStage in -4..4) {
            val atk = ba.stats.attack * stageExpected.getValue(atkStage)
            val def = bb.stats.defense * stageExpected.getValue(defStage)
            val expected = floor(
                neutralCharge.power * BattleDamage.STAB * (atk / def) * 0.5 * BattleDamage.TRAINER_BATTLE_BONUS
            ).toInt() + 1
            val actual = BattleDamage.damage(ba, atkStage, bb, defStage, neutralCharge)
            out += result(
                "DAMAGE-S${atkStage}-D${defStage}", "damage-rounding", expected, actual, expected == actual,
                reference = DAMAGE_SOURCE
            )
        }

        // Type-effectiveness fixtures encode known GO PvP multipliers directly.
        data class TypeFixture(val id: String, val move: String, val defenders: List<String>, val expected: Double)
        listOf(
            TypeFixture("NEUTRAL", "normal", listOf("water"), 1.0),
            TypeFixture("SUPER", "water", listOf("ground"), PvpTypeChart.SUPER_EFFECTIVE),
            TypeFixture("RESIST", "water", listOf("water"), PvpTypeChart.RESISTED),
            TypeFixture("IMMUNITY", "electric", listOf("ground"), PvpTypeChart.IMMUNITY_AS_DOUBLE_RESIST),
            TypeFixture("DOUBLE-WEAK", "ice", listOf("dragon", "flying"), PvpTypeChart.SUPER_EFFECTIVE * PvpTypeChart.SUPER_EFFECTIVE),
            TypeFixture("DOUBLE-RESIST", "grass", listOf("fire", "flying"), PvpTypeChart.RESISTED * PvpTypeChart.RESISTED),
            TypeFixture("WEAK-PLUS-RESIST", "fire", listOf("grass", "water"), PvpTypeChart.SUPER_EFFECTIVE * PvpTypeChart.RESISTED),
            TypeFixture("IMMUNITY-PLUS-WEAK", "ground", listOf("flying", "fire"), PvpTypeChart.IMMUNITY_AS_DOUBLE_RESIST * PvpTypeChart.SUPER_EFFECTIVE)
        ).forEach { f ->
            val actual = PvpTypeChart.effectiveness(f.move, f.defenders)
            out += result("TYPE-${f.id}", "type-effectiveness", f.expected, actual, close(f.expected, actual), reference = DAMAGE_SOURCE)
        }

        // Shadow combinations: expected damage reconstruction is independent of BattleDamage.damage().
        listOf(false to false, true to false, false to true, true to true).forEachIndexed { idx, (shadowA, shadowB) ->
            val a = ba.copy(isShadow = shadowA)
            val b = bb.copy(isShadow = shadowB)
            val atk = a.stats.attack * if (shadowA) BattleDamage.SHADOW_ATTACK else 1.0
            val def = b.stats.defense * if (shadowB) BattleDamage.SHADOW_DEFENSE else 1.0
            val expected = floor(neutralCharge.power * BattleDamage.STAB * (atk / def) * 0.5 * BattleDamage.TRAINER_BATTLE_BONUS).toInt() + 1
            val actual = BattleDamage.damage(a, 0, b, 0, neutralCharge)
            out += result("SHADOW-$idx", "shadow-damage", expected, actual, expected == actual, reference = DAMAGE_SOURCE)
        }

        // Fast Move damage/energy timing for 1..5 turn moves.
        val tank = species("tank", atk = 130, def = 240, hp = 300, types = listOf("normal"))
        val dummyCharge = PvpMove("DUMMY_CHARGE", "Dummy", type = "normal", power = 1, energyCost = 100)
        for (duration in 1..5) {
            val fast = PvpMove("FAST_$duration", "Fast $duration", type = "normal", power = 1, energyGain = 1, turns = duration)
            val x = build("dur-a-$duration", tank, fast, listOf(dummyCharge))
            val y = build("dur-b-$duration", tank, fast, listOf(dummyCharge))
            val sim = engine.simulate(x, y, BattleScenario(shieldsA = 0, shieldsB = 0, maxTurns = 8))
            val first = sim.timeline.first { it.actorKey == x.key && it.type == BattleEventType.FAST_MOVE }
            out += result("FAST-DURATION-$duration", "fast-timing", duration, first.turn, first.turn == duration, reference = BATTLE_SOURCE)
        }

        // Energy cap at 100 after a Fast Move.
        val highEnergyFast = PvpMove("HIGH_ENERGY", "High Energy", type = "normal", power = 1, energyGain = 40, turns = 1)
        val capA = build("cap-a", tank, highEnergyFast, listOf(dummyCharge), energy = 90)
        val capB = build("cap-b", tank, fastOne, listOf(dummyCharge))
        val capSim = engine.simulate(capA, capB, BattleScenario(shieldsA = 0, shieldsB = 0, maxTurns = 1))
        out += result("ENERGY-CAP", "energy", 100, capSim.finalA.energy, capSim.finalA.energy == 100, reference = BATTLE_SOURCE)

        // Shields: first shielded Charged Move must deal exactly 1 damage and consume one shield.
        val charge50 = PvpMove("CHARGE_50", "Charge 50", type = "normal", power = 100, energyCost = 50)
        for (shields in 0..2) {
            val sa = build("shield-a-$shields", neutralA, fastOne, listOf(charge50), energy = 50)
            val sb = build("shield-b-$shields", neutralB, fastOne, listOf(charge50), energy = 0)
            val sim = engine.simulate(sa, sb, BattleScenario(
                shieldsA = 0, shieldsB = shields,
                policyA = BattleSidePolicy(ChargeStrategy.MAX_DAMAGE, ShieldStrategy.ALWAYS),
                policyB = BattleSidePolicy(ChargeStrategy.MAX_DAMAGE, ShieldStrategy.ALWAYS),
                maxTurns = 2
            ))
            val charge = sim.timeline.first { it.actorKey == sa.key && it.type == BattleEventType.CHARGED_MOVE }
            val expectedDamage = if (shields > 0) 1 else BattleDamage.damage(sa, 0, sb, 0, charge50)
            out += result("SHIELD-DAMAGE-$shields", "shields", expectedDamage, charge.damage ?: -1, charge.damage == expectedDamage, reference = BATTLE_SOURCE)
            val expectedRemaining = (shields - 1).coerceAtLeast(0)
            out += result("SHIELD-COUNT-$shields", "shields", expectedRemaining, sim.finalB.shields, sim.finalB.shields == expectedRemaining, reference = BATTLE_SOURCE)
        }

        // Pinned Battle.js starts ordinary simulate-mode Charged Moves with useShield=true.
        // A configured shield therefore blocks even a harmless ordinary Charged Move unless the
        // move/state enters one of the explicit wouldShield-delegation branches.
        val weakCharge = PvpMove("WEAK_CHARGE", "Weak Charge", type = "normal", power = 1, energyCost = 40)
        val weakShieldAttacker = build("weak-shield-a", tank, fastOne, listOf(weakCharge), energy = 40)
        val weakShieldDefender = build("weak-shield-b", tank, fastOne, listOf(dummyCharge), energy = 0)
        val weakShieldSim = engine.simulate(weakShieldAttacker, weakShieldDefender, BattleScenario(
            shieldsA = 0, shieldsB = 1,
            policyA = BattleSidePolicy(ChargeStrategy.MAX_DAMAGE, ShieldStrategy.NEVER),
            policyB = BattleSidePolicy(ChargeStrategy.MAX_DAMAGE, ShieldStrategy.PVPOKE_DEFAULT),
            maxTurns = 1
        ))
        out += result(
            "PVPOKE-SHIELD-ORDINARY-ALWAYS", "shield-policy", 0, weakShieldSim.finalB.shields, weakShieldSim.finalB.shields == 0,
            reference = BATTLE_SOURCE, note = "Pinned Battle.js initializes useShield=true for ordinary Charged Moves."
        )

        // Power-Up-Punch-style self-Attack buffs are delegated to ActionLogic.wouldShield().
        // In a healthy low-pressure state, that delegated heuristic may preserve the shield.
        val setupCharge = PvpMove(
            "SETUP_CHARGE", "Setup Charge", type = "normal", power = 1, energyCost = 40,
            attackerAttackStageDelta = 1, buffApplyChance = 1.0
        )
        val setupShieldAttacker = build("setup-shield-a", tank, fastOne, listOf(setupCharge), energy = 40)
        val setupShieldDefender = build("setup-shield-b", tank, fastOne, listOf(dummyCharge), energy = 0)
        val setupShieldSim = engine.simulate(setupShieldAttacker, setupShieldDefender, BattleScenario(
            shieldsA = 0, shieldsB = 1,
            policyA = BattleSidePolicy(ChargeStrategy.MAX_DAMAGE, ShieldStrategy.NEVER),
            policyB = BattleSidePolicy(ChargeStrategy.MAX_DAMAGE, ShieldStrategy.PVPOKE_DEFAULT),
            maxTurns = 1
        ))
        out += result(
            "PVPOKE-SHIELD-DELEGATED-SETUP", "shield-policy", 1, setupShieldSim.finalB.shields, setupShieldSim.finalB.shields == 1,
            reference = ACTION_LOGIC_SOURCE, note = "Pinned Battle.js delegates early self-Attack-buff moves to ActionLogic.wouldShield()."
        )

        // Lethal ordinary pressure still consumes the shield.
        val lethalShieldAtkSpecies = species("lethal-shield-atk", atk = 260, def = 150, hp = 200)
        val lethalShieldDefSpecies = species("lethal-shield-def", atk = 150, def = 180, hp = 180)
        val lethalShieldAttacker = build("lethal-shield-a", lethalShieldAtkSpecies, fastOne, listOf(charge50.copy(power = 9999)), energy = 50)
        val lethalShieldDefender = build("lethal-shield-b", lethalShieldDefSpecies, fastOne, listOf(dummyCharge), energy = 0)
        val lethalShieldSim = engine.simulate(lethalShieldAttacker, lethalShieldDefender, BattleScenario(
            shieldsA = 0, shieldsB = 1,
            policyA = BattleSidePolicy(ChargeStrategy.MAX_DAMAGE, ShieldStrategy.NEVER),
            policyB = BattleSidePolicy(ChargeStrategy.MAX_DAMAGE, ShieldStrategy.PVPOKE_DEFAULT),
            maxTurns = 1
        ))
        out += result(
            "PVPOKE-SHIELD-ORDINARY-LETHAL", "shield-policy", 0, lethalShieldSim.finalB.shields, lethalShieldSim.finalB.shields == 0,
            reference = BATTLE_SOURCE, note = "Ordinary Charged Move consumes the available shield in pinned simulate mode."
        )

        // Move timing: current upstream defaults optimizeMoveTiming=true. Equal-duration Fast Moves
        // do not delay a ready Charged Move; cadence mismatch may safely insert one Fast Move.
        val timingCharge = PvpMove("TIMING_CHARGE", "Timing Charge", type = "normal", power = 20, energyCost = 40)
        val fast3 = PvpMove("TIMING_FAST_3", "Timing Fast 3", type = "normal", power = 1, energyGain = 10, turns = 3)
        val fast2 = PvpMove("TIMING_FAST_2", "Timing Fast 2", type = "normal", power = 1, energyGain = 10, turns = 2)
        val timingA = build("timing-a", tank, fast3, listOf(timingCharge), energy = 40)
        val timingB = build("timing-b", tank, fast2, listOf(timingCharge), energy = 0)
        val timingMismatch = engine.simulate(timingA, timingB, BattleScenario(
            shieldsA = 0, shieldsB = 0,
            policyA = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.NEVER),
            policyB = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.NEVER),
            maxTurns = 8
        ))
        val firstTimingCharge = timingMismatch.timeline.firstOrNull { it.actorKey == timingA.key && it.type == BattleEventType.CHARGED_MOVE }?.turn
        out += result(
            "PVPOKE-TIMING-MISMATCH-DELAY", "move-timing", true, firstTimingCharge != null && firstTimingCharge > 1, firstTimingCharge != null && firstTimingCharge > 1,
            reference = ACTION_LOGIC_SOURCE, note = "3-turn vs 2-turn cadence should be eligible for timing optimization when survival/energy guards allow it."
        )

        val timingEqualA = build("timing-eq-a", tank, fast2, listOf(timingCharge), energy = 40)
        val timingEqualB = build("timing-eq-b", tank, fast2, listOf(timingCharge), energy = 0)
        val timingEqual = engine.simulate(timingEqualA, timingEqualB, BattleScenario(
            shieldsA = 0, shieldsB = 0,
            policyA = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.NEVER),
            policyB = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.NEVER),
            maxTurns = 2
        ))
        val firstEqualCharge = timingEqual.timeline.firstOrNull { it.actorKey == timingEqualA.key && it.type == BattleEventType.CHARGED_MOVE }?.turn
        out += result(
            "PVPOKE-TIMING-EQUAL-NO-DELAY", "move-timing", 1, firstEqualCharge ?: -1, firstEqualCharge == 1,
            reference = ACTION_LOGIC_SOURCE, note = "Equal Fast-Move durations disable timing optimization in pinned ActionLogic."
        )

        // V1.7 pre-DP ActionLogic: source-aligned survival urgency, energy-overflow timing guard,
        // immediate lethal branch and long-cycle simple-move selection.
        val urgentActorSpecies = species("urgent-actor", atk = 210, def = 120, hp = 90)
        val urgentOppSpecies = species("urgent-opp", atk = 300, def = 130, hp = 180)
        val urgentFast = PvpMove("URGENT_FAST_5", "Urgent Fast 5", type = "normal", power = 1, energyGain = 10, turns = 5)
        val oppNukeFast = PvpMove("OPP_NUKE_FAST", "Opp Nuke Fast", type = "normal", power = 45, energyGain = 8, turns = 1)
        val urgentCheap = PvpMove("URGENT_CHEAP", "Urgent Cheap", type = "normal", power = 30, energyCost = 35)
        val urgentBig = PvpMove("URGENT_BIG", "Urgent Big", type = "normal", power = 100, energyCost = 70)
        val urgentActor = build("urgent-a", urgentActorSpecies, urgentFast, listOf(urgentCheap, urgentBig), energy = 100)
        val urgentOpp = build("urgent-b", urgentOppSpecies, oppNukeFast, listOf(dummyCharge), energy = 0)
        val urgentSim = engine.simulate(urgentActor, urgentOpp, BattleScenario(
            shieldsA = 0, shieldsB = 0,
            policyA = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.NEVER),
            policyB = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.NEVER),
            maxTurns = 2
        ))
        val urgentDecision = urgentSim.decisionTrace.firstOrNull { it.actorKey == urgentActor.key && it.type == BattleDecisionType.CHARGED_SELECTED }
        out += result(
            "PVPOKE-ACTION-SURVIVAL-URGENCY", "action-policy", "URGENT_BIG", urgentDecision?.moveId ?: "none",
            urgentDecision?.moveId == "URGENT_BIG" && (urgentDecision.reason ?: "").contains("survival-urgency"),
            reference = ACTION_LOGIC_SOURCE,
            note = "Pinned pre-DP branch throws the highest available damage path when another Fast Move cannot be survived."
        )

        val overflowActor = build("overflow-a", tank, fast3, listOf(timingCharge), energy = 95)
        val overflowOpp = build("overflow-b", tank, fast2, listOf(timingCharge), energy = 0)
        val overflowSim = engine.simulate(overflowActor, overflowOpp, BattleScenario(
            shieldsA = 0, shieldsB = 0,
            policyA = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.NEVER),
            policyB = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.NEVER),
            maxTurns = 2
        ))
        val overflowFirstCharge = overflowSim.timeline.firstOrNull { it.actorKey == overflowActor.key && it.type == BattleEventType.CHARGED_MOVE }?.turn
        out += result(
            "PVPOKE-TIMING-ENERGY-CAP-NO-DELAY", "move-timing", 1, overflowFirstCharge ?: -1,
            overflowFirstCharge == 1, reference = ACTION_LOGIC_SOURCE,
            note = "Pinned optimizeMoveTiming does not insert another Fast Move when queued gain would exceed 100 energy."
        )

        val lethalActionMove = PvpMove("ACTION_LETHAL", "Action Lethal", type = "normal", power = 9999, energyCost = 40)
        val lethalActionActor = build("action-lethal-a", species("action-lethal-atk", atk = 260, def = 150, hp = 200), fast3, listOf(lethalActionMove), energy = 40)
        val lethalActionDef = build("action-lethal-b", species("action-lethal-def", atk = 150, def = 220, hp = 220), fast2, listOf(dummyCharge), energy = 0)
        val lethalActionSim = engine.simulate(lethalActionActor, lethalActionDef, BattleScenario(
            shieldsA = 0, shieldsB = 0,
            policyA = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.NEVER),
            policyB = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.NEVER),
            maxTurns = 1
        ))
        val lethalActionDecision = lethalActionSim.decisionTrace.firstOrNull { it.actorKey == lethalActionActor.key && it.type == BattleDecisionType.CHARGED_SELECTED }
        out += result(
            "PVPOKE-ACTION-IMMEDIATE-LETHAL", "action-policy", true,
            (lethalActionDecision?.reason ?: "").contains("immediate lethal"),
            (lethalActionDecision?.reason ?: "").contains("immediate lethal"), reference = ACTION_LOGIC_SOURCE
        )

        val longTank = species("long-tank", atk = 100, def = 350, hp = 650)
        val longCharge = PvpMove("LONG_MAIN", "Long Main", type = "normal", power = 20, energyCost = 40)
        val longActor = build("long-a", tank, fastOne, listOf(longCharge), energy = 40)
        val longDef = build("long-b", longTank, fastOne, listOf(dummyCharge), energy = 0)
        val longSim = engine.simulate(longActor, longDef, BattleScenario(
            shieldsA = 0, shieldsB = 0,
            policyA = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.NEVER),
            policyB = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.NEVER),
            maxTurns = 1
        ))
        val longDecision = longSim.decisionTrace.firstOrNull { it.actorKey == longActor.key && it.type == BattleDecisionType.CHARGED_SELECTED }
        out += result(
            "PVPOKE-ACTION-LONG-CYCLE", "action-policy", true,
            (longDecision?.reason ?: "").contains("long-cycle"),
            (longDecision?.reason ?: "").contains("long-cycle"), reference = ACTION_LOGIC_SOURCE
        )

        val selfDebuff = PvpMove("SELF_DEBUFF", "Self Debuff", type = "normal", power = 60, energyCost = 40, attackerAttackStageDelta = -1)
        val safeMove = PvpMove("SAFE_MOVE", "Safe Move", type = "normal", power = 50, energyCost = 40)
        val longChoiceActor = build("long-choice-a", tank, fastOne, listOf(selfDebuff, safeMove), energy = 40)
        val longChoiceSim = engine.simulate(longChoiceActor, longDef.copy(key = "long-choice-b"), BattleScenario(
            shieldsA = 0, shieldsB = 0,
            policyA = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.NEVER),
            policyB = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.NEVER),
            maxTurns = 1
        ))
        val longChoiceDecision = longChoiceSim.decisionTrace.firstOrNull { it.actorKey == longChoiceActor.key && it.type == BattleDecisionType.CHARGED_SELECTED }
        out += result(
            "PVPOKE-ACTION-LONG-CYCLE-AVOID-DEBUFF", "action-policy", "SAFE_MOVE", longChoiceDecision?.moveId ?: "none",
            longChoiceDecision?.moveId == "SAFE_MOVE" && (longChoiceDecision.reason ?: "").contains("long-cycle"),
            reference = ACTION_LOGIC_SOURCE,
            note = "Pinned long-cycle branch prefers a non-self-debuffing alternative when efficiency loss is below the source threshold."
        )

        // V1.8 pinned ActionLogic dynamic-programming core (~400-735). These fixtures target
        // the search itself rather than product heuristics built on top of it.
        val dpFast = PvpMove("DP_FAST", "DP Fast", type = "normal", power = 3, energyGain = 10, turns = 1)
        val dpCheap = PvpMove("DP_CHEAP", "DP Cheap", type = "normal", power = 40, energyCost = 35)
        val dpNuke = PvpMove("DP_NUKE", "DP Nuke", type = "normal", power = 100, energyCost = 55)
        val dpAttackerSp = species("dp-attacker", atk = 220, def = 180, hp = 220)
        val dpDefenderSp = species("dp-defender", atk = 180, def = 200, hp = 240)
        val dpAttacker = build("dp-a", dpAttackerSp, dpFast, listOf(dpCheap, dpNuke), energy = 55)
        val dpDefender = build("dp-b", dpDefenderSp, dpFast, listOf(dpCheap), energy = 0)

        val farmFast = PvpMove("DP_FARM_FAST", "DP Farm Fast", type = "normal", power = 999, energyGain = 5, turns = 1)
        val farmBuild = build("dp-farm-a", dpAttackerSp, farmFast, listOf(dpNuke), energy = 55)
        val farmPlan = PvpokeDpPlanner.plan(farmBuild, dpDefender, 0, 0, 55, 10, 0)
        out += result(
            "PVPOKE-DP-FARM-DOWN", "action-dp", true, farmPlan?.farmDown == true, farmPlan?.farmDown == true,
            reference = ACTION_LOGIC_SOURCE,
            note = "Pinned DP queue adds a farm-down terminal path and accepts it when it is the fastest guaranteed KO."
        )

        val shieldPlan = PvpokeDpPlanner.plan(dpAttacker, dpDefender, 0, 0, 55, 50, 1)
        val shieldMoves = shieldPlan?.moves?.map { it.moveId } ?: emptyList()
        out += result(
            "PVPOKE-DP-SHIELD-PATH", "action-dp", listOf("DP_CHEAP", "DP_NUKE"), shieldMoves,
            shieldMoves == listOf("DP_CHEAP", "DP_NUKE") && shieldPlan?.opponentShields == 0,
            reference = ACTION_LOGIC_SOURCE,
            note = "When the first Charged is guaranteed to be shielded for 1 damage, the DP searches the lower-turn shield-break + KO route."
        )

        val dpBuff = PvpMove(
            "DP_BUFF", "DP Buff", type = "normal", power = 20, energyCost = 35,
            attackerAttackStageDelta = 1, buffApplyChance = 1.0
        )
        val buffAttacker = build("dp-buff-a", species("dp-buff-atk", atk = 180, def = 180, hp = 220), dpFast, listOf(dpBuff, dpNuke), energy = 35)
        val buffPlan = PvpokeDpPlanner.plan(buffAttacker, dpDefender, 0, 0, 35, 100, 0)
        val buffMoves = buffPlan?.moves?.map { it.moveId } ?: emptyList()
        out += result(
            "PVPOKE-DP-GUARANTEED-BUFF", "action-dp", listOf("DP_BUFF", "DP_NUKE"), buffMoves,
            buffMoves == listOf("DP_BUFF", "DP_NUKE") && buffPlan?.attackBuffDelta == 1,
            reference = ACTION_LOGIC_SOURCE,
            note = "Pinned DP carries guaranteed outgoing-damage stage changes forward into later damage calculations."
        )

        val boundedPlan = PvpokeDpPlanner.plan(dpAttacker, dpDefender, 0, 0, 55, 200, 2)
        out += result(
            "PVPOKE-DP-STATE-CAP", "action-dp", true,
            boundedPlan != null && boundedPlan.statesVisited in 1..PvpokeDpPlanner.MAX_STATES,
            boundedPlan != null && boundedPlan.statesVisited in 1..PvpokeDpPlanner.MAX_STATES,
            reference = ACTION_LOGIC_SOURCE,
            note = "Planner retains the pinned 500-state safety ceiling and dominance pruning keeps this representative search bounded."
        )

        val dpEngineSim = engine.simulate(dpAttacker, dpDefender, BattleScenario(
            shieldsA = 0, shieldsB = 1,
            policyA = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.NEVER),
            policyB = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.PVPOKE_DEFAULT),
            maxTurns = 1
        ))
        val dpEngineDecision = dpEngineSim.decisionTrace.firstOrNull { it.actorKey == dpAttacker.key && it.type == BattleDecisionType.CHARGED_SELECTED }
        out += result(
            "PVPOKE-DP-ENGINE-INTEGRATION", "action-dp", true,
            (dpEngineDecision?.reason ?: "").contains("V1.9 DP"),
            (dpEngineDecision?.reason ?: "").contains("V1.9 DP"),
            reference = ACTION_LOGIC_SOURCE,
            note = "PVPOKE_BASELINE no longer falls back to an unconditional Main move when pre-DP branches are exhausted."
        )

        // V1.9 pinned ActionLogic post-plan (~736-920): bait build-up, reorder, defer and
        // farm-down boost behavior. These are source-rule fixtures, not same-revision result parity.
        val postFast = PvpMove("POST_FAST", "Post Fast", type = "normal", power = 3, energyGain = 10, turns = 1)
        val postCheap = PvpMove("POST_CHEAP", "Post Cheap", type = "normal", power = 30, energyCost = 35)
        val postNuke = PvpMove("POST_NUKE", "Post Nuke", type = "normal", power = 120, energyCost = 55)
        val postAtkSp = species("post-atk", atk = 220, def = 180, hp = 220)
        val postDefSp = species("post-def", atk = 180, def = 200, hp = 260)

        val baitBuildActor = build("post-bait-a", postAtkSp, postFast, listOf(postCheap, postNuke), energy = 35)
        val baitBuildDef = build("post-bait-b", postDefSp, postFast, listOf(postCheap), energy = 0)
        val baitBuildSim = engine.simulate(baitBuildActor, baitBuildDef, BattleScenario(
            shieldsA = 0, shieldsB = 1,
            policyA = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.NEVER, baitShields = true),
            policyB = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.PVPOKE_DEFAULT),
            maxTurns = 1
        ))
        val baitBuildDecision = baitBuildSim.decisionTrace.firstOrNull { it.actorKey == baitBuildActor.key && (it.type == BattleDecisionType.CHARGED_SELECTED || it.type == BattleDecisionType.FAST_SELECTED) }
        out += result(
            "PVPOKE-POST-BAIT-BUILDUP", "action-post-plan", "FAST", baitBuildDecision?.type?.name ?: "none",
            baitBuildDecision?.type == BattleDecisionType.FAST_SELECTED, reference = ACTION_LOGIC_SOURCE,
            note = "Pinned bait policy builds toward the more expensive/more efficient move before throwing the cheap move when shields are up."
        )

        val noBaitActor = build("post-nobait-a", postAtkSp, postFast, listOf(postCheap, postNuke), energy = 55)
        val noBaitSim = engine.simulate(noBaitActor, baitBuildDef.copy(key = "post-nobait-b"), BattleScenario(
            shieldsA = 0, shieldsB = 1,
            policyA = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.NEVER, baitShields = false),
            policyB = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.PVPOKE_DEFAULT),
            maxTurns = 1
        ))
        val noBaitDecision = noBaitSim.decisionTrace.firstOrNull { it.actorKey == noBaitActor.key && it.type == BattleDecisionType.CHARGED_SELECTED }
        out += result(
            "PVPOKE-POST-NO-BAIT-REORDER", "action-post-plan", "POST_NUKE", noBaitDecision?.moveId ?: "none",
            noBaitDecision?.moveId == "POST_NUKE", reference = ACTION_LOGIC_SOURCE,
            note = "When baiting is disabled, the pinned post-plan reorders a guaranteed plan to throw the highest immediate-damage move first."
        )

        val postDebuff = PvpMove("POST_DEBUFF", "Post Debuff", type = "normal", power = 90, energyCost = 45, attackerAttackStageDelta = -1)
        val postSafe = PvpMove("POST_SAFE", "Post Safe", type = "normal", power = 70, energyCost = 50)
        val postOppCharge = PvpMove("POST_OPP_CHARGE", "Post Opp Charge", type = "normal", power = 20, energyCost = 35)
        val postOppFast = PvpMove("POST_OPP_FAST", "Post Opp Fast", type = "normal", power = 2, energyGain = 8, turns = 2)

        val deferActor = build("post-defer-a", postAtkSp, PvpMove("POST_DEFER_FAST", "Post Defer Fast", type = "normal", power = 8, energyGain = 10, turns = 1), listOf(postDebuff, postSafe), energy = 90)
        val deferDef = build("post-defer-b", postDefSp, postOppFast, listOf(postOppCharge), energy = 35)
        val deferSim = engine.simulate(deferActor, deferDef, BattleScenario(
            shieldsA = 0, shieldsB = 0,
            policyA = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.NEVER, baitShields = false),
            policyB = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.NEVER),
            maxTurns = 1
        ))
        val deferDecision = deferSim.decisionTrace.firstOrNull { it.actorKey == deferActor.key && (it.type == BattleDecisionType.CHARGED_SELECTED || it.type == BattleDecisionType.FAST_SELECTED) }
        out += result(
            "PVPOKE-POST-DEFER-SELF-DEBUFF", "action-post-plan", "FAST", deferDecision?.type?.name ?: "none",
            deferDecision?.type == BattleDecisionType.FAST_SELECTED, reference = ACTION_LOGIC_SOURCE,
            note = "With no shields and a survivable opposing Charged ready, pinned ActionLogic defers a self-debuffing move."
        )

        val stackActor = deferActor.copy(key = "post-stack-a", initialEnergy = 45)
        val stackDef = deferDef.copy(key = "post-stack-b", initialEnergy = 0)
        val stackSim = engine.simulate(stackActor, stackDef, BattleScenario(
            shieldsA = 0, shieldsB = 0,
            policyA = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.NEVER, baitShields = false),
            policyB = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.NEVER),
            maxTurns = 1
        ))
        val stackDecision = stackSim.decisionTrace.firstOrNull { it.actorKey == stackActor.key && (it.type == BattleDecisionType.CHARGED_SELECTED || it.type == BattleDecisionType.FAST_SELECTED) }
        out += result(
            "PVPOKE-POST-STACK-SELF-DEBUFF", "action-post-plan", "FAST", stackDecision?.type?.name ?: "none",
            stackDecision?.type == BattleDecisionType.FAST_SELECTED, reference = ACTION_LOGIC_SOURCE,
            note = "Pinned post-plan stacks self-debuffing moves toward the largest multiple under 100 energy when it is safe to do so."
        )

        val postBoost = PvpMove("POST_BOOST", "Post Boost", type = "normal", power = 1, energyCost = 35, attackerAttackStageDelta = 1, buffApplyChance = 1.0)
        val farmBoostActor = build("post-boost-a", postAtkSp, PvpMove("POST_FARM_FAST", "Post Farm Fast", type = "normal", power = 999, energyGain = 10, turns = 1), listOf(postBoost), energy = 35)
        val farmBoostDef = build("post-boost-b", postDefSp, postFast, listOf(postOppCharge), energy = 0)
        val farmBoostSim = engine.simulate(farmBoostActor, farmBoostDef, BattleScenario(
            shieldsA = 0, shieldsB = 0,
            policyA = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.NEVER, baitShields = false),
            policyB = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.NEVER),
            maxTurns = 1
        ))
        val farmBoostDecision = farmBoostSim.decisionTrace.firstOrNull { it.actorKey == farmBoostActor.key && it.type == BattleDecisionType.CHARGED_SELECTED }
        out += result(
            "PVPOKE-POST-FARMDOWN-FORCE-BOOST", "action-post-plan", "POST_BOOST", farmBoostDecision?.moveId ?: "none",
            farmBoostDecision?.moveId == "POST_BOOST" && (farmBoostDecision.reason ?: "").contains("farm-down"),
            reference = ACTION_LOGIC_SOURCE, note = "A pure farm-down plan force-throws an available guaranteed self-buff move when getBoostMove()-style logic applies."
        )

        // CMP: higher unmodified Attack acts first. Exact ties are only an internal deterministic invariant.
        val highAtkSpecies = species("highatk", atk = 240, def = 150, hp = 200)
        val lowAtkSpecies = species("lowatk", atk = 160, def = 220, hp = 220)
        val cmpMove = PvpMove("CMP_CHARGE", "CMP Charge", type = "normal", power = 1, energyCost = 1)
        fun cmpSim(aSp: PokemonSpecies, bSp: PokemonSpecies, aKey: String, bKey: String) = engine.simulate(
            build(aKey, aSp, fastOne, listOf(cmpMove), energy = 1),
            build(bKey, bSp, fastOne, listOf(cmpMove), energy = 1),
            BattleScenario(shieldsA = 0, shieldsB = 0, maxTurns = 1)
        )
        val cmpHigh = cmpSim(highAtkSpecies, lowAtkSpecies, "cmp-high", "cmp-low")
        out += result("CMP-HIGHER-A", "cmp", "cmp-high", cmpHigh.timeline.first { it.type == BattleEventType.CHARGED_MOVE }.actorKey ?: "", cmpHigh.timeline.first { it.type == BattleEventType.CHARGED_MOVE }.actorKey == "cmp-high", reference = BATTLE_SOURCE)
        val cmpHighB = cmpSim(lowAtkSpecies, highAtkSpecies, "cmp-low2", "cmp-high2")
        out += result("CMP-HIGHER-B", "cmp", "cmp-high2", cmpHighB.timeline.first { it.type == BattleEventType.CHARGED_MOVE }.actorKey ?: "", cmpHighB.timeline.first { it.type == BattleEventType.CHARGED_MOVE }.actorKey == "cmp-high2", reference = BATTLE_SOURCE)
        val cmpTie = cmpSim(neutralA, neutralA, "cmp-tie-a", "cmp-tie-b")
        out += result(
            "CMP-TIE-MARKED", "cmp", true,
            cmpTie.timeline.any { it.type == BattleEventType.CMP && (it.note ?: "").contains("tie", ignoreCase = true) },
            cmpTie.timeline.any { it.type == BattleEventType.CMP && (it.note ?: "").contains("tie", ignoreCase = true) },
            critical = false, evidence = ParityEvidenceKind.INTERNAL_INVARIANT,
            note = "Tie ordering is deterministic locally for reproducibility; this fixture only requires that a true tie is disclosed."
        )

        // Floating Fast: a 5-turn move queued on T1 must resolve on T3 when a Charged Move occurs T2.
        val slowFast = PvpMove("FLOAT_5", "Float 5", type = "normal", power = 2, energyGain = 8, turns = 5)
        val quickFast = PvpMove("QUICK_1", "Quick 1", type = "normal", power = 1, energyGain = 40, turns = 1)
        val cheap = PvpMove("CHEAP_40", "Cheap 40", type = "normal", power = 1, energyCost = 40)
        val slow = build("float-slow", tank, slowFast, listOf(dummyCharge))
        val quick = build("float-quick", tank, quickFast, listOf(cheap))
        val floating = engine.simulate(slow, quick, BattleScenario(
            shieldsA = 0, shieldsB = 0,
            policyA = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.NEVER),
            policyB = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.NEVER),
            maxTurns = 8
        ))
        val chargedTurn = floating.timeline.first { it.actorKey == quick.key && it.type == BattleEventType.CHARGED_MOVE }.turn
        val floatHitTurn = floating.timeline.first { it.actorKey == slow.key && it.type == BattleEventType.FAST_MOVE }.turn
        out += result("FLOAT-FIRST-TURN-AFTER-CHARGE", "floating-fast", chargedTurn + 1, floatHitTurn, floatHitTurn == chargedTurn + 1, reference = BATTLE_SOURCE)
        val quickFastAfterCharge = floating.timeline.filter { it.actorKey == quick.key && it.type == BattleEventType.FAST_MOVE && it.turn > chargedTurn }.minOfOrNull { it.turn }
        out += result(
            "POST-CHARGE-NO-NEW-ACTION", "floating-fast", "no new Fast at T${chargedTurn + 1}", quickFastAfterCharge ?: "none",
            quickFastAfterCharge == null || quickFastAfterCharge > chargedTurn + 1,
            reference = BATTLE_SOURCE
        )

        // A queued floating Fast is invalid if its user is KO'd by the Charged Move before resolution.
        val lethalCharge = PvpMove("LETHAL", "Lethal", type = "normal", power = 9999, energyCost = 40)
        val quickLethal = build("float-killer", highAtkSpecies, quickFast, listOf(lethalCharge))
        val victim = build("float-victim", lowAtkSpecies, slowFast, listOf(dummyCharge))
        val killed = engine.simulate(victim, quickLethal, BattleScenario(
            shieldsA = 0, shieldsB = 0,
            policyA = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.NEVER),
            policyB = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.NEVER),
            maxTurns = 6
        ))
        val killTurn = killed.timeline.first { it.actorKey == quickLethal.key && it.type == BattleEventType.CHARGED_MOVE }.turn
        val victimFastAfterKill = killed.timeline.any { it.actorKey == victim.key && it.type == BattleEventType.FAST_MOVE && it.turn > killTurn }
        out += result("FLOAT-INVALID-AFTER-CHARGED-KO", "floating-fast", false, victimFastAfterKill, !victimFastAfterKill, reference = BATTLE_SOURCE)

        // PvPoke deterministic buff meter: 50% applies on activations 2,4,...; 25% on 4,8,...
        fun meterFixture(chance: Double, uses: Int, expectedApplies: Int, id: String) {
            val buffMove = PvpMove(
                "BUFF_$id", "Buff $id", type = "normal", power = 0, energyCost = 1,
                attackerAttackStageDelta = 1, buffApplyChance = chance
            )
            val actor = build("meter-a-$id", tank, fastOne, listOf(buffMove), energy = 100)
            val defender = build("meter-b-$id", tank, fastOne, listOf(dummyCharge), energy = 0)
            val maxTurns = uses * 2 - 1
            val sim = engine.simulate(actor, defender, BattleScenario(
                shieldsA = 0, shieldsB = 0,
                policyA = BattleSidePolicy(ChargeStrategy.CHEAPEST, ShieldStrategy.NEVER),
                policyB = BattleSidePolicy(ChargeStrategy.CHEAPEST, ShieldStrategy.NEVER),
                statEffectPolicy = StatEffectPolicy.PVPOKE_DETERMINISTIC_METER,
                maxTurns = maxTurns
            ))
            val actualApplies = sim.timeline.count { it.actorKey == actor.key && it.type == BattleEventType.STAT_CHANGE }
            out += result("BUFF-METER-$id", "chance-effects", expectedApplies, actualApplies, actualApplies == expectedApplies, reference = BATTLE_SOURCE)
        }
        meterFixture(0.50, 4, 2, "50")
        meterFixture(0.25, 8, 2, "25")

        // Same-turn Fast KO must remain a draw, independent of list processing order.
        val glass = species("glass", atk = 300, def = 50, hp = 50)
        val nukeFast = PvpMove("NUKE_FAST", "Nuke Fast", type = "normal", power = 999, energyGain = 1, turns = 1)
        val glassA = build("glass-a", glass, nukeFast, listOf(dummyCharge))
        val glassB = build("glass-b", glass, nukeFast, listOf(dummyCharge))
        val doubleKo = engine.simulate(glassA, glassB, BattleScenario(shieldsA = 0, shieldsB = 0, maxTurns = 2))
        out += result("FAST-DOUBLE-KO", "simultaneous", BattleOutcome.DRAW, doubleKo.outcomeForA, doubleKo.outcomeForA == BattleOutcome.DRAW, reference = BATTLE_SOURCE)

        // Battle Rating invariant: paired ratings are 0..1000, not a probability.
        out += result("RATING-RANGE-A", "battle-rating", true, doubleKo.battleRatingA in 0..1000, doubleKo.battleRatingA in 0..1000, critical = false, evidence = ParityEvidenceKind.INTERNAL_INVARIANT)
        out += result("RATING-RANGE-B", "battle-rating", true, doubleKo.battleRatingB in 0..1000, doubleKo.battleRatingB in 0..1000, critical = false, evidence = ParityEvidenceKind.INTERNAL_INVARIANT)

        // Historical external-result fixtures captured from an older PvPoke multi-battle snapshot.
        // They are useful regression clues but MUST NOT satisfy the current pinned-reference gate.
        // Subject spread is reconstructed from the displayed Azumarill stats/rank (4/15/13 @ L43).
        // Opponents use PvPoke's documented default: rank-64 spread with IV floor 5.
        val externalUrl = PvPokeReferencePin.HISTORICAL_RESULT_URL
        val azumarill = PokemonSpecies(dex = 184, name = "Azumarill", speciesId = "azumarill", types = listOf("water", "fairy"), baseAttack = 112, baseDefense = 152, baseStamina = 225)
        val feraligatr = PokemonSpecies(dex = 160, name = "Feraligatr", speciesId = "feraligatr", types = listOf("water"), baseAttack = 205, baseDefense = 188, baseStamina = 198)
        val walrein = PokemonSpecies(dex = 365, name = "Walrein", speciesId = "walrein", types = listOf("ice", "water"), baseAttack = 182, baseDefense = 176, baseStamina = 242)
        val bubble = PvpMove("BUBBLE", "Bubble", type = "water", power = 8, energyGain = 11, turns = 3)
        val shadowClaw = PvpMove("SHADOW_CLAW", "Shadow Claw", type = "ghost", power = 6, energyGain = 8, turns = 2)
        val powderSnow = PvpMove("POWDER_SNOW", "Powder Snow", type = "ice", power = 5, energyGain = 8, turns = 2)
        val iceBeam = PvpMove("ICE_BEAM", "Ice Beam", type = "ice", power = 90, energyCost = 55)
        val hydroPump = PvpMove("HYDRO_PUMP", "Hydro Pump", type = "water", power = 130, energyCost = 75)
        val hydroCannon = PvpMove("HYDRO_CANNON", "Hydro Cannon", type = "water", power = 80, energyCost = 40)
        val icicleSpear = PvpMove("ICICLE_SPEAR", "Icicle Spear", type = "ice", power = 65, energyCost = 40)
        val earthquake = PvpMove("EARTHQUAKE", "Earthquake", type = "ground", power = 110, energyCost = 65)
        val azuBuild = BattleBuild("external-azu", azumarill, IvSpread(4, 15, 13), 43.0, bubble, listOf(iceBeam, hydroPump))
        fun default64(species: PokemonSpecies): PvPRankEntry = PvPRanker.rank(species, League.GREAT, RankSettings(ivFloor = 5))[63]
        val fera64 = default64(feraligatr)
        val wal64 = default64(walrein)
        val feraBuild = BattleBuild("external-fera", feraligatr, fera64.iv, fera64.level, shadowClaw, listOf(hydroCannon, iceBeam))
        val walBuild = BattleBuild("external-wal", walrein, wal64.iv, wal64.level, powderSnow, listOf(icicleSpear, earthquake))
        val externalScenario = BattleScenario(
            shieldsA = 1, shieldsB = 1,
            policyA = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.PVPOKE_DEFAULT),
            policyB = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.PVPOKE_DEFAULT),
            statEffectPolicy = StatEffectPolicy.PVPOKE_DETERMINISTIC_METER
        )
        fun externalRating(id: String, expectedRating: Int, opponent: BattleBuild, note: String) {
            val sim = engine.simulate(azuBuild, opponent, externalScenario)
            out += result(
                id = id, family = "external-result-parity", expected = expectedRating, actual = sim.battleRatingA,
                pass = sim.battleRatingA == expectedRating, critical = false, evidence = ParityEvidenceKind.HISTORICAL_EXTERNAL_RESULT,
                reference = externalUrl,
                note = "$note; historical PvPoke ${PvPokeReferencePin.HISTORICAL_RESULT_VERSION} fixture, not the pinned ${PvPokeReferencePin.SITE_VERSION}/${PvPokeReferencePin.SHORT_SHA} baseline. Subject=Azumarill 4/15/13 L43, 1-1 shields."
            )
        }
        externalRating("EXTERNAL-AZU-FERALIGATR", 348, feraBuild, "PvPoke lists Feraligatr at Battle Rating 348")
        externalRating("EXTERNAL-AZU-FERALIGATR-SHADOW", 368, feraBuild.copy(key = "external-fera-shadow", isShadow = true), "PvPoke lists Shadow Feraligatr at Battle Rating 368")
        externalRating("EXTERNAL-AZU-WALREIN", 366, walBuild, "PvPoke lists Walrein at Battle Rating 366")

        // Same-revision external baseline. V1.13 continues to use the exact-current pinned catalog,
        // never the historical Walrein move snapshot above. Until an exact-pin export is imported,
        // absence of evidence remains BLOCKED.
        PinnedBattleCaseCatalog.validatePinnedData()
        PinnedBattleCaseCatalog.baselineCases.forEach { c ->
            val captured = PinnedExternalBaseline.get(c.fixtureId)
            val label = "${c.a.species.name} vs ${if (c.b.isShadow) "Shadow " else ""}${c.b.species.name}"
            if (captured == null) {
                out += blocked(
                    id = c.fixtureId,
                    family = "pinned-external-result-parity",
                    expected = "PvPoke ${PvPokeReferencePin.SITE_VERSION}/${PvPokeReferencePin.SHORT_SHA} output",
                    actual = "NOT_CAPTURED",
                    critical = true,
                    evidence = ParityEvidenceKind.EXTERNAL_RESULT,
                    reference = PvPokeReferencePin.ACTION_LOGIC_URL,
                    note = "$label must be generated from the pinned upstream revision before it can open the recommendation gate."
                )
            } else {
                check(captured.sourceCommitSha == PvPokeReferencePin.COMMIT_SHA)
                check(captured.sourceSiteVersion == PvPokeReferencePin.SITE_VERSION)
                val sim = engine.simulate(c.a, c.b, c.scenario())
                out += result(
                    id = c.fixtureId, family = "pinned-external-result-parity", expected = captured.battleRatingA, actual = sim.battleRatingA,
                    pass = sim.battleRatingA == captured.battleRatingA, critical = true, evidence = ParityEvidenceKind.EXTERNAL_RESULT,
                    reference = PvPokeReferencePin.ACTION_LOGIC_URL,
                    note = "$label exact-pin export; upstream B=${captured.battleRatingB}, turns=${captured.turns}, trace=${captured.traceSha256.take(12)}."
                )
            }
        }

        return ParityFixtureMatrix(out)
    }
}
