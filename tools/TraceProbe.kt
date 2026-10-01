import com.rui.pvpgo.engine.*

fun main() {
    val engine = BattleEngine()
    val azumarill = PokemonSpecies(dex = 184, name = "Azumarill", speciesId = "azumarill", types = listOf("water", "fairy"), baseAttack = 112, baseDefense = 152, baseStamina = 225)
    val feraligatr = PokemonSpecies(dex = 160, name = "Feraligatr", speciesId = "feraligatr", types = listOf("water"), baseAttack = 205, baseDefense = 188, baseStamina = 198)
    val walrein = PokemonSpecies(dex = 365, name = "Walrein", speciesId = "walrein", types = listOf("ice", "water"), baseAttack = 182, baseDefense = 176, baseStamina = 242)
    val bubble = PvpMove("BUBBLE", "Bubble", type = "water", power = 8, energyGain = 11, turns = 3)
    val shadowClaw = PvpMove("SHADOW_CLAW", "Shadow Claw", type = "ghost", power = 6, energyGain = 8, turns = 2)
    val powderSnow = PvpMove("POWDER_SNOW", "Powder Snow", type = "ice", power = 5, energyGain = 8, turns = 2)
    val iceBeam = PvpMove("ICE_BEAM", "Ice Beam", type = "ice", power = 90, energyCost = 55)
    val hydroPump = PvpMove("HYDRO_PUMP", "Hydro Pump", type = "water", power = 130, energyCost = 75)
    val hydroCannon = PvpMove("HYDRO_CANNON", "Hydro Cannon", type = "water", power = 80, energyCost = 40)
    val icicleSpear = PvpMove("ICICLE_SPEAR", "Icicle Spear", type = "ice", power = 65, energyCost = 40)
    val earthquake = PvpMove("EARTHQUAKE", "Earthquake", type = "ground", power = 110, energyCost = 65)
    val azuBuild = BattleBuild("azu", azumarill, IvSpread(4,15,13), 43.0, bubble, listOf(iceBeam, hydroPump))
    fun default64(species: PokemonSpecies): PvPRankEntry = PvPRanker.rank(species, League.GREAT, RankSettings(ivFloor=5))[63]
    val f = default64(feraligatr); val w = default64(walrein)
    val fera = BattleBuild("fera", feraligatr, f.iv, f.level, shadowClaw, listOf(hydroCannon, iceBeam))
    val wal = BattleBuild("wal", walrein, w.iv, w.level, powderSnow, listOf(icicleSpear, earthquake))
    val sc = BattleScenario(shieldsA=1, shieldsB=1,
        policyA=BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.PVPOKE_DEFAULT),
        policyB=BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.PVPOKE_DEFAULT),
        statEffectPolicy=StatEffectPolicy.PVPOKE_DETERMINISTIC_METER)
    fun show(name:String, opp:BattleBuild) {
        val r=engine.simulate(azuBuild, opp, sc)
        println("=== $name rating=${r.battleRatingA}/${r.battleRatingB} outcome=${r.outcomeForA} turns=${r.turns}")
        println("A ${r.finalA}")
        println("B ${r.finalB}")
        r.timeline.forEach { println("T${it.turn} actor=${it.actorKey} type=${it.type} move=${it.moveId} dmg=${it.damage} en=${it.energyAfter} shield=${it.shielded} note=${it.note}") }
    }
    show("Fera", fera)
    show("Shadow Fera", fera.copy(key="feraS", isShadow=true))
    show("Wal", wal)
}
