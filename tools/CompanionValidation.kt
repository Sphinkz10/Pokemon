import com.rui.pvpgo.CollectionPvPService
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.engine.IvSpread
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.PokemonSpecies

fun main() {
    val charmander = PokemonSpecies(
        dex = 4, name = "Charmander", speciesId = "charmander",
        evolutionSpeciesIds = listOf("charmeleon"), baseAttack = 116, baseDefense = 93, baseStamina = 118
    )
    val charmeleon = PokemonSpecies(
        dex = 5, name = "Charmeleon", speciesId = "charmeleon", parentSpeciesId = "charmander",
        evolutionSpeciesIds = listOf("charizard"), baseAttack = 158, baseDefense = 126, baseStamina = 151
    )
    val charizard = PokemonSpecies(
        dex = 6, name = "Charizard", speciesId = "charizard", parentSpeciesId = "charmeleon",
        baseAttack = 223, baseDefense = 173, baseStamina = 186
    )
    val catalog = listOf(charmander, charmeleon, charizard)
    val old = OwnedPokemon(
        id = "old", speciesId = "charizard", iv = IvSpread(3, 15, 14),
        cp = 1496, createdAtEpochMs = 1
    )
    val candidate = OwnedPokemon(
        id = "candidate", speciesId = "charmander", iv = IvSpread(0, 15, 13),
        cp = 300, createdAtEpochMs = 2
    )

    val insights = CollectionPvPService.upgradeInsights(candidate, listOf(old), catalog)
    val greatCharizard = insights.first { it.candidate.targetSpecies.speciesId == "charizard" && it.candidate.league == League.GREAT }
    check(greatCharizard.candidate.rank.rank == 1)
    check(greatCharizard.currentBest != null)
    check(greatCharizard.currentBest!!.owned.id == "old")
    check(greatCharizard.isUpgrade)
    check((greatCharizard.rankImprovement ?: 0) > 0)

    println("COLLECTION_PVP_OK · pre-evolution candidate compared against owned evolved target")
}
