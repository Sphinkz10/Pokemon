import com.rui.pvpgo.domain.BattleOutcome
import com.rui.pvpgo.engine.*

private fun species(
    id: String,
    atk: Int,
    def: Int,
    hp: Int,
    types: List<String>
) = PokemonSpecies(
    dex = 1,
    name = id,
    speciesId = id,
    types = types,
    baseAttack = atk,
    baseDefense = def,
    baseStamina = hp
)

fun main() {
    check(kotlin.math.abs(PvpTypeChart.effectiveness("fighting", listOf("ghost")) - 0.390625) < 1e-9)
    check(PvpTypeChart.effectiveness("water", listOf("ground")) > 1.59)
    check(PvpTypeChart.effectiveness("electric", listOf("water", "flying")) > 2.55)
    check(BattleDamage.stageMultiplier(-4) == 0.5)
    check(BattleDamage.stageMultiplier(4) == 2.0)

    val alpha = species("alpha", 210, 170, 190, listOf("water"))
    val beta = species("beta", 180, 190, 210, listOf("ground"))

    val fastA = PvpMove("FAST_A", "Fast A", type = "water", power = 3, energyGain = 10, turns = 2)
    val fastB = PvpMove("FAST_B", "Fast B", type = "ground", power = 4, energyGain = 9, turns = 2)
    val cheap = PvpMove("CHEAP", "Cheap", type = "water", power = 45, energyCost = 35)
    val nuke = PvpMove("NUKE", "Nuke", type = "water", power = 100, energyCost = 50)
    val debuff = PvpMove(
        "DEBUFF", "Debuff", type = "water", power = 20, energyCost = 35,
        opponentDefenseStageDelta = -1, buffApplyChance = 1.0
    )
    val betaCharge = PvpMove("BETA_CHARGE", "Beta Charge", type = "ground", power = 80, energyCost = 45)

    val a = BattleBuild(
        key = "a", species = alpha, iv = IvSpread(0, 15, 15), level = 30.0,
        fastMove = fastA, chargedMoves = listOf(cheap, nuke), initialEnergy = 50
    )
    val b = BattleBuild(
        key = "b", species = beta, iv = IvSpread(0, 15, 15), level = 30.0,
        fastMove = fastB, chargedMoves = listOf(betaCharge), initialEnergy = 45
    )

    val engine = BattleEngine()
    val oneShield = engine.simulate(
        a, b,
        BattleScenario(
            shieldsA = 1, shieldsB = 1,
            policyA = BattleSidePolicy(ChargeStrategy.MAX_DAMAGE, ShieldStrategy.ALWAYS),
            policyB = BattleSidePolicy(ChargeStrategy.MAX_DAMAGE, ShieldStrategy.ALWAYS)
        )
    )
    check(oneShield.timeline.any { it.type == BattleEventType.CMP })
    check(oneShield.timeline.any { it.type == BattleEventType.SHIELD })
    check(oneShield.timeline.filter { it.type == BattleEventType.CHARGED_MOVE && it.shielded }.all { it.damage == 1 })
    check(oneShield.finalA.energy in 0..100 && oneShield.finalB.energy in 0..100)
    check(oneShield.battleRatingA in 0..1000 && oneShield.battleRatingB in 0..1000)

    val bait = engine.simulate(
        a, b,
        BattleScenario(
            shieldsA = 0, shieldsB = 1,
            policyA = BattleSidePolicy(ChargeStrategy.BAIT_IF_SHIELDED, ShieldStrategy.NEVER),
            policyB = BattleSidePolicy(ChargeStrategy.MAX_DAMAGE, ShieldStrategy.ALWAYS)
        )
    )
    val firstACharge = bait.timeline.first { it.actorKey == "a" && it.type == BattleEventType.CHARGED_MOVE }
    check(firstACharge.moveId == "CHEAP")

    val aDebuff = a.copy(chargedMoves = listOf(debuff), initialEnergy = 35)
    val debuffResult = engine.simulate(
        aDebuff, b.copy(initialEnergy = 0),
        BattleScenario(
            shieldsA = 0, shieldsB = 0,
            statEffectPolicy = StatEffectPolicy.GUARANTEED_ONLY,
            policyA = BattleSidePolicy(ChargeStrategy.MAX_DAMAGE, ShieldStrategy.NEVER),
            policyB = BattleSidePolicy(ChargeStrategy.MAX_DAMAGE, ShieldStrategy.NEVER)
        )
    )
    check(debuffResult.timeline.any { it.type == BattleEventType.STAT_CHANGE })

    val repeat = engine.simulate(
        a, b,
        BattleScenario(shieldsA = 1, shieldsB = 1, randomSeed = 42)
    )
    val repeat2 = engine.simulate(
        a, b,
        BattleScenario(shieldsA = 1, shieldsB = 1, randomSeed = 42)
    )
    check(repeat.outcomeForA == repeat2.outcomeForA)
    check(repeat.battleRatingA == repeat2.battleRatingA)
    check(repeat.timeline == repeat2.timeline)


    val glass = species("glass", 300, 50, 50, listOf("normal"))
    val oneTurnKo = PvpMove("ONE_TURN_KO", "One Turn KO", type = "normal", power = 250, energyGain = 1, turns = 1)
    val fillerCharge = PvpMove("FILLER", "Filler", type = "normal", power = 10, energyCost = 100)
    val g1 = BattleBuild("g1", glass, IvSpread(0,0,0), 20.0, oneTurnKo, listOf(fillerCharge))
    val g2 = BattleBuild("g2", glass, IvSpread(0,0,0), 20.0, oneTurnKo, listOf(fillerCharge))
    val simultaneous = engine.simulate(g1, g2, BattleScenario(shieldsA = 0, shieldsB = 0))
    check(simultaneous.outcomeForA == BattleOutcome.DRAW) { "Fast Move ties must not depend on processing order" }
    check(simultaneous.finalA.hp == 0 && simultaneous.finalB.hp == 0)

    val batch = MatchupBatchAnalyzer.analyze(engine, a, listOf(b), BattleScenario(shieldsA = 0, shieldsB = 0))
    check(batch.size == 1)
    check(batch.first().outcome in BattleOutcome.entries)

    println("BATTLE_ENGINE_OK")
    println("1S outcome=${oneShield.outcomeForA} rating=${oneShield.battleRatingA}-${oneShield.battleRatingB} turns=${oneShield.turns}")
    println("Bait first charged=${firstACharge.moveId}")
    println("Batch=${batch.first()}")
}
