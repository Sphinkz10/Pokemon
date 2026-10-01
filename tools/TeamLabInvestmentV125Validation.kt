import com.rui.pvpgo.CollectionTeamAdvisor
import com.rui.pvpgo.CollectionTeamLab
import com.rui.pvpgo.domain.*
import com.rui.pvpgo.engine.*

private fun ensure(v:Boolean,m:String){if(!v) error(m)}
private fun sp(id:String,dex:Int)=PokemonSpecies(dex=dex,name=id,speciesId=id,types=listOf("normal"),fastMoveIds=listOf("FAST"),chargedMoveIds=listOf("CH"),baseAttack=100,baseDefense=100,baseStamina=100)
private fun mon(id:String,species:String)=OwnedPokemon(id=id,speciesId=species,iv=IvSpread(0,0,0),level=20.0,fastMoveId="FAST",chargedMoveIds=listOf("CH"),createdAtEpochMs=1L)
private fun opp(id:String)=OpponentBuild(id,"FAST",listOf("CH"),IvSpread(0,0,0),20.0,false)
private class Eval(private val m:Map<Pair<String,String>,Int>):MoveAwareMatchupEvaluator{
 override fun evaluate(pokemon:OwnedPokemon,opponent:OpponentBuild,scenario:MatchupScenario):MatchupEvaluation?{
  val r=m[pokemon.id to opponent.speciesId]?:return null
  val o=when{r>500->BattleOutcome.WIN;r<500->BattleOutcome.LOSS;else->BattleOutcome.DRAW}
  val b=when{r>=650->MatchupBand.VERY_FAVORABLE;r>=550->MatchupBand.FAVORABLE;r>=450->MatchupBand.CLOSE;r>=350->MatchupBand.UNFAVORABLE;else->MatchupBand.VERY_UNFAVORABLE}
  return MatchupEvaluation(pokemon.id,opponent.speciesId,scenario,r,1000-r,o,b,10)
 }
}
private fun inv(id:String,dust:Int,elite:Int=0)=InvestmentPlan(
 target=InvestmentTarget(id,League.GREAT,id.lowercase(),20.0,"FAST",listOf("CH")),
 status=InvestmentPlanStatus.READY_NOW,
 costs=ResourceCostSummary(stardust=dust,eliteChargedTm=elite),
 requirements=buildList{if(dust>0)add(InvestmentRequirement(InvestmentRequirementKind.STARDUST,dust,detail="dust"));if(elite>0)add(InvestmentRequirement(InvestmentRequirementKind.ELITE_CHARGED_TM,elite,detail="elite"))},
 missingResources=emptyList(),costPackId="fixture",costPackVersion="1",costPackHashSha256="a".repeat(64)
)

fun main(){
 val species=listOf(sp("a",1),sp("b",2),sp("c",3),sp("d",4))
 val collection=listOf(mon("A","a"),mon("B","b"),mon("C","c"),mon("D","d"))
 val meta=VersionedMetaGroup(
  id="meta", league=League.GREAT, seasonId="s", sourceName="fixture", sourceVersion="1", generatedAtEpochMs=1L,
  entries=listOf(MetaBuildEntry("x",opp("x"),1.0),MetaBuildEntry("y",opp("y"),1.0)),
  rulesetId="great",rulesetVersion="1",dataPackHashSha256="b".repeat(64)
 )
 val rules=PvPRuleset("great","1","Great",League.GREAT,sourceName="fixture",sourceVersion="1",generatedAtEpochMs=1L)
 val scenario=MatchupScenario(League.GREAT)
 val matrix=mapOf(
  ("A" to "x") to 800,("A" to "y") to 300,
  ("B" to "x") to 700,("B" to "y") to 700,
  ("C" to "x") to 300,("C" to "y") to 800,
  ("D" to "x") to 650,("D" to "y") to 650
 )
 val plans=mapOf("A" to inv("A",10000),"B" to inv("B",90000),"C" to inv("C",10000),"D" to inv("D",20000,elite=1))
 val advisor=CollectionTeamAdvisor(species,Eval(matrix))
 val capped=advisor.recommend(collection,emptyList(),meta,rules,scenario,
  constraints=TeamAdvisorConstraints(maxStardustInvestment=50000,maxCandidatePool=4),limit=5,investmentPlansByOwnedId=plans)
 ensure(capped.recommendations.single().memberOwnedPokemonIds.toSet()==setOf("A","C","D"),"Max-cost should exclude B only")
 ensure(capped.exclusions.any{it.ownedPokemonId=="B"&&it.reason==TeamCandidateExclusionReason.MAX_COST_EXCEEDED},"Max-cost exclusion missing")
 val noElite=advisor.recommend(collection,emptyList(),meta,rules,scenario,
  constraints=TeamAdvisorConstraints(allowFutureEliteTm=false,maxCandidatePool=4),limit=5,investmentPlansByOwnedId=plans)
 ensure(noElite.recommendations.single().memberOwnedPokemonIds.toSet()==setOf("A","B","C"),"No-Elite should exclude D")
 ensure(noElite.exclusions.any{it.ownedPokemonId=="D"&&it.reason==TeamCandidateExclusionReason.ELITE_TM_NOT_ALLOWED},"Elite exclusion missing")
 val lab=CollectionTeamLab(species,Eval(matrix)).analyze(collection,emptyList(),meta,rules,scenario,
  constraints=TeamAdvisorConstraints(maxCandidatePool=4),candidateTeamLimit=10,investmentPlansByOwnedId=plans)
 ensure(lab.alternatives.any{it.kind==TeamAlternativeKind.CHEAPEST},"CHEAPEST alternative should exist")
 val cheapest=lab.alternatives.first{it.kind==TeamAlternativeKind.CHEAPEST}
 ensure(cheapest.memberOwnedPokemonIds.toSet()==setOf("A","C","D"),"Cheapest trio mismatch: ${cheapest.memberOwnedPokemonIds}")
 ensure(lab.why!!.caveats.any{it.contains("InvestmentPlan")},"WhyThisTeam must disclose cost-plan dependency")
 println("TEAM_LAB_INVESTMENT_CONSTRAINTS_F95_V1_29_OK")
 println("TEAM_LAB_CHEAPEST_F98_V1_29_OK team=${cheapest.memberOwnedPokemonIds}")
}
