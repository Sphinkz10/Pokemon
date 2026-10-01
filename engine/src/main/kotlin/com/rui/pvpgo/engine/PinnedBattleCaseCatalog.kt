package com.rui.pvpgo.engine

/**
 * Exact-current battle cases for PvPoke 1.40.1.3 @ a93147b.
 *
 * Keep this catalog separate from historical-result fixtures. The old multi-battle snapshot used
 * older move stats (notably Walrein), so historical diagnostics must never be reused as exact-pin
 * evidence.
 */
data class PinnedBattleCase(
    val fixtureId: String,
    val a: BattleBuild,
    val b: BattleBuild,
    val shieldsA: Int = 1,
    val shieldsB: Int = 1,
    val coverage: Set<String> = emptySet()
) {
    fun scenario(): BattleScenario = BattleScenario(
        shieldsA = shieldsA,
        shieldsB = shieldsB,
        policyA = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.PVPOKE_DEFAULT),
        policyB = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.PVPOKE_DEFAULT),
        statEffectPolicy = StatEffectPolicy.PVPOKE_DETERMINISTIC_METER
    )
}

object PinnedBattleCaseCatalog {
    const val REFERENCE_SHA = "a93147bf1f2e829758958bfb5b37e56bbadc9678"
    const val REFERENCE_VERSION = "1.40.1.3"

    // Species base stats from the pinned Game Master.
    val azumarill = PokemonSpecies(184, "Azumarill", speciesId = "azumarill", types = listOf("water", "fairy"), baseAttack = 112, baseDefense = 152, baseStamina = 225)
    val feraligatr = PokemonSpecies(160, "Feraligatr", speciesId = "feraligatr", types = listOf("water"), baseAttack = 205, baseDefense = 188, baseStamina = 198)
    val walrein = PokemonSpecies(365, "Walrein", speciesId = "walrein", types = listOf("ice", "water"), baseAttack = 182, baseDefense = 176, baseStamina = 242)
    val altaria = PokemonSpecies(334, "Altaria", speciesId = "altaria", types = listOf("dragon", "flying"), baseAttack = 141, baseDefense = 201, baseStamina = 181)
    val galvantula = PokemonSpecies(596, "Galvantula", speciesId = "galvantula", types = listOf("bug", "electric"), baseAttack = 201, baseDefense = 128, baseStamina = 172)
    val swampert = PokemonSpecies(260, "Swampert", speciesId = "swampert", types = listOf("water", "ground"), baseAttack = 208, baseDefense = 175, baseStamina = 225)
    val talonflame = PokemonSpecies(663, "Talonflame", speciesId = "talonflame", types = listOf("fire", "flying"), baseAttack = 176, baseDefense = 155, baseStamina = 186)

    // Exact-current move stats from the pinned Game Master.
    val bubble = PvpMove("BUBBLE", "Bubble", type = "water", power = 8, energyGain = 11, turns = 3)
    val shadowClaw = PvpMove("SHADOW_CLAW", "Shadow Claw", type = "ghost", power = 6, energyGain = 8, turns = 2)
    val powderSnow = PvpMove("POWDER_SNOW", "Powder Snow", type = "ice", power = 6, energyGain = 8, turns = 2)
    val dragonBreath = PvpMove("DRAGON_BREATH", "Dragon Breath", type = "dragon", power = 3, energyGain = 4, turns = 1)
    val voltSwitch = PvpMove("VOLT_SWITCH", "Volt Switch", type = "electric", power = 14, energyGain = 16, turns = 4)
    val mudShot = PvpMove("MUD_SHOT", "Mud Shot", type = "ground", power = 3, energyGain = 9, turns = 2)
    val incinerate = PvpMove("INCINERATE", "Incinerate", type = "fire", power = 20, energyGain = 20, turns = 5)

    val iceBeam = PvpMove("ICE_BEAM", "Ice Beam", type = "ice", power = 90, energyCost = 55)
    val hydroPump = PvpMove("HYDRO_PUMP", "Hydro Pump", type = "water", power = 130, energyCost = 75)
    val hydroCannon = PvpMove("HYDRO_CANNON", "Hydro Cannon", type = "water", power = 80, energyCost = 40)
    val icicleSpear = PvpMove("ICICLE_SPEAR", "Icicle Spear", type = "ice", power = 70, energyCost = 40)
    val earthquake = PvpMove("EARTHQUAKE", "Earthquake", type = "ground", power = 120, energyCost = 65)
    val skyAttack = PvpMove("SKY_ATTACK", "Sky Attack", type = "flying", power = 75, energyCost = 50)
    val dragonPulse = PvpMove("DRAGON_PULSE", "Dragon Pulse", type = "dragon", power = 90, energyCost = 55)
    val lunge = PvpMove("LUNGE", "Lunge", type = "bug", power = 70, energyCost = 45, opponentAttackStageDelta = -1, buffApplyChance = 1.0)
    val discharge = PvpMove("DISCHARGE", "Discharge", type = "electric", power = 55, energyCost = 40)
    val flameCharge = PvpMove("FLAME_CHARGE", "Flame Charge", type = "fire", power = 65, energyCost = 50, attackerAttackStageDelta = 1, buffApplyChance = 1.0)
    val fireBlast = PvpMove("FIRE_BLAST", "Fire Blast", type = "fire", power = 140, energyCost = 80)

