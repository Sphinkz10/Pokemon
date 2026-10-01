import com.rui.pvpgo.engine.*

fun main() {
    fun species(id: String, name: String, atk: Int, def: Int, sta: Int, types: List<String>) = PokemonSpecies(
        dex = if (id.startsWith("aegislash")) 681 else 184,
        name = name,
        speciesId = id,
        types = types,
        baseAttack = atk,
        baseDefense = def,
        baseStamina = sta
    )

    val shield = species("aegislash_shield", "Aegislash (Shield)", 97, 272, 155, listOf("ghost", "steel"))
    val blade = species("aegislash_blade", "Aegislash (Blade)", 272, 97, 155, listOf("ghost", "steel"))
    val target = species("azumarill", "Azumarill", 112, 152, 225, listOf("water", "fairy"))

    val psychoCut = PvpMove("AEGISLASH_CHARGE_PSYCHO_CUT", "Psycho Cut", abbreviation="PC", type="psychic", power=0, energyGain=6, turns=2, cooldownMs=1000)
    val bubble = PvpMove("BUBBLE", "Bubble", type="water", power=8, energyGain=11, turns=1)
    val shadowBall = PvpMove("SHADOW_BALL", "Shadow Ball", type="ghost", power=100, energyCost=50)
    val playRough = PvpMove("PLAY_ROUGH", "Play Rough", type="fairy", power=90, energyCost=60)

    val ivA = IvSpread(4,14,15)
    val ivB = IvSpread(4,15,13)
    fun build(key: String, sp: PokemonSpecies, fast: PvpMove, charged: List<PvpMove>, energy: Int = 0) = BattleBuild(
        key = key,
        species = sp,
        iv = if (sp.dex == 681) ivA else ivB,
        level = if (sp.dex == 681) 20.0 else 30.0,
        fastMove = fast,
        chargedMoves = charged,
        initialEnergy = energy
    )

    val engine = BattleEngine()
    val shieldBuild = build("aegis", shield, psychoCut, listOf(shadowBall), energy=50)
    val bladeBuild = build("aegis-blade-ref", blade, psychoCut, listOf(shadowBall), energy=50)
    val targetBuild = build("azu", target, bubble, listOf(playRough), energy=0)

    // Shield -> Blade must happen before Charged damage. The same-turn incoming Fast must therefore
    // hit Blade defense, not the pre-transform Shield defense.
    val charge = engine.simulate(
        shieldBuild,
        targetBuild,
        BattleScenario(
            shieldsA = 0,
            shieldsB = 0,
            policyA = BattleSidePolicy(ChargeStrategy.MAX_DAMAGE, ShieldStrategy.NEVER),
            policyB = BattleSidePolicy(ChargeStrategy.MAX_DAMAGE, ShieldStrategy.NEVER),
            maxTurns = 1
        )
    )
    val special = charge.timeline.firstOrNull { it.type == BattleEventType.SPECIAL_MECHANIC && it.actorKey == "aegis" }
        ?: error("Missing Aegislash Shield->Blade event")
    val charged = charge.timeline.first { it.type == BattleEventType.CHARGED_MOVE && it.actorKey == "aegis" }
    check(charge.timeline.indexOf(special) < charge.timeline.indexOf(charged))
    check(charge.finalA.activeFormId == "aegislash_blade")

    val expectedBladeCharged = BattleDamage.damage(bladeBuild, 0, targetBuild, 0, shadowBall)
    val expectedShieldCharged = BattleDamage.damage(shieldBuild, 0, targetBuild, 0, shadowBall)
    check(charged.damage == expectedBladeCharged) { "Charged damage=${charged.damage}, expected Blade=$expectedBladeCharged" }
    check(expectedBladeCharged != expectedShieldCharged)

    val incomingFast = charge.timeline.first { it.type == BattleEventType.FAST_MOVE && it.actorKey == "azu" }
    val expectedIntoBlade = BattleDamage.damage(targetBuild, 0, bladeBuild, 0, bubble)
    val expectedIntoShield = BattleDamage.damage(targetBuild, 0, shieldBuild, 0, bubble)
    check(incomingFast.damage == expectedIntoBlade) { "Incoming Fast=${incomingFast.damage}, expected into Blade=$expectedIntoBlade" }
    check(expectedIntoBlade != expectedIntoShield)

    // Blade -> Shield occurs after using a Protect Shield.
    val bladeDefender = build("aegis-def", blade, psychoCut, listOf(shadowBall), energy=0)
    val chargedAttacker = build("azu-atk", target, bubble, listOf(playRough), energy=60)
    val shielded = engine.simulate(
        bladeDefender,
        chargedAttacker,
        BattleScenario(
            shieldsA = 1,
            shieldsB = 0,
            policyA = BattleSidePolicy(ChargeStrategy.MAX_DAMAGE, ShieldStrategy.ALWAYS),
            policyB = BattleSidePolicy(ChargeStrategy.MAX_DAMAGE, ShieldStrategy.NEVER),
            maxTurns = 1
        )
    )
    check(shielded.finalA.shields == 0)
    check(shielded.finalA.activeFormId == "aegislash_shield")
    val shieldEvent = shielded.timeline.first { it.type == BattleEventType.SHIELD && it.actorKey == "aegis-def" }
    val shieldFormEvent = shielded.timeline.first { it.type == BattleEventType.SPECIAL_MECHANIC && it.actorKey == "aegis-def" }
    check(shielded.timeline.indexOf(shieldEvent) < shielded.timeline.indexOf(shieldFormEvent))
    check(shieldFormEvent.note?.contains("AEGISLASH_SHIELD") == true)

    // Starting Shield and doing nothing bespoke keeps Shield; starting Blade without shielding keeps Blade.
    val quietShield = engine.simulate(
        build("quiet-shield", shield, psychoCut, listOf(shadowBall), 0),
        targetBuild,
        BattleScenario(shieldsA=0, shieldsB=0, policyA=BattleSidePolicy(ChargeStrategy.MAX_DAMAGE, ShieldStrategy.NEVER), policyB=BattleSidePolicy(ChargeStrategy.MAX_DAMAGE, ShieldStrategy.NEVER), maxTurns=1)
    )
    check(quietShield.finalA.activeFormId == "aegislash_shield")

    // V1.17/F91: Shield and Blade must also swap their Fast Move records, not only base stats.
    // Shield Psycho Cut is 0 power / +6 energy / 2 turns in the pinned data, so it resolves for
    // minimum 1 damage. After a Charged Move changes to Blade, future Psycho Cut becomes the
    // ordinary 4 power / +9 energy / 2-turn move.
    val shieldFastOnly = engine.simulate(
        build("shield-fast", shield, psychoCut, listOf(shadowBall), 0),
        targetBuild,
        BattleScenario(
            shieldsA=0, shieldsB=0,
            policyA=BattleSidePolicy(ChargeStrategy.MAX_DAMAGE, ShieldStrategy.NEVER),
            policyB=BattleSidePolicy(ChargeStrategy.MAX_DAMAGE, ShieldStrategy.NEVER),
            maxTurns=2
        )
    )
    val shieldFastEvent = shieldFastOnly.timeline.first { it.type == BattleEventType.FAST_MOVE && it.actorKey == "shield-fast" }
    check(shieldFastEvent.moveId == "AEGISLASH_CHARGE_PSYCHO_CUT")
    check(shieldFastEvent.damage == 1)
    check(shieldFastEvent.energyAfter == 6)

    val bladeFastAfterCharge = engine.simulate(
        build("blade-fast", shield, psychoCut, listOf(shadowBall), 50),
        targetBuild,
        BattleScenario(
            shieldsA=0, shieldsB=0,
            policyA=BattleSidePolicy(ChargeStrategy.MAX_DAMAGE, ShieldStrategy.NEVER),
            policyB=BattleSidePolicy(ChargeStrategy.MAX_DAMAGE, ShieldStrategy.NEVER),
            maxTurns=4
        )
    )
    val bladeFastEvent = bladeFastAfterCharge.timeline.first { it.type == BattleEventType.FAST_MOVE && it.actorKey == "blade-fast" }
    check(bladeFastEvent.moveId == "PSYCHO_CUT")
    check((bladeFastEvent.damage ?: 0) > 1)
    check(bladeFastEvent.energyAfter == 9)

    check("aegislash-stance-change" in SpecialMechanicsRegistry.implementedLocally)
    check(!SpecialMechanicsRegistry.isStandardAdviceSafe("aegislash_shield"))
    check(!SpecialMechanicsRegistry.isStandardAdviceSafe("aegislash_blade"))

    println("SPECIAL_MECHANICS_V1_29_AEGISLASH_FAST_FORM_OK")
    println("chargedDamage=$expectedBladeCharged incomingFastIntoBlade=$expectedIntoBlade shieldFast=${shieldFastEvent.damage}/+${shieldFastEvent.energyAfter} bladeFast=${bladeFastEvent.damage}/+${bladeFastEvent.energyAfter} shieldReset=${shielded.finalA.activeFormId}")
    println("authoritativeAdvice=REFUSED pending exact-pin trace + form-specific ActionLogic validation")
}
