import com.rui.pvpgo.engine.*
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

/**
 * F91/V1.29 independent STANDARD mechanics oracle.
 *
 * Deliberately avoids BattleDamage, PokemonMath, CpMultipliers and PvpTypeChart when computing
 * expected values. Reference constants/formulas are duplicated from external mechanics references
 * recorded in INDEPENDENT_REFERENCE_VALIDATION.md, then compared with the production contracts.
 */
private object StandardOracleV118 {
    const val BONUS = 1.2999999523162842
    const val STAB = 1.2000000476837158
    const val SUPER = 1.600000023841858
    const val RESIST = 0.625
    const val IMMUNITY = 0.390625
    const val SHADOW_ATK = 1.2
    const val SHADOW_DEF = 0.83333331

    private val cpm = mapOf(
        1.0 to 0.0939999968,
        20.0 to 0.5974000096,
        40.0 to 0.7903000116,
        50.0 to 0.8403000236,
        51.0 to 0.845300018787384
    )

    fun cpm(level: Double): Double = cpm[level] ?: error("No independent CPM fixture for L$level")

    fun stage(stage: Int): Double = when (stage.coerceIn(-4, 4)) {
        -4 -> 0.5
        -3 -> 4.0 / 7.0
        -2 -> 4.0 / 6.0
        -1 -> 0.8
        0 -> 1.0
        1 -> 1.25
        2 -> 1.5
        3 -> 1.75
        4 -> 2.0
        else -> error("unreachable")
    }

    fun stats(species: PokemonSpecies, iv: IvSpread, level: Double): Triple<Double, Double, Int> {
        val m = cpm(level)
        return Triple(
            (species.baseAttack + iv.attack) * m,
            (species.baseDefense + iv.defense) * m,
            max(10, floor((species.baseStamina + iv.stamina) * m).toInt())
        )
    }

    fun cp(species: PokemonSpecies, iv: IvSpread, level: Double): Int {
        val m = cpm(level)
        val raw = (species.baseAttack + iv.attack) *
            sqrt((species.baseDefense + iv.defense).toDouble()) *
            sqrt((species.baseStamina + iv.stamina).toDouble()) *
            m * m / 10.0
        return max(10, floor(raw).toInt())
    }

    fun effectiveness(moveType: String, defenderTypes: List<String>): Double {
        fun one(attack: String, defense: String): Double = when (attack.lowercase() to defense.lowercase()) {
            "fire" to "grass", "fire" to "steel", "fire" to "bug", "fire" to "ice" -> SUPER
            "fire" to "fire", "fire" to "water", "fire" to "rock", "fire" to "dragon" -> RESIST
            "water" to "fire", "water" to "ground", "water" to "rock" -> SUPER
            "water" to "water", "water" to "grass", "water" to "dragon" -> RESIST
            "ghost" to "ghost", "ghost" to "psychic" -> SUPER
            "ghost" to "dark" -> RESIST
            "ghost" to "normal" -> IMMUNITY
            "normal" to "rock", "normal" to "steel" -> RESIST
            "normal" to "ghost" -> IMMUNITY
            "electric" to "water", "electric" to "flying" -> SUPER
            "electric" to "electric", "electric" to "grass", "electric" to "dragon" -> RESIST
            "electric" to "ground" -> IMMUNITY
            "ground" to "electric", "ground" to "fire", "ground" to "poison", "ground" to "rock", "ground" to "steel" -> SUPER
            "ground" to "bug", "ground" to "grass" -> RESIST
            "ground" to "flying" -> IMMUNITY
            else -> 1.0
        }
        return defenderTypes.fold(1.0) { acc, type -> acc * one(moveType, type) }
    }

