import com.rui.pvpgo.engine.*

private fun species(id: String, name: String, atk: Int, def: Int, hp: Int, types: List<String>) =
    PokemonSpecies(
        dex = if (id.startsWith("mimikyu")) 778 else 184,
        name = name,
        speciesId = id,
        types = types,
        baseAttack = atk,
        baseDefense = def,
        baseStamina = hp
    )

fun main() {
    val mimikyu = species("mimikyu", "Mimikyu", 177, 199, 146, listOf("ghost", "fairy"))
    val mimikyuBusted = species("mimikyu_busted", "Mimikyu (Busted)", 177, 199, 146, listOf("ghost", "fairy"))
    val azu = species("azumarill", "Azumarill", 112, 152, 225, listOf("water", "fairy"))

    val shadowClaw = PvpMove("SHADOW_CLAW", "Shadow Claw", type = "ghost", power = 6, energyGain = 8, turns = 2)
    val shadowSneak = PvpMove("SHADOW_SNEAK", "Shadow Sneak", type = "ghost", power = 50, energyCost = 45)
    val playRough = PvpMove("PLAY_ROUGH", "Play Rough", type = "fairy", power = 90, energyCost = 60)
    val bubble = PvpMove("BUBBLE", "Bubble", type = "water", power = 8, energyGain = 11, turns = 3)
    val iceBeam = PvpMove("ICE_BEAM", "Ice Beam", type = "ice", power = 90, energyCost = 55)
    val instantBeam = PvpMove("TEST_INSTANT", "Test Instant", type = "ice", power = 90, energyCost = 55, tags = setOf("instant"))

    fun build(key: String, s: PokemonSpecies, fast: PvpMove, charged: List<PvpMove>, energy: Int = 0) = BattleBuild(
        key = key,
        species = s,
        iv = IvSpread(5, 13, 15),
        level = if (s.speciesId == "azumarill") 43.0 else 25.0,
        fastMove = fast,
        chargedMoves = charged,
        initialEnergy = energy
    )

    val engine = BattleEngine()
    val attacker = build("azu", azu, bubble, listOf(iceBeam), energy = 100)
    val defender = build("mimi", mimikyu, shadowClaw, listOf(shadowSneak, playRough))

    // 1) Unshielded non-instant Charged hit busts Disguise and deals exactly 1 damage.
    val bust = engine.simulate(
        attacker, defender,
        BattleScenario(
            shieldsA = 0,
            shieldsB = 0,
            policyA = BattleSidePolicy(ChargeStrategy.CHEAPEST, ShieldStrategy.NEVER),
            policyB = BattleSidePolicy(ChargeStrategy.CHEAPEST, ShieldStrategy.NEVER),
            maxTurns = 1
        )
    )
    val bustCharge = bust.timeline.first { it.type == BattleEventType.CHARGED_MOVE && it.actorKey == "azu" }
    check(bustCharge.damage == 1 && !bustCharge.shielded)
    check(bust.timeline.any { it.type == BattleEventType.SPECIAL_MECHANIC && it.actorKey == "mimi" && it.note?.contains("MIMIKYU_DISGUISE_BUSTED") == true })
    check(bust.finalB.activeFormId == "mimikyu_busted")
    check(!bust.finalB.disguiseActive)
    check(bust.finalB.defenseStage == -1)

    // 2) A real Protect Shield blocks the Charged Move without consuming Disguise.
    val shielded = engine.simulate(
        attacker, defender,
        BattleScenario(
            shieldsA = 0,
            shieldsB = 1,
            policyA = BattleSidePolicy(ChargeStrategy.CHEAPEST, ShieldStrategy.NEVER),
            policyB = BattleSidePolicy(ChargeStrategy.CHEAPEST, ShieldStrategy.ALWAYS),
            maxTurns = 1
        )
    )
    val shieldCharge = shielded.timeline.first { it.type == BattleEventType.CHARGED_MOVE && it.actorKey == "azu" }
    check(shieldCharge.damage == 1 && shieldCharge.shielded)
    check(shielded.timeline.none { it.type == BattleEventType.SPECIAL_MECHANIC })
    check(shielded.finalB.activeFormId == "mimikyu")
    check(shielded.finalB.disguiseActive)
    check(shielded.finalB.defenseStage == 0)

    // 3) PvPoke excludes moves tagged instant from the Disguise trigger.
    val instantAttacker = build("azu-instant", azu, bubble, listOf(instantBeam), energy = 100)
    val instant = engine.simulate(
        instantAttacker, defender,
        BattleScenario(
            shieldsA = 0,
            shieldsB = 0,
            policyA = BattleSidePolicy(ChargeStrategy.CHEAPEST, ShieldStrategy.NEVER),
            policyB = BattleSidePolicy(ChargeStrategy.CHEAPEST, ShieldStrategy.NEVER),
            maxTurns = 1
        )
    )
    val instantCharge = instant.timeline.first { it.type == BattleEventType.CHARGED_MOVE && it.actorKey == "azu-instant" }
    check((instantCharge.damage ?: 0) > 1)
    check(instant.timeline.none { it.type == BattleEventType.SPECIAL_MECHANIC })
    check(instant.finalB.disguiseActive)
    check(instant.finalB.defenseStage == 0)

    // 4) Direct Busted-form simulations inherit the pinned native -1 Defense state.
    val bustedBuild = build("mimi-busted", mimikyuBusted, shadowClaw, listOf(shadowSneak, playRough))
    val bustedInitial = engine.simulate(
        bustedBuild,
        build("azu-passive", azu, bubble, listOf(iceBeam)),
        BattleScenario(shieldsA = 0, shieldsB = 0, maxTurns = 1)
    )
    check(bustedInitial.finalA.activeFormId == "mimikyu_busted")
    check(!bustedInitial.finalA.disguiseActive)
    check(bustedInitial.finalA.defenseStage == -1)

    // Registry boundary: Mimikyu is implemented locally but remains refused for authoritative advice
    // until an exact-pin upstream trace validates the mechanic.
    check(!SpecialMechanicsRegistry.isStandardAdviceSafe("mimikyu"))
    check(!SpecialMechanicsRegistry.isStandardAdviceSafe("mimikyu_busted"))
    val stillBlocked = listOf(
        "aegislash_blade", "aegislash_shield",
        "cramorant", "cramorant_gulping", "cramorant_gorging",
        "morpeko_full_belly", "morpeko_hangry"
    )
    check(stillBlocked.all { !SpecialMechanicsRegistry.isStandardAdviceSafe(it) })
    check(SpecialMechanicsRegistry.unsupportedForStandardAdvice.size == 4)
    check("mimikyu-disguise" in SpecialMechanicsRegistry.implementedLocally)

    println("SPECIAL_MECHANICS_V1_13_MIMIKYU_OK")
    println("disguise=no-shield->busted; shield->preserved; instant->preserved; busted-native-defense=-1")
    println("authoritativeBlockedFamilies=${SpecialMechanicsRegistry.unsupportedForStandardAdvice.size} remainingOtherBlockedSpecies=${stillBlocked.size} mimikyuLocalImplemented=true")
}
