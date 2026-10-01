import com.rui.pvpgo.engine.*
import java.util.Random
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

/**
 * F44B/F91 deterministic randomized cross-checks.
 * Expected values are calculated without PokemonMath, BattleDamage, CpMultipliers or PvpTypeChart.
 * The seed is fixed so any failure is exactly reproducible.
 */
private object RandomOracleV120 {
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

    fun cpm(level: Double): Double = cpm[level] ?: error("unsupported level $level")
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

    fun stats(s: PokemonSpecies, iv: IvSpread, level: Double): Triple<Double, Double, Int> {
        val m = cpm(level)
        return Triple(
            (s.baseAttack + iv.attack) * m,
            (s.baseDefense + iv.defense) * m,
            max(10, floor((s.baseStamina + iv.stamina) * m).toInt())
        )
    }

    fun cp(s: PokemonSpecies, iv: IvSpread, level: Double): Int {
        val m = cpm(level)
        val raw = (s.baseAttack + iv.attack) *
            sqrt((s.baseDefense + iv.defense).toDouble()) *
            sqrt((s.baseStamina + iv.stamina).toDouble()) * m * m / 10.0
        return max(10, floor(raw).toInt())
    }

    private fun one(attack: String, defense: String): Double = when (attack to defense) {
        "fire" to "grass", "fire" to "steel", "fire" to "bug", "fire" to "ice" -> SUPER
        "fire" to "fire", "fire" to "water", "fire" to "rock", "fire" to "dragon" -> RESIST
        "water" to "fire", "water" to "ground", "water" to "rock" -> SUPER
        "water" to "water", "water" to "grass", "water" to "dragon" -> RESIST
        "electric" to "water", "electric" to "flying" -> SUPER
        "electric" to "electric", "electric" to "grass", "electric" to "dragon" -> RESIST
        "electric" to "ground" -> IMMUNITY
        "ghost" to "ghost", "ghost" to "psychic" -> SUPER
        "ghost" to "dark" -> RESIST
        "ghost" to "normal" -> IMMUNITY
        "normal" to "rock", "normal" to "steel" -> RESIST
        "normal" to "ghost" -> IMMUNITY
        "ground" to "electric", "ground" to "fire", "ground" to "poison", "ground" to "rock", "ground" to "steel" -> SUPER
        "ground" to "bug", "ground" to "grass" -> RESIST
        "ground" to "flying" -> IMMUNITY
        else -> 1.0
    }

    fun effectiveness(moveType: String, defenderTypes: List<String>): Double =
        defenderTypes.fold(1.0) { acc, d -> acc * one(moveType, d) }

    fun damage(
        a: PokemonSpecies, aIv: IvSpread, aLevel: Double, aStage: Int, aShadow: Boolean,
        d: PokemonSpecies, dIv: IvSpread, dLevel: Double, dStage: Int, dShadow: Boolean,
        moveType: String, power: Int
    ): Int {
        val atk = stats(a, aIv, aLevel).first * stage(aStage) * if (aShadow) SHADOW_ATK else 1.0
        val def = stats(d, dIv, dLevel).second * stage(dStage) * if (dShadow) SHADOW_DEF else 1.0
        val stab = if (moveType in a.types) STAB else 1.0
        val eff = effectiveness(moveType, d.types)
        return floor(power * stab * (atk / def) * eff * 0.5 * BONUS).toInt() + 1
    }
}

private fun species(id: String, dex: Int, atk: Int, def: Int, sta: Int, types: List<String>) = PokemonSpecies(
    dex = dex, name = id, speciesId = id, types = types, baseAttack = atk, baseDefense = def, baseStamina = sta
)

