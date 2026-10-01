import com.rui.pvpgo.engine.*

fun main() {
    val azu = PokemonSpecies(184, "Azumarill", speciesId="azumarill", types=listOf("water","fairy"), baseAttack=112, baseDefense=152, baseStamina=225)
    val fera = PokemonSpecies(160, "Feraligatr", speciesId="feraligatr", types=listOf("water","none"), baseAttack=205, baseDefense=188, baseStamina=198)
    val mimi = PokemonSpecies(778, "Mimikyu", speciesId="mimikyu", types=listOf("ghost","fairy"), baseAttack=177, baseDefense=199, baseStamina=146)
    val aegis = PokemonSpecies(681, "Aegislash Shield", speciesId="aegislash_shield", types=listOf("steel","ghost"), baseAttack=97, baseDefense=272, baseStamina=155)
    val morpeko = PokemonSpecies(877, "Morpeko Full Belly", speciesId="morpeko_full_belly", types=listOf("electric","dark"), baseAttack=192, baseDefense=121, baseStamina=151)
    val cramorant = PokemonSpecies(845, "Cramorant", speciesId="cramorant", types=listOf("flying","water"), baseAttack=173, baseDefense=163, baseStamina=172)
    val bubble=PvpMove("BUBBLE","Bubble",type="water",power=8,energyGain=11,turns=3)
    val sc=PvpMove("SHADOW_CLAW","Shadow Claw",type="ghost",power=6,energyGain=8,turns=2)
    val ib=PvpMove("ICE_BEAM","Ice Beam",type="ice",power=90,energyCost=55)
    val hp=PvpMove("HYDRO_PUMP","Hydro Pump",type="water",power=130,energyCost=75)
    val hc=PvpMove("HYDRO_CANNON","Hydro Cannon",type="water",power=80,energyCost=40)
    val ss=PvpMove("SHADOW_SNEAK","Shadow Sneak",type="ghost",power=50,energyCost=45)
    val fc=PvpMove("FLASH_CANNON","Flash Cannon",type="steel",power=110,energyCost=70)
    val ts=PvpMove("THUNDER_SHOCK","Thunder Shock",type="electric",power=3,energyGain=9,turns=2)
    val auw=PvpMove("AURA_WHEEL_ELECTRIC","Aura Wheel",type="electric",power=100,energyCost=45,attackerAttackStageDelta=1,buffApplyChance=1.0)
    val peck=PvpMove("PECK","Peck",type="flying",power=6,energyGain=8,turns=2)
    val dive=PvpMove("DIVE","Dive",type="water",power=50,energyCost=40)

    fun build(key:String,s:PokemonSpecies,level:Double,iv:IvSpread,fast:PvpMove,charged:List<PvpMove>,energy:Int=0)=BattleBuild(key,s,iv,level,fast,charged,initialEnergy=energy)
    val a=build("owned:azu",azu,43.0,IvSpread(4,15,13),bubble,listOf(ib,hp))
    val b=build("opp:fera",fera,19.5,IvSpread(6,15,6),sc,listOf(hc,ib))
    val scenario=BattleScenario(
        shieldsA=1, shieldsB=1,
        policyA=BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE,ShieldStrategy.PVPOKE_DEFAULT),
        policyB=BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE,ShieldStrategy.PVPOKE_DEFAULT),
        statEffectPolicy=StatEffectPolicy.PVPOKE_DETERMINISTIC_METER
    )
    val result=BattleEngine().simulate(a,b,scenario)
    val snapshot=RecommendationEvidence.scenario(a,b,scenario)
    val card=RecommendationEvidence.card(snapshot,result)
    check(snapshot.engineVersion=="1.29")
    check(snapshot.upstreamCommitSha==PvPokeReferencePin.COMMIT_SHA)
    check(snapshot.subject.iv==IvSpread(4,15,13))
    check(snapshot.subject.chargedMoveIds==listOf("ICE_BEAM","HYDRO_PUMP"))
    check(snapshot.shieldsA==1 && snapshot.shieldsB==1)
    check(card.status==EvidenceStatus.VALIDATED)
    check(card.restrictions.any { it.contains("same-revision", ignoreCase=true) })
    check(card.restrictions.none { it.contains("baseline-policy-second-implementation") })
    check(card.restrictions.none { it.contains("queued-action-second-implementation") })
    check(card.trustStatement.contains("probabilidade"))

    val m=build("owned:mimi",mimi,25.0,IvSpread(5,13,15),sc,listOf(ss,ib))
    val mimiResult=BattleEngine().simulate(m,a,scenario.copy(shieldsA=0,shieldsB=0,maxTurns=5))
    val mimiCard=RecommendationEvidence.card(RecommendationEvidence.scenario(m,a,scenario.copy(shieldsA=0,shieldsB=0,maxTurns=5)),mimiResult)
    check(mimiCard.status==EvidenceStatus.REFUSED)
    check(mimiCard.restrictions.any { it.contains("mimikyu-disguise") })

    val g=build("owned:aegis",aegis,20.0,IvSpread(0,15,15),sc,listOf(fc,ss),energy=100)
    val aegisScenario=scenario.copy(shieldsA=0,shieldsB=0,maxTurns=5)
    val aegisResult=BattleEngine().simulate(g,a,aegisScenario)
    val aegisCard=RecommendationEvidence.card(RecommendationEvidence.scenario(g,a,aegisScenario),aegisResult)
    check(aegisCard.status==EvidenceStatus.REFUSED)
    check(aegisCard.restrictions.any { it.contains("aegislash-stance-change") })

    val mo=build("owned:morpeko",morpeko,28.5,IvSpread(5,14,15),ts,listOf(auw),energy=100)
    val morpekoScenario=scenario.copy(shieldsA=0,shieldsB=0,maxTurns=5)
    val morpekoResult=BattleEngine().simulate(mo,a,morpekoScenario)
    val morpekoCard=RecommendationEvidence.card(RecommendationEvidence.scenario(mo,a,morpekoScenario),morpekoResult)
    check(morpekoCard.status==EvidenceStatus.REFUSED)
    check(morpekoCard.restrictions.any { it.contains("morpeko-form-toggle") })

    val cr=build("owned:cramorant",cramorant,27.0,IvSpread(0,14,11),peck,listOf(dive,fc),energy=100)
    val crScenario=scenario.copy(shieldsA=0,shieldsB=0,maxTurns=5)
    val crResult=BattleEngine().simulate(cr,a,crScenario)
    val crCard=RecommendationEvidence.card(RecommendationEvidence.scenario(cr,a,crScenario),crResult)
    check(crCard.status==EvidenceStatus.REFUSED)
    check(crCard.restrictions.any { it.contains("cramorant-gulp-missile") })

    println("RECOMMENDATION_EVIDENCE_V1_29_OK")
    println("standard=${card.status} mimikyu=${mimiCard.status} aegislash=${aegisCard.status} morpeko=${morpekoCard.status} cramorant=${crCard.status} pin=${snapshot.upstreamSiteVersion}@${PvPokeReferencePin.SHORT_SHA}")
}
