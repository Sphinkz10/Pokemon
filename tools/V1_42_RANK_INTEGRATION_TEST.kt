import com.rui.pvpgo.RankRepository
import com.rui.pvpgo.SpeciesCollectionPolicy
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.engine.IvSpread
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.PokemonSpecies

fun main() {
  val carbink = PokemonSpecies(dex=703,name="Carbink",speciesId="carbink",baseAttack=95,baseDefense=285,baseStamina=137)
  val ranks = RankRepository.ranks(carbink, League.GREAT)
  check(ranks.size in 1..4096)
  check(ranks[0].rank==1)
  val best = OwnedPokemon(id="top",speciesId="carbink",iv=ranks[0].iv,createdAtEpochMs=1)
  val other = OwnedPokemon(id="other",speciesId="carbink",iv=ranks.last().iv,createdAtEpochMs=1)
  val indexed = listOf(best,other).associate { it.id to checkNotNull(RankRepository.find(carbink,League.GREAT,it.iv)) }
  check(SpeciesCollectionPolicy.sortedForGreat(listOf(other,best),indexed).first().id=="top")
  check(SpeciesCollectionPolicy.bestForGreat(listOf(best,other),indexed)?.id=="top")
  check(SpeciesCollectionPolicy.betterGreatRank(best,other,indexed)=="A")
  check(SpeciesCollectionPolicy.validComparison(listOf(best,other),"top","other"))
  println("SPECIES_RANK_INTEGRATION_OK: 6 checks; ${ranks.size} valid IV spreads; ranks ${indexed.getValue("top").rank} vs ${indexed.getValue("other").rank}")
}
