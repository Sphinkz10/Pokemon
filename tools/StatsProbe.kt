import com.rui.pvpgo.engine.*
fun main(){
val az=PokemonSpecies(dex=184,name="Azumarill",speciesId="azumarill",types=listOf("water","fairy"),baseAttack=112,baseDefense=152,baseStamina=225)
val f=PokemonSpecies(dex=160,name="Feraligatr",speciesId="feraligatr",types=listOf("water"),baseAttack=205,baseDefense=188,baseStamina=198)
val w=PokemonSpecies(dex=365,name="Walrein",speciesId="walrein",types=listOf("ice","water"),baseAttack=182,baseDefense=176,baseStamina=242)
fun d(s:PokemonSpecies){ val e=PvPRanker.rank(s,League.GREAT,RankSettings(ivFloor=5))[63]; println("${s.speciesId} iv=${e.iv} lv=${e.level} cp=${e.cp} stats=${e.stats}")}
d(f);d(w); println("azu stats="+PokemonMath.battleStats(az,IvSpread(4,15,13),43.0)+" cp="+PokemonMath.cp(az,IvSpread(4,15,13),43.0))
}
