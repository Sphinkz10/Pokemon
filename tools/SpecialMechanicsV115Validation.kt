import com.rui.pvpgo.engine.*

private fun sp(id: String, name: String, atk: Int, def: Int, sta: Int, types: List<String>) = PokemonSpecies(
    dex = if (id.startsWith("morpeko")) 877 else 113,
    name = name,
    speciesId = id,
    types = types,
    baseAttack = atk,
    baseDefense = def,
    baseStamina = sta
)

fun main() {
    val full = sp("morpeko_full_belly", "Morpeko (Full Belly)", 192, 121, 151, listOf("electric", "dark"))
    val hangry = sp("morpeko_hangry", "Morpeko (Hangry)", 192, 121, 151, listOf("electric", "dark"))
    val chansey = sp("chansey", "Chansey", 60, 128, 487, listOf("normal", "none"))

    val thunderShock = PvpMove("THUNDER_SHOCK", "Thunder Shock", type="electric", power=0, energyGain=20, turns=1)
    val zen = PvpMove("ZEN_HEADBUTT", "Zen Headbutt", type="psychic", power=0, energyGain=6, turns=2)
    val auraElectric = PvpMove(
        "AURA_WHEEL_ELECTRIC", "Aura Wheel", abbreviation="AuW", type="electric", power=100, energyCost=45,
        attackerAttackStageDelta=1, buffApplyChance=1.0, archetype="Boost"
    )
    val dazzling = PvpMove("DAZZLING_GLEAM", "Dazzling Gleam", type="fairy", power=110, energyCost=70)
    val psychicFangs = PvpMove(
        "PSYCHIC_FANGS", "Psychic Fangs", type="psychic", power=40, energyCost=35,
        opponentDefenseStageDelta=-1, buffApplyChance=1.0
    )

    fun build(key: String, species: PokemonSpecies, charged: List<PvpMove>, energy: Int) = BattleBuild(
        key=key,
        species=species,
        iv=IvSpread(5,14,15),
        level=28.5,
        fastMove=if (species.dex == 877) thunderShock else zen,
        chargedMoves=charged,
        initialEnergy=energy
    )

    val engine = BattleEngine()
    val target = build("chansey", chansey, listOf(dazzling), 0)

    // Two consecutive Aura Wheels must alternate Electric -> Dark and return the form to Full Belly.
    val wheel = engine.simulate(
        build("morpeko", full, listOf(auraElectric), 100),
        target,
        BattleScenario(
            shieldsA=0, shieldsB=0,
            policyA=BattleSidePolicy(ChargeStrategy.CHEAPEST, ShieldStrategy.NEVER),
            policyB=BattleSidePolicy(ChargeStrategy.CHEAPEST, ShieldStrategy.NEVER),
            statEffectPolicy=StatEffectPolicy.GUARANTEED_ONLY,
            maxTurns=8
        )
    )
    val wheelEvents = wheel.timeline.filter { it.type == BattleEventType.CHARGED_MOVE && it.actorKey == "morpeko" }
    check(wheelEvents.size >= 3) { "Expected at least three Aura Wheels, got ${wheelEvents.size}" }
    wheelEvents.forEachIndexed { i, event ->
        val expected = if (i % 2 == 0) "AURA_WHEEL_ELECTRIC" else "AURA_WHEEL_DARK"
        check(event.moveId == expected) { "Aura Wheel #${i+1}=${event.moveId}, expected=$expected" }
    }
    val toggles = wheel.timeline.filter { it.type == BattleEventType.SPECIAL_MECHANIC && it.actorKey == "morpeko" && it.note?.contains("MORPEKO_TOGGLE") == true }
    check(toggles.size == wheelEvents.size)
    check(toggles[0].note?.contains("morpeko_hangry") == true)
    check(toggles[1].note?.contains("morpeko_full_belly") == true)
    val expectedFinalForm = if (wheelEvents.size % 2 == 0) "morpeko_full_belly" else "morpeko_hangry"
    check(wheel.finalA.activeFormId == expectedFinalForm)
    check(wheel.finalA.attackStage == wheelEvents.size.coerceAtMost(4)) { "Aura Wheel guaranteed boosts should stack to stage cap" }

    // type=toggle + moveId=ANY means a non-Aura charged move must toggle forms too.
    val anyCharged = engine.simulate(
        build("morpeko-any", full, listOf(psychicFangs), 100),
        target,
        BattleScenario(
            shieldsA=0, shieldsB=0,
            policyA=BattleSidePolicy(ChargeStrategy.CHEAPEST, ShieldStrategy.NEVER),
            policyB=BattleSidePolicy(ChargeStrategy.CHEAPEST, ShieldStrategy.NEVER),
            statEffectPolicy=StatEffectPolicy.GUARANTEED_ONLY,
            maxTurns=1
        )
    )
    check(anyCharged.finalA.activeFormId == "morpeko_hangry")
    check(anyCharged.timeline.any { it.type == BattleEventType.SPECIAL_MECHANIC && it.note?.contains("morpeko_hangry") == true })

    // Starting in Hangry uses Dark Aura Wheel immediately and toggles back to Full Belly.
    val hangryStart = engine.simulate(
        build("morpeko-hangry", hangry, listOf(auraElectric), 100),
        target,
        BattleScenario(
            shieldsA=0, shieldsB=0,
            policyA=BattleSidePolicy(ChargeStrategy.CHEAPEST, ShieldStrategy.NEVER),
            policyB=BattleSidePolicy(ChargeStrategy.CHEAPEST, ShieldStrategy.NEVER),
            maxTurns=1
        )
    )
    val hangryCharge = hangryStart.timeline.first { it.type == BattleEventType.CHARGED_MOVE && it.actorKey == "morpeko-hangry" }
    check(hangryCharge.moveId == "AURA_WHEEL_DARK")
    check(hangryStart.finalA.activeFormId == "morpeko_full_belly")

    check("morpeko-form-toggle" in SpecialMechanicsRegistry.implementedLocally)
    check(!SpecialMechanicsRegistry.isStandardAdviceSafe("morpeko_full_belly"))
    check(!SpecialMechanicsRegistry.isStandardAdviceSafe("morpeko_hangry"))

    val divergence = KnownDivergencesRegistry.forSpecies("morpeko_full_belly").single()
    check(divergence.id == "a93147b-morpeko-toggle-one-way")
    check(divergence.disposition == DivergenceDisposition.LOCAL_CORRECTNESS_WINS)
    check(divergence.upstreamCommitSha == PvPokeReferencePin.COMMIT_SHA)

    println("SPECIAL_MECHANICS_V1_15_MORPEKO_OK")
    println("auraWheel=${wheelEvents.joinToString("->") { it.moveId ?: "?" }} finalForm=${wheel.finalA.activeFormId} attackStage=${wheel.finalA.attackStage}")
    println("knownReferenceDivergence=${divergence.id} authoritativeAdvice=REFUSED")
}