    val azu = BattleBuild("pinned-azu", azumarill, IvSpread(4, 15, 13), 43.0, bubble, listOf(iceBeam, hydroPump))
    val fera = BattleBuild("pinned-fera", feraligatr, IvSpread(6, 15, 6), 19.5, shadowClaw, listOf(hydroCannon, iceBeam))
    val wal = BattleBuild("pinned-wal", walrein, IvSpread(7, 15, 13), 20.0, powderSnow, listOf(icicleSpear, earthquake))
    val alt = BattleBuild("pinned-altaria", altaria, IvSpread(4, 12, 13), 28.5, dragonBreath, listOf(skyAttack, dragonPulse))
    val galv = BattleBuild("pinned-galvantula", galvantula, IvSpread(4, 15, 9), 25.5, voltSwitch, listOf(lunge, discharge))
    val swamp = BattleBuild("pinned-swampert", swampert, IvSpread(6, 15, 12), 18.5, mudShot, listOf(hydroCannon, earthquake))
    val talon = BattleBuild("pinned-talonflame", talonflame, IvSpread(4, 12, 15), 25.5, incinerate, listOf(flameCharge, fireBlast))

    val baselineCases: List<PinnedBattleCase> = listOf(
        PinnedBattleCase("PINNED-AZU-FERALIGATR", azu, fera, coverage = setOf("1-shield", "fast-2", "fast-3")),
        PinnedBattleCase("PINNED-AZU-FERALIGATR-SHADOW", azu, fera.copy(key = "pinned-fera-shadow", isShadow = true), coverage = setOf("shadow", "1-shield")),
        PinnedBattleCase("PINNED-AZU-WALREIN", azu, wal, coverage = setOf("1-shield", "walrein-current-data"))
    )

    /**
     * F46 expansion: symmetric 0/1/2 shields, all Fast durations 1..5, Shadow, and guaranteed
     * stat effects. External exact-pin capture remains required before these can be gating evidence.
     */
    val expandedCases: List<PinnedBattleCase> = baselineCases + listOf(
        PinnedBattleCase("EXP-AZU-FERALIGATR-0S", azu, fera, shieldsA = 0, shieldsB = 0, coverage = setOf("0-shield")),
        PinnedBattleCase("EXP-AZU-FERALIGATR-2S", azu, fera, shieldsA = 2, shieldsB = 2, coverage = setOf("2-shield")),
        PinnedBattleCase("EXP-ALTARIA-AZU-1TURN", alt, azu.copy(key = "pinned-azu-altaria"), coverage = setOf("fast-1", "meta")),
        PinnedBattleCase("EXP-GALVANTULA-SWAMPERT-4TURN-DEBUFF", galv, swamp, coverage = setOf("fast-4", "guaranteed-debuff", "meta")),
        PinnedBattleCase("EXP-TALONFLAME-GALVANTULA-5TURN-BUFF", talon, galv.copy(key = "pinned-galvantula-talon"), coverage = setOf("fast-5", "guaranteed-self-buff", "meta"))
    )

    fun validatePinnedData() {
        check(PvPokeReferencePin.COMMIT_SHA == REFERENCE_SHA)
        check(PvPokeReferencePin.SITE_VERSION == REFERENCE_VERSION)
        check(powderSnow.power == 6 && powderSnow.energyGain == 8 && powderSnow.turns == 2)
        check(icicleSpear.power == 70 && icicleSpear.energyCost == 40)
        check(earthquake.power == 120 && earthquake.energyCost == 65)
        check(dragonBreath.turns == 1 && shadowClaw.turns == 2 && bubble.turns == 3 && voltSwitch.turns == 4 && incinerate.turns == 5)
        check(lunge.opponentAttackStageDelta == -1 && lunge.buffApplyChance == 1.0)
        check(flameCharge.attackerAttackStageDelta == 1 && flameCharge.buffApplyChance == 1.0)
        check(baselineCases.map { it.fixtureId }.toSet().size == baselineCases.size)
        check(expandedCases.map { it.fixtureId }.toSet().size == expandedCases.size)
    }
}
