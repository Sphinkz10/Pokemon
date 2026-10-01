import com.rui.pvpgo.LocalBattleMatchupEvaluator
import com.rui.pvpgo.domain.*
import com.rui.pvpgo.engine.*

fun main() {
    val blocked = listOf("aegislash_blade","aegislash_shield","mimikyu","mimikyu_busted","cramorant","cramorant_gulping","cramorant_gorging","morpeko_full_belly","morpeko_hangry")
    check(blocked.all { !SpecialMechanicsRegistry.isStandardAdviceSafe(it) })
    check(SpecialMechanicsRegistry.unsupportedForStandardAdvice.size == 4)
    check(SpecialMechanicsRegistry.isStandardAdviceSafe("azumarill"))

    val mimikyu = PokemonSpecies(778,"Mimikyu",speciesId="mimikyu",types=listOf("ghost","fairy"),baseAttack=177,baseDefense=199,baseStamina=146)
    val azu = PokemonSpecies(184,"Azumarill",speciesId="azumarill",types=listOf("water","fairy"),baseAttack=112,baseDefense=152,baseStamina=225)
    val shadowClaw=PvpMove("SHADOW_CLAW","Shadow Claw",type="ghost",power=6,energyGain=8,turns=2)
    val bubble=PvpMove("BUBBLE","Bubble",type="water",power=8,energyGain=11,turns=3)
    val playRough=PvpMove("PLAY_ROUGH","Play Rough",type="fairy",power=90,energyCost=60)
    val shadowSneak=PvpMove("SHADOW_SNEAK","Shadow Sneak",type="ghost",power=50,energyCost=45)
    val iceBeam=PvpMove("ICE_BEAM","Ice Beam",type="ice",power=90,energyCost=55)
    val hydroPump=PvpMove("HYDRO_PUMP","Hydro Pump",type="water",power=130,energyCost=75)
    val eval=LocalBattleMatchupEvaluator(listOf(mimikyu,azu),listOf(shadowClaw,bubble,playRough,shadowSneak,iceBeam,hydroPump))
    val owned=OwnedPokemon(id="mimi",speciesId="mimikyu",iv=IvSpread(5,13,15),level=25.0,fastMoveId="SHADOW_CLAW",chargedMoveIds=listOf("SHADOW_SNEAK","PLAY_ROUGH"),createdAtEpochMs=1)
    val opp=OpponentBuild(speciesId="azumarill",fastMoveId="BUBBLE",chargedMoveIds=listOf("ICE_BEAM","HYDRO_PUMP"),iv=IvSpread(4,15,13),level=43.0)
    check(eval.evaluate(owned,opp,MatchupScenario(League.GREAT)) == null)
    println("SPECIAL_MECHANICS_V1_29_GUARD_OK")
    println("registered=${SpecialMechanicsRegistry.unsupportedForStandardAdvice.size} species=${blocked.size} authoritativeAdvice=REFUSED")
}