    fun damage(
        attacker: PokemonSpecies,
        attackerIv: IvSpread,
        attackerLevel: Double,
        attackerStage: Int,
        attackerShadow: Boolean,
        defender: PokemonSpecies,
        defenderIv: IvSpread,
        defenderLevel: Double,
        defenderStage: Int,
        defenderShadow: Boolean,
        moveType: String,
        movePower: Int,
        hasStab: Boolean,
        chargeMultiplier: Double = 1.0
    ): Int {
        val atk = stats(attacker, attackerIv, attackerLevel).first * stage(attackerStage) * if (attackerShadow) SHADOW_ATK else 1.0
        val def = stats(defender, defenderIv, defenderLevel).second * stage(defenderStage) * if (defenderShadow) SHADOW_DEF else 1.0
        val stab = if (hasStab) STAB else 1.0
        val eff = effectiveness(moveType, defender.types)
        return floor(movePower * stab * (atk / def) * eff * chargeMultiplier * 0.5 * BONUS).toInt() + 1
    }
}


private object FastOnlyBattleOracleV118 {
    data class Side(
        val maxHp: Int,
        var hp: Int,
        val damage: Int,
        val turns: Int,
        var nextStart: Int = 1,
        val due: MutableList<Int> = mutableListOf()
    )
    data class Result(val turns: Int, val hpA: Int, val hpB: Int, val ratingA: Int, val ratingB: Int, val outcome: String)

    fun simulate(maxHpA: Int, damageA: Int, turnsA: Int, maxHpB: Int, damageB: Int, turnsB: Int, maxTurns: Int = 480): Result {
        val a = Side(maxHpA, maxHpA, damageA, turnsA)
        val b = Side(maxHpB, maxHpB, damageB, turnsB)
        var turn = 0
        while (turn < maxTurns && a.hp > 0 && b.hp > 0) {
            turn++
            if (turn == a.nextStart) {
                a.due += turn + a.turns - 1
                a.nextStart = turn + a.turns
            }
            if (turn == b.nextStart) {
                b.due += turn + b.turns - 1
                b.nextStart = turn + b.turns
            }
            val hitA = a.due.remove(turn)
            val hitB = b.due.remove(turn)
            // Same-turn Fast damage is simultaneous.
            if (hitA) b.hp = (b.hp - a.damage).coerceAtLeast(0)
            if (hitB) a.hp = (a.hp - b.damage).coerceAtLeast(0)
        }
        fun rating(self: Side, opp: Side): Int = floor(
            500.0 * ((opp.maxHp - opp.hp).toDouble() / opp.maxHp) +
                500.0 * (self.hp.toDouble() / self.maxHp)
        ).toInt().coerceIn(0, 1000)
        val ra = rating(a, b)
        val rb = rating(b, a)
        val outcome = when {
            a.hp > 0 && b.hp <= 0 -> "WIN"
            b.hp > 0 && a.hp <= 0 -> "LOSS"
            a.hp <= 0 && b.hp <= 0 -> "DRAW"
            ra > rb -> "WIN"
            rb > ra -> "LOSS"
            else -> "DRAW"
        }
        return Result(turn, a.hp, b.hp, ra, rb, outcome)
    }
}

private fun sp(id: String, dex: Int, atk: Int, def: Int, sta: Int, vararg types: String) = PokemonSpecies(
    dex = dex, name = id, speciesId = id, types = types.toList(), baseAttack = atk, baseDefense = def, baseStamina = sta
)

private fun build(
    key: String,
    species: PokemonSpecies,
    iv: IvSpread,
    level: Double,
    fast: PvpMove,
    charged: List<PvpMove>,
    shadow: Boolean = false,
    energy: Int = 0,
    attackStage: Int = 0,
    defenseStage: Int = 0
) = BattleBuild(
    key = key,
    species = species,
    iv = iv,
    level = level,
    fastMove = fast,
    chargedMoves = charged,
    isShadow = shadow,
    initialEnergy = energy,
    initialAttackStage = attackStage,
    initialDefenseStage = defenseStage
)

