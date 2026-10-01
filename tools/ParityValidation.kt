import com.rui.pvpgo.domain.BattleOutcome
import com.rui.pvpgo.engine.*
import kotlin.math.abs
import kotlin.math.floor

private fun species(id: String, atk: Int, def: Int, hp: Int, types: List<String>) = PokemonSpecies(
    dex = 1, name = id, speciesId = id, types = types,
    baseAttack = atk, baseDefense = def, baseStamina = hp
)

fun main() {
    // Product gate may open through exact-pin or the independently certified F44B fallback.
    val parity = BattleParityGate.current()
    check(parity.matchupAdviceGateOpen)
    check(parity.verifiedCount >= 9)
    check(parity.capabilities.any { it.id == "action-pre-dp" && it.status == ParityStatus.VERIFIED })
    check(parity.capabilities.any { it.id == "action-policy" && it.status == ParityStatus.PARTIAL })
    check(parity.capabilities.any { it.id == "floating-fast" && it.status == ParityStatus.VERIFIED })
    check(parity.capabilities.any { it.id == "chance-effects" && it.status == ParityStatus.VERIFIED })

    // PvP stage multipliers.
    val expectedStages = mapOf(-4 to 0.5, -3 to 4.0/7.0, -2 to 2.0/3.0, -1 to 0.8, 0 to 1.0,
        1 to 1.25, 2 to 1.5, 3 to 1.75, 4 to 2.0)
    expectedStages.forEach { (stage, expected) ->
        check(abs(BattleDamage.stageMultiplier(stage) - expected) < 1e-12)
    }

    val water = species("watermon", 210, 170, 190, listOf("water"))
    val ground = species("groundmon", 180, 190, 210, listOf("ground"))
    val fastWater = PvpMove("FAST_WATER", "Fast Water", type="water", power=5, energyGain=8, turns=2)
    val fastGround = PvpMove("FAST_GROUND", "Fast Ground", type="ground", power=4, energyGain=9, turns=2)
    val efficient = PvpMove("EFFICIENT", "Efficient", type="water", power=90, energyCost=45)
    val bait = PvpMove("BAIT", "Bait", type="water", power=45, energyCost=35)
    val groundCharge = PvpMove("GROUND_CHARGE", "Ground Charge", type="ground", power=80, energyCost=45)

    val a = BattleBuild("a", water, IvSpread(0,15,15), 30.0, fastWater, listOf(efficient, bait), initialEnergy=45)
    val b = BattleBuild("b", ground, IvSpread(0,15,15), 30.0, fastGround, listOf(groundCharge), initialEnergy=0)

    // Independent damage reconstruction using the published floor(...)+1 structure.
    val atk = BattleDamage.effectiveAttack(a.stats.attack, 0, false)
    val def = BattleDamage.effectiveDefense(b.stats.defense, 0, false)
    val eff = PvpTypeChart.effectiveness("water", listOf("ground"))
    val expectedDamage = floor(efficient.power * BattleDamage.STAB * (atk/def) * eff * 0.5 * BattleDamage.TRAINER_BATTLE_BONUS).toInt() + 1
    check(BattleDamage.damage(a, 0, b, 0, efficient) == expectedDamage)

    // CMP must ignore stat stages and Shadow damage multipliers.
    val aShadow = a.copy(isShadow=true, initialAttackStage=4)
    check(abs(BattleDamage.cmpAttack(aShadow) - aShadow.stats.attack) < 1e-12)

    // PVPOKE_BASELINE: validate source-aligned decision behavior under a representative affordable-move state.
    val engine = BattleEngine()
    val mainNow = engine.simulate(a, b, BattleScenario(
        shieldsA=0, shieldsB=0,
        policyA=BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.ALWAYS),
        policyB=BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.ALWAYS)
    ))
    val firstA = mainNow.timeline.first { it.actorKey == "a" && it.type == BattleEventType.CHARGED_MOVE }
    check(firstA.moveId == "EFFICIENT")

    // With only enough energy for Secondary and an opposing shield, baseline uses shield pressure.
    val a35 = a.copy(initialEnergy=35)
    val shieldPressure = engine.simulate(a35, b, BattleScenario(
        shieldsA=0, shieldsB=1,
        policyA=BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.ALWAYS),
        policyB=BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.ALWAYS)
    ))
    val firstA35 = shieldPressure.timeline.first { it.actorKey == "a" && it.type == BattleEventType.CHARGED_MOVE }
    check(firstA35.moveId == "BAIT")
    check(firstA35.shielded && firstA35.damage == 1)

    // Floating fast move: 5-turn move starts; opponent reaches a charged on turn 2; slow fast floats on turn 3.
    val slowFast = PvpMove("SLOW_FAST", "Slow Fast", type="water", power=10, energyGain=8, turns=5)
    val quickFast = PvpMove("QUICK_FAST", "Quick Fast", type="ground", power=1, energyGain=40, turns=1)
    val cheapCharge = PvpMove("CHEAP_CHARGE", "Cheap Charge", type="ground", power=20, energyCost=40)
    val slow = BattleBuild("slow", water, IvSpread(0,15,15), 30.0, slowFast, listOf(efficient), initialEnergy=0)
    val quick = BattleBuild("quick", ground, IvSpread(0,15,15), 30.0, quickFast, listOf(cheapCharge), initialEnergy=0)
    val floating = engine.simulate(slow, quick, BattleScenario(
        shieldsA=0, shieldsB=0,
        policyA=BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.NEVER),
        policyB=BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.NEVER),
        maxTurns=20
    ))
    val quickChargedTurn = floating.timeline.first { it.actorKey == "quick" && it.type == BattleEventType.CHARGED_MOVE }.turn
    val slowFastHit = floating.timeline.first { it.actorKey == "slow" && it.type == BattleEventType.FAST_MOVE }
    check(slowFastHit.turn == quickChargedTurn + 1)

    // PvPoke deterministic buff meter: a 50% effect applies on the second and fourth activation.
    val meterMove = PvpMove("METER", "Meter", type="water", power=0, energyCost=1, attackerAttackStageDelta=1, buffApplyChance=0.5)
    val meterA = BattleBuild("meter-a", water, IvSpread(0,15,15), 30.0, fastWater, listOf(meterMove), initialEnergy=100)
    val meterB = BattleBuild("meter-b", ground, IvSpread(0,15,15), 30.0, fastGround, listOf(groundCharge), initialEnergy=0)
    val meterSim = engine.simulate(meterA, meterB, BattleScenario(
        shieldsA=0, shieldsB=0,
        policyA=BattleSidePolicy(ChargeStrategy.CHEAPEST, ShieldStrategy.NEVER),
        policyB=BattleSidePolicy(ChargeStrategy.CHEAPEST, ShieldStrategy.NEVER),
        statEffectPolicy=StatEffectPolicy.PVPOKE_DETERMINISTIC_METER,
        maxTurns=7
    ))
    check(meterSim.timeline.count { it.actorKey == "meter-a" && it.type == BattleEventType.STAT_CHANGE } == 2)

    // Battle Rating is deterministic and outcome threshold remains 500-centered, not a probability.
    check(mainNow.battleRatingA in 0..1000 && mainNow.battleRatingB in 0..1000)
    if (mainNow.outcomeForA == BattleOutcome.WIN) check(mainNow.battleRatingA >= 500)

    println("PARITY_VALIDATION_OK")
    println("verified=${parity.verifiedCount} partial=${parity.partialCount} pending=${parity.pendingCount} gate=${parity.matchupAdviceGateOpen}")
    println("baseline-main=${firstA.moveId} baseline-shield=${firstA35.moveId} floatingFastTurn=${slowFastHit.turn} afterChargeTurn=$quickChargedTurn")
}
