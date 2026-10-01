import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.engine.*

fun main() {
    val charizard = PokemonSpecies(
        dex = 6,
        name = "Charizard",
        speciesId = "charizard",
        types = listOf("fire", "flying"),
        fastMoveIds = listOf("FIRE_SPIN", "WING_ATTACK"),
        chargedMoveIds = listOf("BLAST_BURN", "DRAGON_CLAW", "OVERHEAT"),
        eliteMoveIds = setOf("WING_ATTACK", "BLAST_BURN"),
        thirdMoveStardustCost = 10000,
        baseAttack = 223, baseDefense = 173, baseStamina = 186
    )
    val moves = listOf(
        PvpMove("FIRE_SPIN", "Fire Spin", type = "fire", power = 10, energyGain = 10, turns = 3, cooldownMs = 1500),
        PvpMove("WING_ATTACK", "Wing Attack", type = "flying", power = 5, energyGain = 8, turns = 2, cooldownMs = 1000),
        PvpMove("BLAST_BURN", "Blast Burn", type = "fire", power = 110, energyCost = 50, turns = 1),
        PvpMove("DRAGON_CLAW", "Dragon Claw", type = "dragon", power = 50, energyCost = 35, turns = 1),
        PvpMove("OVERHEAT", "Overheat", type = "fire", power = 130, energyCost = 55, turns = 1),
        PvpMove("FRUSTRATION", "Frustration", type = "normal", power = 10, energyCost = 70, turns = 1)
    ).associateBy { it.moveId }

    check(moves.getValue("WING_ATTACK").dpt == 2.5)
    check(moves.getValue("WING_ATTACK").ept == 4.0)
    check(moves.getValue("BLAST_BURN").dpe == 2.2)
    check(MoveMath.hasStab(charizard, moves.getValue("WING_ATTACK")))
    check(MoveMath.baseDpe(moves.getValue("BLAST_BURN"), charizard) == 2.64)

    val owned = OwnedPokemon(
        id = "z1", speciesId = "charizard", iv = IvSpread(0, 15, 13),
        fastMoveId = "FIRE_SPIN", chargedMoveIds = listOf("DRAGON_CLAW"),
        secondChargedMoveUnlocked = false, createdAtEpochMs = 1
    )
    val audit = MovesetAnalyzer.analyze(
        charizard, owned, "WING_ATTACK", listOf("BLAST_BURN", "DRAGON_CLAW"), moves
    )
    check(audit.requiresEliteTm)
    check(audit.requiresSecondChargedUnlock)
    check(audit.changes.any { it.targetMoveId == "WING_ATTACK" && it.requirement == MoveRequirement.ELITE_FAST_TM })
    check(audit.changes.any { it.targetMoveId == "BLAST_BURN" && it.requirement == MoveRequirement.ELITE_CHARGED_TM })


    val obstruct = PvpMove(
        moveId = "OBSTRUCT", name = "Obstruct", type = "dark", power = 15, energyCost = 40,
        attackerDefenseStageDelta = 1, opponentDefenseStageDelta = -1, buffApplyChance = 1.0
    )
    check(obstruct.hasStatEffect)
    check(obstruct.attackerDefenseStageDelta == 1 && obstruct.opponentDefenseStageDelta == -1)

    val readyOwned = owned.copy(
        fastMoveId = "WING_ATTACK",
        chargedMoveIds = listOf("BLAST_BURN", "DRAGON_CLAW"),
        secondChargedMoveUnlocked = true
    )
    val readyAudit = MovesetAnalyzer.analyze(
        charizard, readyOwned, "WING_ATTACK", listOf("BLAST_BURN", "DRAGON_CLAW"), moves
    )
    check(readyAudit.battleReadyMoves)

        val shadowSpecies = charizard.copy(speciesId = "charizard_shadow", tags = setOf("shadow"))
    val shadow = owned.copy(
        id = "zs", speciesId = "charizard_shadow", isShadow = true,
        chargedMoveIds = listOf("FRUSTRATION"), secondChargedMoveUnlocked = false
    )
    val shadowAudit = MovesetAnalyzer.analyze(
        shadowSpecies, shadow, "FIRE_SPIN", listOf("DRAGON_CLAW"), moves
    )
    check(shadowAudit.changes.any { it.requirement == MoveRequirement.REMOVE_FRUSTRATION_WHEN_ELIGIBLE })
    check(shadowAudit.warnings.any { it.contains("Frustration") })

    println("MOVE_ENGINE_OK · metrics + effects + STAB + Elite TM + second move + Shadow Frustration + battle-ready audit")
}
