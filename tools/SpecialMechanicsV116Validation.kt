import com.rui.pvpgo.engine.*
import kotlin.math.floor

private fun sp(id: String, dex: Int, atk: Int, def: Int, sta: Int, types: List<String>) = PokemonSpecies(
    dex = dex,
    name = id,
    speciesId = id,
    types = types,
    baseAttack = atk,
    baseDefense = def,
    baseStamina = sta
)

fun main() {
    val cramorant = sp("cramorant", 845, 173, 163, 172, listOf("flying", "water"))
    val gulping = sp("cramorant_gulping", 845, 173, 163, 172, listOf("flying", "water"))
    val gorging = sp("cramorant_gorging", 845, 173, 163, 172, listOf("flying", "water"))
    val target = sp("target", 999, 180, 160, 200, listOf("normal", "none"))
    val hitter = sp("hitter", 998, 220, 150, 180, listOf("normal", "none"))

    val peck = PvpMove("PECK", "Peck", type="flying", power=0, energyGain=10, turns=1)
    val dive = PvpMove("DIVE", "Dive", type="water", power=50, energyCost=40)
    val fly = PvpMove("FLY", "Fly", type="flying", power=80, energyCost=45)
    val passiveFast = PvpMove("PASSIVE", "Passive", type="normal", power=0, energyGain=1, turns=1)
    val hitFast = PvpMove("HIT", "Hit", type="normal", power=20, energyGain=1, turns=1)
    val slam = PvpMove("SLAM", "Slam", type="normal", power=20, energyCost=35)
    val nuke = PvpMove("NUKE", "Nuke", type="normal", power=500, energyCost=35)
    val instant = PvpMove("INSTANT_TEST", "Instant Test", type="normal", power=20, energyCost=35, tags=setOf("instant"))
    val dummy = PvpMove("DUMMY", "Dummy", type="normal", power=1, energyCost=100)

    fun build(
        key: String,
        species: PokemonSpecies,
        fast: PvpMove,
        charged: List<PvpMove>,
        energy: Int = 0,
        level: Double = 27.0
    ) = BattleBuild(
        key = key,
        species = species,
        iv = IvSpread(0, 14, 11),
        level = level,
        fastMove = fast,
        chargedMoves = charged,
        initialEnergy = energy
    )

    val engine = BattleEngine()
    val inertTarget = build("target", target, passiveFast, listOf(dummy), 0, 20.0)
    val noShield = BattleSidePolicy(ChargeStrategy.CHEAPEST, ShieldStrategy.NEVER)

    // >50% HP + Dive => Gulping.
    val high = engine.simulate(
        build("cr-high", cramorant, peck, listOf(dive), 40),
        inertTarget,
        BattleScenario(shieldsA=0, shieldsB=0, policyA=noShield, policyB=noShield, maxTurns=1)
    )
    check(high.finalA.activeFormId == "cramorant_gulping") { "High-HP Dive should load Gulping" }
    check(high.timeline.any { it.type == BattleEventType.SPECIAL_MECHANIC && it.note?.contains("CRAMORANT_LOAD") == true && it.note?.contains("cramorant_gulping") == true })

    // Damage Cramorant below 50% before it reaches Dive => Gorging.
    val lowOpponent = build("hitter", hitter, hitFast, listOf(dummy), 0, 20.0)
    val low = engine.simulate(
        build("cr-low", cramorant, peck, listOf(dive), 0),
        lowOpponent,
        BattleScenario(shieldsA=0, shieldsB=0, policyA=noShield, policyB=noShield, maxTurns=5)
    )
    check(low.timeline.any { it.type == BattleEventType.SPECIAL_MECHANIC && it.note?.contains("cramorant_gorging") == true }) {
        "Low-HP Dive should load Gorging; final=${low.finalA}"
    }

    fun incomingScenario(shieldsForCramorant: Int, shieldStrategy: ShieldStrategy = ShieldStrategy.NEVER) = BattleScenario(
        shieldsA = shieldsForCramorant,
        shieldsB = 0,
        policyA = BattleSidePolicy(ChargeStrategy.CHEAPEST, shieldStrategy),
        policyB = noShield,
        maxTurns = 1
    )

    // Gulping retaliation: 15% max HP + 1 and -1 Defense, then reset to normal.
    val chargedTarget = build("attacker", target, passiveFast, listOf(slam), 35, 20.0)
    val gulp = engine.simulate(
        build("gulp", gulping, peck, listOf(fly), 0),
        chargedTarget,
        incomingScenario(0)
    )
    val gulpEvent = gulp.timeline.single { it.type == BattleEventType.CHARGED_MOVE && it.moveId == "GULP_MISSILE_ARROKUDA" }
    val expectedGulpDamage = floor(gulp.finalB.maxHp * 0.15).toInt() + 1
    check(gulpEvent.damage == expectedGulpDamage) { "Gulp damage=${gulpEvent.damage}, expected=$expectedGulpDamage" }
    check(gulp.finalB.defenseStage == -1 && gulp.finalB.attackStage == 0)
    check(gulp.finalA.activeFormId == "cramorant")

    // Gorging retaliation: same damage method and -2 Attack.
    val gorge = engine.simulate(
        build("gorge", gorging, peck, listOf(fly), 0),
        chargedTarget,
        incomingScenario(0)
    )
    val gorgeEvent = gorge.timeline.single { it.type == BattleEventType.CHARGED_MOVE && it.moveId == "GULP_MISSILE_PIKACHU" }
    check(gorgeEvent.damage == expectedGulpDamage)
    check(gorge.finalB.attackStage == -2 && gorge.finalB.defenseStage == 0)
    check(gorge.finalA.activeFormId == "cramorant")

    // Protect Shield prevents the retaliation and preserves the loaded form.
    val shielded = engine.simulate(
        build("gulp-shield", gulping, peck, listOf(fly), 0),
        chargedTarget,
        incomingScenario(1, ShieldStrategy.ALWAYS)
    )
    check(shielded.timeline.none { it.moveId == "GULP_MISSILE_ARROKUDA" })
    check(shielded.finalA.activeFormId == "cramorant_gulping")
    check(shielded.finalA.shields == 0)

    // Pinned Cramorant shield heuristic delegates weak hits to wouldShield; a safe weak hit is
    // intentionally left unshielded so Gulp Missile can fire.
    val weak = PvpMove("WEAK", "Weak", type="normal", power=10, energyCost=35)
    val weakTarget = build("weak-attacker", target, passiveFast, listOf(weak), 35, 20.0)
    val weakPolicy = engine.simulate(
        build("gulp-policy", gulping, peck, listOf(fly), 0),
        weakTarget,
        BattleScenario(
            shieldsA=1, shieldsB=0,
            policyA=BattleSidePolicy(ChargeStrategy.CHEAPEST, ShieldStrategy.PVPOKE_DEFAULT),
            policyB=noShield,
            maxTurns=1
        )
    )
    check(weakPolicy.finalA.shields == 1)
    check(weakPolicy.timeline.any { it.moveId == "GULP_MISSILE_ARROKUDA" && it.type == BattleEventType.CHARGED_MOVE })

    // Instant Charged Moves are unshieldable and do not trigger Gulp Missile.
    val instantTarget = build("instant-attacker", target, passiveFast, listOf(instant), 35, 20.0)
    val instantResult = engine.simulate(
        build("gulp-instant", gulping, peck, listOf(fly), 0),
        instantTarget,
        incomingScenario(1, ShieldStrategy.ALWAYS)
    )
    check(instantResult.finalA.shields == 1) { "Instant move must not consume a Protect Shield" }
    check(instantResult.timeline.none { it.moveId == "GULP_MISSILE_ARROKUDA" })
    check(instantResult.finalA.activeFormId == "cramorant_gulping")

    // ignoresFaint: Cramorant still retaliates after the triggering Charged Move KOs it.
    val nukeTarget = build("nuke-attacker", target, passiveFast, listOf(nuke), 35, 20.0)
    val faintTrade = engine.simulate(
        build("gulp-faint", gulping, peck, listOf(fly), 0),
        nukeTarget,
        incomingScenario(0)
    )
    check(faintTrade.finalA.hp == 0)
    check(faintTrade.timeline.any { it.moveId == "GULP_MISSILE_ARROKUDA" && it.type == BattleEventType.CHARGED_MOVE })
    check(faintTrade.finalB.defenseStage == -1)
    check(faintTrade.finalA.activeFormId == "cramorant")

    check("cramorant-gulp-missile" in SpecialMechanicsRegistry.implementedLocally)
    check(!SpecialMechanicsRegistry.isStandardAdviceSafe("cramorant"))
    check(!SpecialMechanicsRegistry.isStandardAdviceSafe("cramorant_gulping"))
    check(!SpecialMechanicsRegistry.isStandardAdviceSafe("cramorant_gorging"))

    val lowLoadNote = low.timeline.first { it.note?.contains("CRAMORANT_LOAD") == true }.note
    println("SPECIAL_MECHANICS_V1_16_CRAMORANT_OK")
    println("forms=high->${high.finalA.activeFormId} low->$lowLoadNote")
    println("gulpDamage=$expectedGulpDamage gulpDebuff=DEF-1 gorgeDebuff=ATK-2 shieldPreserves=true weakPolicyNoShield=true instantExcluded=true faintThrough=true")
    println("authoritativeAdvice=REFUSED pending exact-pin trace + switch-reset/3v3 + broader special action-policy")
}
