import com.rui.pvpgo.engine.*
import kotlin.math.floor

/**
 * F91/V1.17 independent mechanics matrix.
 *
 * This file deliberately does NOT call production special-mechanic helpers. Expected transitions
 * are encoded in the small oracle below from independent references recorded in
 * tools/independent_special_mechanics_reference.json. Production BattleEngine outputs are then
 * compared against those expectations through the public simulate() contract only.
 */
private object MechanicsOracleV117 {
    data class MimikyuResult(
        val chargedDamage: Int?,
        val nextForm: String,
        val defenseStageDelta: Int,
        val disguiseRemains: Boolean
    )

    fun mimikyu(disguiseActive: Boolean, shielded: Boolean, instant: Boolean): MimikyuResult {
        if (!disguiseActive || shielded || instant) {
            return MimikyuResult(null, if (disguiseActive) "mimikyu" else "mimikyu_busted", 0, disguiseActive)
        }
        return MimikyuResult(1, "mimikyu_busted", -1, false)
    }

    data class FastSpec(val moveId: String, val power: Int, val energyGain: Int, val turns: Int)

    fun aegislashFast(form: String, baseMove: String): FastSpec = when (form to baseMove) {
        "aegislash_shield" to "psycho_cut" -> FastSpec("AEGISLASH_CHARGE_PSYCHO_CUT", 0, 6, 2)
        "aegislash_blade" to "psycho_cut" -> FastSpec("PSYCHO_CUT", 4, 9, 2)
        "aegislash_shield" to "air_slash" -> FastSpec("AEGISLASH_CHARGE_AIR_SLASH", 0, 6, 3)
        "aegislash_blade" to "air_slash" -> FastSpec("AIR_SLASH", 9, 9, 3)
        else -> error("Unknown Aegislash fast form=$form move=$baseMove")
    }

    fun aegislashAfterCharged(form: String): String =
        if (form == "aegislash_shield") "aegislash_blade" else form

    fun aegislashAfterProtectShield(form: String): String =
        if (form == "aegislash_blade") "aegislash_shield" else form

    fun morpekoNextForm(form: String): String = when (form) {
        "morpeko_full_belly" -> "morpeko_hangry"
        "morpeko_hangry" -> "morpeko_full_belly"
        else -> error("Unknown Morpeko form $form")
    }

    fun auraWheel(form: String): String = when (form) {
        "morpeko_full_belly" -> "AURA_WHEEL_ELECTRIC"
        "morpeko_hangry" -> "AURA_WHEEL_DARK"
        else -> error("Unknown Morpeko form $form")
    }

    fun cramorantLoadedForm(hp: Int, maxHp: Int): String =
        if (hp.toDouble() / maxHp.toDouble() > 0.5) "cramorant_gulping" else "cramorant_gorging"

    fun gulpMissileDamage(targetMaxHp: Int): Int = floor(targetMaxHp * 0.15).toInt() + 1

    fun gulpDebuff(form: String): Pair<Int, Int> = when (form) {
        "cramorant_gulping" -> 0 to -1
        "cramorant_gorging" -> -2 to 0
        else -> 0 to 0
    }

    fun shouldTriggerGulp(form: String, incomingShielded: Boolean, incomingInstant: Boolean): Boolean =
        form in setOf("cramorant_gulping", "cramorant_gorging") && !incomingShielded && !incomingInstant
}

private fun species(id: String, dex: Int, atk: Int, def: Int, sta: Int, types: List<String>) = PokemonSpecies(
    dex = dex,
    name = id,
    speciesId = id,
    types = types,
    baseAttack = atk,
    baseDefense = def,
    baseStamina = sta
)

private fun build(
    key: String,
    species: PokemonSpecies,
    fast: PvpMove,
    charged: List<PvpMove>,
    level: Double,
    energy: Int = 0,
    iv: IvSpread = IvSpread(5, 13, 15)
) = BattleBuild(key, species, iv, level, fast, charged, initialEnergy = energy)