fun main() {
    var assertions = 0
    fun verify(condition: Boolean, message: String) {
        check(condition) { message }
        assertions++
    }
    fun close(actual: Double, expected: Double, eps: Double = 1e-10, label: String) {
        verify(abs(actual - expected) <= eps, "$label expected=$expected actual=$actual")
    }

    // CPM + CP/stat formula fixtures from an external CPM table.
    listOf(1.0, 20.0, 40.0, 50.0, 51.0).forEach { level ->
        close(CpMultipliers.at(level), StandardOracleV118.cpm(level), 1e-12, "CPM L$level")
    }

    val charizard = sp("charizard", 6, 223, 173, 186, "fire", "flying")
    val charIv = IvSpread(0, 15, 13)
    verify(PokemonMath.cp(charizard, charIv, 19.5) == 1500, "Known Charizard GL fixture 0/15/13 L19.5 must be 1500 CP")
    val testSpecies = sp("formula_test", 9001, 200, 180, 190, "fire")
    val testIv = IvSpread(10, 11, 12)
    for (level in listOf(20.0, 40.0, 50.0, 51.0)) {
        val expected = StandardOracleV118.stats(testSpecies, testIv, level)
        val actual = PokemonMath.battleStats(testSpecies, testIv, level)
        close(actual.attack, expected.first, 1e-10, "Attack stat L$level")
        close(actual.defense, expected.second, 1e-10, "Defense stat L$level")
        verify(actual.hp == expected.third, "HP L$level expected=${expected.third} actual=${actual.hp}")
        verify(PokemonMath.cp(testSpecies, testIv, level) == StandardOracleV118.cp(testSpecies, testIv, level), "CP formula L$level")
    }

    // Full stage table, including clamps.
    for (stage in -6..6) {
        close(BattleDamage.stageMultiplier(stage), StandardOracleV118.stage(stage), 1e-12, "Stage $stage")
    }

    // Type multiplier samples: neutral, SE, resisted, immunity/double-resist, double weakness/resist.
    close(PvpTypeChart.effectiveness("fire", listOf("grass")), StandardOracleV118.SUPER, 1e-12, "Fire>Grass")
    close(PvpTypeChart.effectiveness("fire", listOf("water")), StandardOracleV118.RESIST, 1e-12, "Fire>Water")
    close(PvpTypeChart.effectiveness("normal", listOf("ghost")), StandardOracleV118.IMMUNITY, 1e-12, "Normal>Ghost")
    close(PvpTypeChart.effectiveness("fire", listOf("grass", "steel")), StandardOracleV118.SUPER * StandardOracleV118.SUPER, 1e-10, "Fire>Grass/Steel")
    close(PvpTypeChart.effectiveness("fire", listOf("water", "dragon")), StandardOracleV118.RESIST * StandardOracleV118.RESIST, 1e-12, "Fire>Water/Dragon")
    close(PvpTypeChart.effectiveness("electric", listOf("ground")), StandardOracleV118.IMMUNITY, 1e-12, "Electric>Ground")

    // Damage formula matrix independent of production helpers.
    val attacker = sp("attacker", 9002, 210, 150, 180, "fire")
    val grassSteel = sp("grass_steel", 9003, 145, 190, 210, "grass", "steel")
    val waterDragon = sp("water_dragon", 9004, 160, 185, 210, "water", "dragon")
    val ghost = sp("ghost_target", 9005, 160, 185, 210, "ghost")
    val ivA = IvSpread(7, 12, 14)
    val ivD = IvSpread(5, 13, 15)
    val zeroFast = PvpMove("ZERO_FAST", "Zero Fast", type = "normal", power = 0, energyGain = 1, turns = 1)
    val dummyCharge = PvpMove("DUMMY_CHARGE", "Dummy", type = "normal", power = 1, energyCost = 100)
    val fireMove = PvpMove("FIRE_TEST", "Fire Test", type = "fire", power = 60, energyCost = 40)
    val normalMove = PvpMove("NORMAL_TEST", "Normal Test", type = "normal", power = 60, energyCost = 40)

    fun prodDamage(
        defenderSpecies: PokemonSpecies,
        move: PvpMove,
        attackStage: Int = 0,
        defenseStage: Int = 0,
        attackerShadow: Boolean = false,
        defenderShadow: Boolean = false
    ): Int {
        val a = build("a", attacker, ivA, 20.0, zeroFast, listOf(dummyCharge), shadow = attackerShadow)
        val d = build("d", defenderSpecies, ivD, 20.0, zeroFast, listOf(dummyCharge), shadow = defenderShadow)
        return BattleDamage.damage(a, attackStage, d, defenseStage, move)
    }

    fun oracleDamage(
        defenderSpecies: PokemonSpecies,
        move: PvpMove,
        attackStage: Int = 0,
        defenseStage: Int = 0,
        attackerShadow: Boolean = false,
        defenderShadow: Boolean = false,
        stab: Boolean = move.type in attacker.types
    ) = StandardOracleV118.damage(
        attacker, ivA, 20.0, attackStage, attackerShadow,
        defenderSpecies, ivD, 20.0, defenseStage, defenderShadow,
        move.type, move.power, stab
    )

    verify(prodDamage(grassSteel, fireMove) == oracleDamage(grassSteel, fireMove), "Damage double weakness + STAB")
    verify(prodDamage(waterDragon, fireMove) == oracleDamage(waterDragon, fireMove), "Damage double resistance + STAB")
    verify(prodDamage(ghost, normalMove) == oracleDamage(ghost, normalMove), "Damage immunity conversion")
    verify(prodDamage(grassSteel, fireMove, attackStage = 2, defenseStage = -1) == oracleDamage(grassSteel, fireMove, 2, -1), "Damage stages")
    verify(prodDamage(grassSteel, fireMove, attackerShadow = true) == oracleDamage(grassSteel, fireMove, attackerShadow = true), "Shadow attacker damage")
    verify(prodDamage(grassSteel, fireMove, defenderShadow = true) == oracleDamage(grassSteel, fireMove, defenderShadow = true), "Shadow defender damage")
    verify(prodDamage(grassSteel, fireMove, attackStage = 4, defenseStage = -4, attackerShadow = true, defenderShadow = true) ==
        oracleDamage(grassSteel, fireMove, 4, -4, true, true), "Combined stage + Shadow damage")

    // Fast Move timing: duration N resolves on turn N and awards energy at the same resolution.
    val engine = BattleEngine()
    val tank = sp("tank", 9006, 100, 300, 500, "normal")
    val iv = IvSpread(0, 0, 0)
    for (turns in 1..5) {
        val fast = PvpMove("FAST_$turns", "Fast $turns", type = "normal", power = 0, energyGain = 7, turns = turns, cooldownMs = turns * 500)
        val result = engine.simulate(
            build("timing-$turns", tank, iv, 20.0, fast, listOf(dummyCharge)),
            build("target-$turns", tank, iv, 20.0, zeroFast, listOf(dummyCharge)),
            BattleScenario(shieldsA = 0, shieldsB = 0, maxTurns = turns)
        )
        val hit = result.timeline.firstOrNull { it.type == BattleEventType.FAST_MOVE && it.actorKey == "timing-$turns" }
        verify(hit != null, "$turns-turn Fast must resolve within its duration")
        verify(hit!!.turn == turns, "$turns-turn Fast expected resolution T$turns actual T${hit.turn}")
        verify(hit.energyAfter == 7, "$turns-turn Fast energy awarded at resolution")
    }

    // Outcome-level fast-only micro battles, using a standalone event scheduler oracle.
    fun verifyFastOnlyCase(label: String, fastA: PvpMove, fastB: PvpMove, speciesA: PokemonSpecies, speciesB: PokemonSpecies) {
        val impossibleCharge = PvpMove("IMPOSSIBLE_$label", "Impossible", type = "normal", power = 1, energyCost = 999)
        val ba = build("micro-a-$label", speciesA, iv, 20.0, fastA, listOf(impossibleCharge))
        val bb = build("micro-b-$label", speciesB, iv, 20.0, fastB, listOf(impossibleCharge))
        val damageA = StandardOracleV118.damage(speciesA, iv, 20.0, 0, false, speciesB, iv, 20.0, 0, false, fastA.type, fastA.power, fastA.type in speciesA.types)
        val damageB = StandardOracleV118.damage(speciesB, iv, 20.0, 0, false, speciesA, iv, 20.0, 0, false, fastB.type, fastB.power, fastB.type in speciesB.types)
        val hpA = StandardOracleV118.stats(speciesA, iv, 20.0).third
        val hpB = StandardOracleV118.stats(speciesB, iv, 20.0).third
        val oracle = FastOnlyBattleOracleV118.simulate(hpA, damageA, fastA.turns, hpB, damageB, fastB.turns)
        val prod = engine.simulate(
            ba, bb,
            BattleScenario(shieldsA = 0, shieldsB = 0, maxTurns = 480)
        )
        verify(prod.turns == oracle.turns, "$label turns expected=${oracle.turns} actual=${prod.turns}")
        verify(prod.finalA.hp == oracle.hpA && prod.finalB.hp == oracle.hpB, "$label final HP expected=${oracle.hpA}/${oracle.hpB} actual=${prod.finalA.hp}/${prod.finalB.hp}")
        verify(prod.battleRatingA == oracle.ratingA && prod.battleRatingB == oracle.ratingB, "$label rating expected=${oracle.ratingA}/${oracle.ratingB} actual=${prod.battleRatingA}/${prod.battleRatingB}")
        verify(prod.outcomeForA.name == oracle.outcome, "$label outcome expected=${oracle.outcome} actual=${prod.outcomeForA}")
    }

    val oneA = PvpMove("ONE_A", "One A", type = "normal", power = 7, energyGain = 1, turns = 1, cooldownMs = 500)
    val oneB = PvpMove("ONE_B", "One B", type = "normal", power = 7, energyGain = 1, turns = 1, cooldownMs = 500)
    verifyFastOnlyCase("symmetric-1v1", oneA, oneB, tank, tank)

    val two = PvpMove("TWO", "Two", type = "normal", power = 12, energyGain = 1, turns = 2, cooldownMs = 1000)
    verifyFastOnlyCase("one-v-two", oneA, two, tank, tank)

    val fireFast = PvpMove("FIRE_FAST", "Fire Fast", type = "fire", power = 9, energyGain = 1, turns = 3, cooldownMs = 1500)
    val grassTank = sp("grass_tank", 9009, 100, 300, 500, "grass")
    verifyFastOnlyCase("typed-three-v-five", fireFast, PvpMove("FIVE", "Five", type = "normal", power = 20, energyGain = 1, turns = 5, cooldownMs = 2500), attacker, grassTank)

    // Energy ceiling and Charged energy spend.
    val capFast = PvpMove("CAP_FAST", "Cap Fast", type = "normal", power = 0, energyGain = 9, turns = 1)
    val capResult = engine.simulate(
        build("cap", tank, iv, 20.0, capFast, listOf(dummyCharge), energy = 98),
        build("cap-target", tank, iv, 20.0, zeroFast, listOf(dummyCharge)),
        BattleScenario(shieldsA = 0, shieldsB = 0, maxTurns = 1)
    )
    verify(capResult.finalA.energy == 100, "Fast energy must cap at 100")

    val charge45 = PvpMove("CHARGE_45", "Charge 45", type = "normal", power = 10, energyCost = 45)
    val spendResult = engine.simulate(
        build("spender", tank, iv, 20.0, zeroFast, listOf(charge45), energy = 60),
        build("spend-target", tank, iv, 20.0, zeroFast, listOf(dummyCharge)),
        BattleScenario(
            shieldsA = 0, shieldsB = 0,
            policyA = BattleSidePolicy(ChargeStrategy.CHEAPEST, ShieldStrategy.NEVER),
            policyB = BattleSidePolicy(ChargeStrategy.MAX_DAMAGE, ShieldStrategy.NEVER),
            maxTurns = 1
        )
    )
    verify(spendResult.finalA.energy == 15, "Charged Move must spend exact energy cost")

    // Shield semantics: one damage, one shield spent.
    val heavy = PvpMove("HEAVY", "Heavy", type = "normal", power = 200, energyCost = 40)
    val shieldedResult = engine.simulate(
        build("shield-atk", tank, iv, 20.0, zeroFast, listOf(heavy), energy = 40),
        build("shield-def", tank, iv, 20.0, zeroFast, listOf(dummyCharge)),
        BattleScenario(
            shieldsA = 0, shieldsB = 1,
            policyA = BattleSidePolicy(ChargeStrategy.CHEAPEST, ShieldStrategy.NEVER),
            policyB = BattleSidePolicy(ChargeStrategy.MAX_DAMAGE, ShieldStrategy.ALWAYS),
            maxTurns = 1
        )
    )
    val shieldEvent = shieldedResult.timeline.first { it.type == BattleEventType.CHARGED_MOVE && it.actorKey == "shield-atk" }
    verify(shieldEvent.shielded && shieldEvent.damage == 1, "Protect Shield must reduce Charged damage to 1")
    verify(shieldedResult.finalB.shields == 0, "Protect Shield must consume one shield")

    // Guaranteed stat effects apply and clamp at +/-4 even through a shield.
    val debuff = PvpMove(
        "DEBUFF", "Debuff", type = "normal", power = 10, energyCost = 35,
        opponentAttackStageDelta = -2, attackerDefenseStageDelta = 2, buffApplyChance = 1.0
    )
    val statResult = engine.simulate(
        build("stat-atk", tank, iv, 20.0, zeroFast, listOf(debuff), energy = 100, defenseStage = 3),
        build("stat-def", tank, iv, 20.0, zeroFast, listOf(dummyCharge), attackStage = -3),
        BattleScenario(
            shieldsA = 0, shieldsB = 1,
            policyA = BattleSidePolicy(ChargeStrategy.CHEAPEST, ShieldStrategy.NEVER),
            policyB = BattleSidePolicy(ChargeStrategy.MAX_DAMAGE, ShieldStrategy.ALWAYS),
            statEffectPolicy = StatEffectPolicy.GUARANTEED_ONLY,
            maxTurns = 1
        )
    )
    verify(statResult.finalA.defenseStage == 4, "Self Defense buff must clamp at +4")
    verify(statResult.finalB.attackStage == -4, "Opponent Attack debuff must clamp at -4 through shield")

    // CMP uses true effective Attack (base+IV scaled by level), not buffs/debuffs or Shadow multiplier.
    val highCmp = sp("high_cmp", 9007, 220, 150, 200, "normal")
    val lowCmp = sp("low_cmp", 9008, 180, 150, 200, "normal")
    val cmpCharge = PvpMove("CMP_CHARGE", "CMP Charge", type = "normal", power = 20, energyCost = 40)
    val cmpResult = engine.simulate(
        build("high", highCmp, IvSpread(0, 0, 0), 20.0, zeroFast, listOf(cmpCharge), shadow = false, energy = 40, attackStage = -4),
        build("low", lowCmp, IvSpread(15, 0, 0), 20.0, zeroFast, listOf(cmpCharge), shadow = true, energy = 40, attackStage = 4),
        BattleScenario(
            shieldsA = 0, shieldsB = 0,
            policyA = BattleSidePolicy(ChargeStrategy.CHEAPEST, ShieldStrategy.NEVER),
            policyB = BattleSidePolicy(ChargeStrategy.CHEAPEST, ShieldStrategy.NEVER),
            maxTurns = 1
        )
    )
    val chargedOrder = cmpResult.timeline.filter { it.type == BattleEventType.CHARGED_MOVE }.mapNotNull { it.actorKey }
    verify(chargedOrder.firstOrNull() == "high", "CMP must ignore combat Attack stage and Shadow multiplier")

    println("INDEPENDENT_STANDARD_MECHANICS_V1_29_OK assertions=$assertions")
    println("coverage=cpm+cp+stats+stage-table+type+damage+STAB+shadow+fast-1..5+energy+shield+stat-clamp+CMP+3-outcome-microbattles")
}