fun main() {
    val rng = Random(256160025L)
    val levels = listOf(1.0, 20.0, 40.0, 50.0, 51.0)
    val typePairs = listOf(
        listOf("normal"), listOf("ghost"), listOf("water"), listOf("grass"), listOf("fire"),
        listOf("ground"), listOf("flying"), listOf("steel"), listOf("dark"), listOf("psychic"),
        listOf("grass", "steel"), listOf("water", "dragon")
    )
    val moveTypes = listOf("normal", "ghost", "water", "fire", "electric", "ground")
    val zeroFast = PvpMove("ZERO", "Zero", type = "normal", power = 0, energyGain = 1, turns = 1)
    val dummy = PvpMove("DUMMY", "Dummy", type = "normal", power = 1, energyCost = 100)

    var assertions = 0
    fun verify(ok: Boolean, msg: String) { check(ok) { msg }; assertions++ }

    // 1,000 independent CP/stat cases across five externally pinned CPM fixtures.
    repeat(1000) { i ->
        val s = species(
            "s$i", 10000 + i,
            40 + rng.nextInt(260), 40 + rng.nextInt(260), 40 + rng.nextInt(460),
            typePairs[rng.nextInt(typePairs.size)]
        )
        val iv = IvSpread(rng.nextInt(16), rng.nextInt(16), rng.nextInt(16))
        val level = levels[rng.nextInt(levels.size)]
        val expected = RandomOracleV120.stats(s, iv, level)
        val actual = PokemonMath.battleStats(s, iv, level)
        verify(kotlin.math.abs(actual.attack - expected.first) < 1e-10, "stats attack case=$i")
        verify(kotlin.math.abs(actual.defense - expected.second) < 1e-10, "stats defense case=$i")
        verify(actual.hp == expected.third, "stats hp case=$i")
        verify(PokemonMath.cp(s, iv, level) == RandomOracleV120.cp(s, iv, level), "cp case=$i")
    }

    // 2,000 independent damage cases; randomized stages, Shadow, STAB and a controlled type matrix.
    repeat(2000) { i ->
        val aTypes = typePairs[rng.nextInt(typePairs.size)]
        val dTypes = typePairs[rng.nextInt(typePairs.size)]
        val a = species("a$i", 20000 + i, 60 + rng.nextInt(240), 60 + rng.nextInt(240), 80 + rng.nextInt(320), aTypes)
        val d = species("d$i", 30000 + i, 60 + rng.nextInt(240), 60 + rng.nextInt(240), 80 + rng.nextInt(320), dTypes)
        val ai = IvSpread(rng.nextInt(16), rng.nextInt(16), rng.nextInt(16))
        val di = IvSpread(rng.nextInt(16), rng.nextInt(16), rng.nextInt(16))
        val al = levels[rng.nextInt(levels.size)]
        val dl = levels[rng.nextInt(levels.size)]
        val asg = -4 + rng.nextInt(9)
        val dsg = -4 + rng.nextInt(9)
        val ash = rng.nextBoolean()
        val dsh = rng.nextBoolean()
        val mt = moveTypes[rng.nextInt(moveTypes.size)]
        val power = rng.nextInt(181)
        val move = PvpMove("M$i", "M$i", type = mt, power = power, energyCost = 50)
        val ab = BattleBuild("a$i", a, ai, al, zeroFast, listOf(dummy), isShadow = ash, initialAttackStage = asg)
        val db = BattleBuild("d$i", d, di, dl, zeroFast, listOf(dummy), isShadow = dsh, initialDefenseStage = dsg)
        val actual = BattleDamage.damage(ab, asg, db, dsg, move)
        val expected = RandomOracleV120.damage(a, ai, al, asg, ash, d, di, dl, dsg, dsh, mt, power)
        verify(actual == expected, "damage case=$i expected=$expected actual=$actual")
    }

    // Sensitivity checks: deliberate mutants must be rejected by the independent expected value.
    val probeA = species("probeA", 50001, 223, 173, 186, listOf("fire", "flying"))
    val probeD = species("probeD", 50002, 150, 200, 220, listOf("grass", "steel"))
    val piv = IvSpread(0, 15, 13)
    val div = IvSpread(5, 15, 15)
    val expected = RandomOracleV120.damage(probeA, piv, 20.0, 2, true, probeD, div, 20.0, -1, false, "fire", 90)
    val noShadow = RandomOracleV120.damage(probeA, piv, 20.0, 2, false, probeD, div, 20.0, -1, false, "fire", 90)
    val noStage = RandomOracleV120.damage(probeA, piv, 20.0, 0, true, probeD, div, 20.0, 0, false, "fire", 90)
    val wrongType = RandomOracleV120.damage(probeA, piv, 20.0, 2, true, probeD, div, 20.0, -1, false, "normal", 90)
    verify(expected != noShadow, "mutation shadow sensitivity")
    verify(expected != noStage, "mutation stage sensitivity")
    verify(expected != wrongType, "mutation type sensitivity")

    println("INDEPENDENT_RANDOMIZED_CERTIFICATION_V1_29_OK")
    println("seed=0xF44B119 statsCpCases=1000 damageCases=2000 assertions=$assertions mutationChecks=3")
}