fun main() {
    var assertions = 0
    fun verify(value: Boolean, message: String) {
        check(value) { message }
        assertions++
    }

    // --- Pure oracle invariants ---------------------------------------------------------------
    val mimiBust = MechanicsOracleV117.mimikyu(disguiseActive = true, shielded = false, instant = false)
    verify(mimiBust.chargedDamage == 1, "Mimikyu oracle damage")
    verify(mimiBust.nextForm == "mimikyu_busted" && mimiBust.defenseStageDelta == -1 && !mimiBust.disguiseRemains, "Mimikyu oracle bust")
    val mimiShield = MechanicsOracleV117.mimikyu(disguiseActive = true, shielded = true, instant = false)
    verify(mimiShield.disguiseRemains && mimiShield.nextForm == "mimikyu", "Mimikyu oracle shield preserve")

    val aegisShieldFast = MechanicsOracleV117.aegislashFast("aegislash_shield", "psycho_cut")
    val aegisBladeFast = MechanicsOracleV117.aegislashFast("aegislash_blade", "psycho_cut")
    verify(aegisShieldFast == MechanicsOracleV117.FastSpec("AEGISLASH_CHARGE_PSYCHO_CUT", 0, 6, 2), "Aegislash shield fast oracle")
    verify(aegisBladeFast == MechanicsOracleV117.FastSpec("PSYCHO_CUT", 4, 9, 2), "Aegislash blade fast oracle")
    verify(MechanicsOracleV117.aegislashAfterCharged("aegislash_shield") == "aegislash_blade", "Aegislash charged form oracle")
    verify(MechanicsOracleV117.aegislashAfterProtectShield("aegislash_blade") == "aegislash_shield", "Aegislash shield reset oracle")

    verify(MechanicsOracleV117.morpekoNextForm("morpeko_full_belly") == "morpeko_hangry", "Morpeko first toggle")
    verify(MechanicsOracleV117.morpekoNextForm("morpeko_hangry") == "morpeko_full_belly", "Morpeko second toggle")
    verify(MechanicsOracleV117.auraWheel("morpeko_full_belly") == "AURA_WHEEL_ELECTRIC", "Morpeko electric wheel")
    verify(MechanicsOracleV117.auraWheel("morpeko_hangry") == "AURA_WHEEL_DARK", "Morpeko dark wheel")

    verify(MechanicsOracleV117.cramorantLoadedForm(51, 100) == "cramorant_gulping", "Cramorant >50")
    verify(MechanicsOracleV117.cramorantLoadedForm(50, 100) == "cramorant_gorging", "Cramorant <=50")
    verify(MechanicsOracleV117.gulpMissileDamage(119) == 18, "Cramorant 15% max HP")
    verify(MechanicsOracleV117.gulpDebuff("cramorant_gulping") == (0 to -1), "Cramorant gulping debuff")
    verify(MechanicsOracleV117.gulpDebuff("cramorant_gorging") == (-2 to 0), "Cramorant gorging debuff")
    verify(MechanicsOracleV117.shouldTriggerGulp("cramorant_gulping", false, false), "Cramorant trigger")
    verify(!MechanicsOracleV117.shouldTriggerGulp("cramorant_gulping", true, false), "Cramorant shield exclusion")

    // --- Production smoke vs independent oracle ---------------------------------------------
    val engine = BattleEngine()
    val passive = PvpMove("PASSIVE", "Passive", type = "normal", power = 0, energyGain = 1, turns = 1)
    val dummy = PvpMove("DUMMY", "Dummy", type = "normal", power = 1, energyCost = 100)

    // Mimikyu
    val mimikyu = species("mimikyu", 778, 177, 199, 146, listOf("ghost", "fairy"))
    val azu = species("azumarill", 184, 112, 152, 225, listOf("water", "fairy"))
    val shadowClaw = PvpMove("SHADOW_CLAW", "Shadow Claw", type = "ghost", power = 6, energyGain = 8, turns = 2)
    val shadowSneak = PvpMove("SHADOW_SNEAK", "Shadow Sneak", type = "ghost", power = 50, energyCost = 45)
    val bubble = PvpMove("BUBBLE", "Bubble", type = "water", power = 8, energyGain = 11, turns = 3)
    val iceBeam = PvpMove("ICE_BEAM", "Ice Beam", type = "ice", power = 90, energyCost = 55)
    val mimiResult = engine.simulate(
        build("azu", azu, bubble, listOf(iceBeam), 43.0, 55),
        build("mimi", mimikyu, shadowClaw, listOf(shadowSneak), 25.0),
        BattleScenario(
            shieldsA = 0, shieldsB = 0,
            policyA = BattleSidePolicy(ChargeStrategy.CHEAPEST, ShieldStrategy.NEVER),
            policyB = BattleSidePolicy(ChargeStrategy.CHEAPEST, ShieldStrategy.NEVER),
            maxTurns = 1
        )
    )
    val mimiCharge = mimiResult.timeline.first { it.type == BattleEventType.CHARGED_MOVE && it.actorKey == "azu" }
    verify(mimiCharge.damage == mimiBust.chargedDamage, "Mimikyu production damage vs oracle")
    verify(mimiResult.finalB.activeFormId == mimiBust.nextForm && mimiResult.finalB.defenseStage == mimiBust.defenseStageDelta, "Mimikyu production state vs oracle")

    // Aegislash — this specifically guards the V1.17 bug fix: Fast Move records must change with form.
    val aegis = species("aegislash_shield", 681, 97, 272, 155, listOf("steel", "ghost"))
    val target = species("target", 999, 150, 150, 220, listOf("normal", "none"))
    val shieldPsychoCut = PvpMove(
        "AEGISLASH_CHARGE_PSYCHO_CUT", "Psycho Cut", abbreviation = "PC", type = "psychic",
        power = 0, energyGain = 6, turns = 2, cooldownMs = 1000
    )
    val shadowBall = PvpMove("SHADOW_BALL", "Shadow Ball", type = "ghost", power = 100, energyCost = 50)
    val shieldFastResult = engine.simulate(
        build("aegis-shield", aegis, shieldPsychoCut, listOf(shadowBall), 20.0),
        build("target", target, passive, listOf(dummy), 20.0),
        BattleScenario(shieldsA = 0, shieldsB = 0, maxTurns = 2)
    )
    val shieldFastEvent = shieldFastResult.timeline.first { it.type == BattleEventType.FAST_MOVE && it.actorKey == "aegis-shield" }
    verify(shieldFastEvent.moveId == aegisShieldFast.moveId && shieldFastEvent.damage == 1 && shieldFastEvent.energyAfter == aegisShieldFast.energyGain, "Aegislash Shield fast vs oracle")

    val bladeFastResult = engine.simulate(
        build("aegis-blade", aegis, shieldPsychoCut, listOf(shadowBall), 20.0, 50),
        build("target2", target, passive, listOf(dummy), 20.0),
        BattleScenario(
            shieldsA = 0, shieldsB = 0,
            policyA = BattleSidePolicy(ChargeStrategy.MAX_DAMAGE, ShieldStrategy.NEVER),
            policyB = BattleSidePolicy(ChargeStrategy.MAX_DAMAGE, ShieldStrategy.NEVER),
            maxTurns = 4
        )
    )
    val bladeFastEvent = bladeFastResult.timeline.first { it.type == BattleEventType.FAST_MOVE && it.actorKey == "aegis-blade" }
    verify(bladeFastResult.finalA.activeFormId == MechanicsOracleV117.aegislashAfterCharged("aegislash_shield"), "Aegislash form vs oracle")
    verify(bladeFastEvent.moveId == aegisBladeFast.moveId && bladeFastEvent.energyAfter == aegisBladeFast.energyGain && (bladeFastEvent.damage ?: 0) > 1, "Aegislash Blade fast vs oracle")

    // Morpeko
    val morpeko = species("morpeko_full_belly", 877, 192, 121, 151, listOf("electric", "dark"))
    val chansey = species("chansey", 113, 60, 128, 487, listOf("normal", "none"))
    val thunderShock = PvpMove("THUNDER_SHOCK", "Thunder Shock", type = "electric", power = 0, energyGain = 20, turns = 1)
    val zen = PvpMove("ZEN_HEADBUTT", "Zen Headbutt", type = "psychic", power = 0, energyGain = 6, turns = 2)
    val auraElectric = PvpMove(
        "AURA_WHEEL_ELECTRIC", "Aura Wheel", type = "electric", power = 100, energyCost = 45,
        attackerAttackStageDelta = 1, buffApplyChance = 1.0
    )
    val dazzling = PvpMove("DAZZLING_GLEAM", "Dazzling Gleam", type = "fairy", power = 110, energyCost = 70)
    val morpekoResult = engine.simulate(
        build("morpeko", morpeko, thunderShock, listOf(auraElectric), 28.5, 100),
        build("chansey", chansey, zen, listOf(dazzling), 28.5),
        BattleScenario(
            shieldsA = 0, shieldsB = 0,
            policyA = BattleSidePolicy(ChargeStrategy.CHEAPEST, ShieldStrategy.NEVER),
            policyB = BattleSidePolicy(ChargeStrategy.CHEAPEST, ShieldStrategy.NEVER),
            statEffectPolicy = StatEffectPolicy.GUARANTEED_ONLY,
            maxTurns = 5
        )
    )
    val morpekoCharges = morpekoResult.timeline.filter { it.type == BattleEventType.CHARGED_MOVE && it.actorKey == "morpeko" }
    verify(morpekoCharges.size >= 2, "Morpeko needs two charged moves for toggle matrix")
    verify(morpekoCharges[0].moveId == MechanicsOracleV117.auraWheel("morpeko_full_belly"), "Morpeko first wheel vs oracle")
    verify(morpekoCharges[1].moveId == MechanicsOracleV117.auraWheel("morpeko_hangry"), "Morpeko second wheel vs oracle")
    verify(morpekoResult.finalA.activeFormId == "morpeko_full_belly", "Morpeko two-toggle final form")

    // Cramorant
    val cr = species("cramorant_gulping", 845, 173, 163, 172, listOf("flying", "water"))
    val opp = species("cr-target", 998, 180, 160, 200, listOf("normal", "none"))
    val peck = PvpMove("PECK", "Peck", type = "flying", power = 0, energyGain = 8, turns = 2)
    val fly = PvpMove("FLY", "Fly", type = "flying", power = 80, energyCost = 45)
    val slam = PvpMove("SLAM", "Slam", type = "normal", power = 20, energyCost = 35)
    val crResult = engine.simulate(
        build("cr", cr, peck, listOf(fly), 27.0),
        build("cr-opp", opp, passive, listOf(slam), 20.0, 35),
        BattleScenario(
            shieldsA = 0, shieldsB = 0,
            policyA = BattleSidePolicy(ChargeStrategy.CHEAPEST, ShieldStrategy.NEVER),
            policyB = BattleSidePolicy(ChargeStrategy.CHEAPEST, ShieldStrategy.NEVER),
            maxTurns = 1
        )
    )
    val missile = crResult.timeline.single { it.moveId == "GULP_MISSILE_ARROKUDA" && it.type == BattleEventType.CHARGED_MOVE }
    verify(missile.damage == MechanicsOracleV117.gulpMissileDamage(crResult.finalB.maxHp), "Cramorant production damage vs oracle")
    verify((crResult.finalB.attackStage to crResult.finalB.defenseStage) == MechanicsOracleV117.gulpDebuff("cramorant_gulping"), "Cramorant production debuff vs oracle")
    verify(crResult.finalA.activeFormId == "cramorant", "Cramorant resets to base")

    println("INDEPENDENT_MECHANICS_V1_29_MATRIX_OK")
    println("families=4 assertions=$assertions mimikyu=PASS aegislash=PASS morpeko=PASS cramorant=PASS")
    println("scope=state-transitions+form-fast-moves+damage/debuff triggers; switch/3v3 and full second-simulator battle outcomes remain PARTIAL")
}
