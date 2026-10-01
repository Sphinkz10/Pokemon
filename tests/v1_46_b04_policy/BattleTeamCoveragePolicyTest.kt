package com.rui.pvpgo

import com.rui.pvpgo.domain.*

private var checks = 0
private fun verify(condition:Boolean, label:String){ check(condition){"FAIL: $label"}; checks++ }
private val scenario=MatchupScenario(League.GREAT,1,1)
private val team=SavedTeam("t1","Great team",League.GREAT,listOf(
    SavedMember("a",TeamRole.LEAD), SavedMember("b",TeamRole.SAFE_SWITCH), SavedMember("c",TeamRole.CLOSER)))
private val owned=listOf(OwnedPokemon("a","carbink"),OwnedPokemon("b","clodsire"),OwnedPokemon("c","feraligatr"))
private fun rec(id:String, rating:Int, outcome:BattleOutcome, sc:MatchupScenario=scenario)=
    Recommendation(id,Evaluation(id,sc,rating,outcome),"Motivo real $id")
private fun result(status:MatchupAdvisorStatus=MatchupAdvisorStatus.READY,
    recommendations:List<Recommendation> =listOf(rec("a",720,BattleOutcome.WIN),rec("b",410,BattleOutcome.LOSS),rec("c",530,BattleOutcome.DRAW)),
    exclusions:List<Exclusion> = emptyList(),sc:MatchupScenario=scenario)=
    MatchupAdvisorResult(status,sc,recommendations,exclusions,if(status==MatchupAdvisorStatus.REFUSED)"recusado" else null)

fun main(){
    val full=BattleTeamCoveragePolicy.project(team,owned,result())
    verify(full.members.size==3,"three members")
    verify(full.members.map{it.role}==listOf(TeamRole.LEAD,TeamRole.SAFE_SWITCH,TeamRole.CLOSER),"role order preserved")
    verify(full.evaluated==3,"3 evaluated")
    verify(full.winsInOneVsOne==1,"counts only real 1v1 WIN")
    verify(full.unavailable==0,"zero unavailable")
    verify(full.members[0].rating==720,"exact rating")
    verify(full.members[1].rating==410,"exact rating loss")
    verify(full.members[2].outcome==BattleOutcome.DRAW,"draw is not win")
    verify(full.members[0].detail=="Motivo real a","why source preserved")
    verify(full.explanation.contains("Não é batalha 3v3"),"no fabricated team forecast")
    val partial=BattleTeamCoveragePolicy.project(team,owned,result(recommendations=listOf(rec("a",750,BattleOutcome.WIN)),exclusions=listOf(Exclusion("b","falta Fast Move"))))
    verify(partial.evaluated==1,"only actual recommendation")
    verify(partial.unavailable==2,"exclusion and not assessed unavailable")
    verify(partial.members[1].state==B04MemberState.EXCLUDED,"exclusion explicit")
    verify(partial.members[1].detail=="falta Fast Move","exclusion reason intact")
    verify(partial.members[1].rating==null,"no invented excluded rating")
    verify(partial.members[2].state==B04MemberState.UNAVAILABLE,"not in top 50 reported")
    verify(partial.members[2].rating==null,"no invented missing rating")
    verify(partial.explanation.contains("incompleta"),"partial label")
    val removed=BattleTeamCoveragePolicy.project(team,owned.filterNot {it.id=="a"},result())
    verify(removed.members[0].state==B04MemberState.UNAVAILABLE,"removed Pokémon not evaluated")
    verify(removed.members[0].rating==null,"removed Pokémon not rated")
    verify(removed.evaluated==2,"removed reduces count")
    val wrongLeague=BattleTeamCoveragePolicy.project(team,owned,result(sc=MatchupScenario(League.ULTRA)))
    verify(!wrongLeague.canDisplayEvaluations,"wrong league blocks")
    verify(wrongLeague.evaluated==0,"wrong league zero scores")
    verify(wrongLeague.members.all{it.rating==null},"wrong league all ratings hidden")
    val refused=BattleTeamCoveragePolicy.project(team,owned,result(status=MatchupAdvisorStatus.REFUSED))
    verify(!refused.canDisplayEvaluations,"refusal blocks")
    verify(refused.evaluated==0,"refusal no results")
    verify(refused.explanation=="recusado","refusal reason preserved")
    verify(refused.members.all{it.rating==null},"refusal hides even stale results")
    val empty=BattleTeamCoveragePolicy.project(team,owned,result(recommendations=emptyList()))
    verify(empty.evaluated==0,"empty READY no results")
    verify(!empty.canDisplayEvaluations,"empty READY guard")
    val wrongScenario=BattleTeamCoveragePolicy.project(team,owned,result(recommendations=listOf(rec("a",720,BattleOutcome.WIN,MatchupScenario(League.GREAT,0,2)))))
    verify(wrongScenario.evaluated==0,"mismatched shields reject recommendation")
    val alienResult=BattleTeamCoveragePolicy.project(team,owned,result(recommendations=listOf(Recommendation("a",Evaluation("different",scenario,777,BattleOutcome.WIN),"mismatch"))))
    verify(alienResult.evaluated==0,"id mismatch refuses fabricated mapping")
    verify(alienResult.members[0].rating==null,"id mismatch hides rating")
    val noEligible=BattleTeamCoveragePolicy.project(team,owned,result(status=MatchupAdvisorStatus.NO_ELIGIBLE_CANDIDATES))
    verify(noEligible.evaluated==0,"no eligible zero")
    verify(noEligible.unavailable==3,"no eligible all unavailable")
    verify(full.members.map{it.ownedPokemonId}==listOf("a","b","c"),"deterministic ordering")
    println("B04_COVERAGE_POLICY_PASS $checks checks")
}
