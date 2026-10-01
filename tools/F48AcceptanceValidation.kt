import com.rui.pvpgo.LocalBattleMatchupEvaluator
import com.rui.pvpgo.domain.*
import com.rui.pvpgo.engine.*

fun main() {
    val cert = BattleAdviceCertificationGate.current()
    check(cert.exactPin.status == CertificationRouteStatus.BLOCKED)
    check(cert.independentFallback.status == CertificationRouteStatus.CERTIFIED)
    check(cert.selectedRoute == CertificationRoute.INDEPENDENT_FALLBACK)
    check(cert.authoritativeStandard1v1AdviceOpen)
    check(BattleParityGate.current().capabilities.first { it.id == "3v3" }.status == ParityStatus.PENDING)

    val azu = PokemonSpecies(184,"Azumarill",speciesId="azumarill",types=listOf("water","fairy"),baseAttack=112,baseDefense=152,baseStamina=225)
    val fera = PokemonSpecies(160,"Feraligatr",speciesId="feraligatr",types=listOf("water"),baseAttack=205,baseDefense=188,baseStamina=198)
    val mimi = PokemonSpecies(778,"Mimikyu",speciesId="mimikyu",types=listOf("ghost","fairy"),baseAttack=177,baseDefense=199,baseStamina=146)
    val bubble=PvpMove("BUBBLE","Bubble",type="water",power=8,energyGain=11,turns=3)
    val sc=PvpMove("SHADOW_CLAW","Shadow Claw",type="ghost",power=6,energyGain=8,turns=2)
    val ib=PvpMove("ICE_BEAM","Ice Beam",type="ice",power=90,energyCost=55)
    val hp=PvpMove("HYDRO_PUMP","Hydro Pump",type="water",power=130,energyCost=75)
    val hc=PvpMove("HYDRO_CANNON","Hydro Cannon",type="water",power=80,energyCost=40)
    val ss=PvpMove("SHADOW_SNEAK","Shadow Sneak",type="ghost",power=50,energyCost=45)

    val eval=LocalBattleMatchupEvaluator(listOf(azu,fera,mimi),listOf(bubble,sc,ib,hp,hc,ss))
    val owned=OwnedPokemon(id="azu-1",speciesId="azumarill",iv=IvSpread(4,15,13),level=43.0,fastMoveId="BUBBLE",chargedMoveIds=listOf("ICE_BEAM","HYDRO_PUMP"),createdAtEpochMs=1)
    val opp=OpponentBuild(speciesId="feraligatr",fastMoveId="SHADOW_CLAW",chargedMoveIds=listOf("HYDRO_CANNON","ICE_BEAM"),iv=IvSpread(6,15,6),level=19.5)
    val standard=eval.evaluate(owned,opp,MatchupScenario(League.GREAT))
    check(standard != null)
    check(standard.explanation?.contains("INDEPENDENT_FALLBACK") == true)

    val specialOwned=OwnedPokemon(id="mimi-1",speciesId="mimikyu",iv=IvSpread(5,13,15),level=25.0,fastMoveId="SHADOW_CLAW",chargedMoveIds=listOf("SHADOW_SNEAK","ICE_BEAM"),createdAtEpochMs=1)
    check(eval.evaluate(specialOwned,opp,MatchupScenario(League.GREAT)) == null)

    println("F48_ACCEPTANCE_V1_29_OK")
    println("route=${cert.selectedRoute} exact=${cert.exactPin.status} fallback=${cert.independentFallback.status} standardAdvice=OPEN specialAdvice=REFUSED")
}
