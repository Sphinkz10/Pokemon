import com.rui.pvpgo.SpeciesCollectionPolicy
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.domain.PvpBuildPlan
import com.rui.pvpgo.engine.BattleStats
import com.rui.pvpgo.engine.IvSpread
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.PvPRankEntry

private fun fixture(id: String, species: String, iv: IvSpread, fav: Boolean = false) =
    OwnedPokemon(id=id, speciesId=species, iv=iv, isFavorite=fav, createdAtEpochMs=1)

fun main() {
    val a=fixture("a","carbink",IvSpread(5,15,13))
    val b=fixture("b","carbink",IvSpread(15,15,15),fav=true)
    val c=fixture("c","azumarill",IvSpread(0,15,15))
    val plans=listOf(PvpBuildPlan("p1","a",League.GREAT,createdAtEpochMs=1))
    fun r(iv:IvSpread,rank:Int) = PvPRankEntry(rank,rank,iv,50.0,1499,BattleStats(100.0,100.0,100),97.0)
    val ranks=mapOf("a" to r(a.iv,18),"b" to r(b.iv,247))
    check(SpeciesCollectionPolicy.sortedForGreat(listOf(b,a),ranks).map{it.id}==listOf("a","b"))
    check(SpeciesCollectionPolicy.bestForGreat(listOf(a,b),ranks)?.id=="a")
    check(SpeciesCollectionPolicy.bestForGreat(listOf(a,b),null)==null)
    check(SpeciesCollectionPolicy.isProtected(a,plans))
    check(SpeciesCollectionPolicy.isProtected(b,emptyList()))
    check(!SpeciesCollectionPolicy.isProtected(c,plans))
    check(SpeciesCollectionPolicy.validComparison(listOf(a,b,c),"a","b"))
    check(!SpeciesCollectionPolicy.validComparison(listOf(a,b,c),"a","a"))
    check(!SpeciesCollectionPolicy.validComparison(listOf(a,b,c),"a","c"))
    check(!SpeciesCollectionPolicy.validComparison(listOf(a,b,c),"a","missing"))
    check(SpeciesCollectionPolicy.betterGreatRank(a,b,ranks)=="A")
    check(SpeciesCollectionPolicy.betterGreatRank(a,b,null)=="Indisponível")
    check(SpeciesCollectionPolicy.higherIvTotal(a,b)=="B")
    check(SpeciesCollectionPolicy.higherIvTotal(a,a)=="Empate")
    println("SPECIES_COLLECTION_POLICY_OK: 14 checks")
}
