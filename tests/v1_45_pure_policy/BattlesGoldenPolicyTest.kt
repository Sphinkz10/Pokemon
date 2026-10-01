package com.rui.pvpgo

import com.rui.pvpgo.engine.League
import com.rui.pvpgo.domain.*

private var checks = 0
private fun checkIt(value: Boolean, name: String) { check(value) { "FAIL $name" }; checks++ }
fun main() {
    val battles = listOf(
      BattleRecord("b1",League.GREAT,300,BattleOutcome.WIN),
      BattleRecord("b2",League.ULTRA,700,BattleOutcome.LOSS),
      BattleRecord("b3",League.GREAT,100,BattleOutcome.LOSS),
      BattleRecord("b4",League.GREAT,500,BattleOutcome.DRAW),
      BattleRecord("b0",League.GREAT,500,BattleOutcome.WIN)
    )
    val gl=BattlesGoldenPolicy.forLeague(battles,League.GREAT)
    checkIt(gl.size==4,"scoped league")
    checkIt(gl.map{it.id}==listOf("b0","b4","b1","b3"),"deterministic chronological sort")
    checkIt(BattlesGoldenPolicy.wins(gl)==2,"wins")
    checkIt(BattlesGoldenPolicy.losses(gl)==1,"losses")
    checkIt(BattlesGoldenPolicy.draws(gl)==1,"draws")
    checkIt("2 V" in BattlesGoldenPolicy.historyLabel(gl),"honest history")
    checkIt("Sem batalhas" in BattlesGoldenPolicy.historyLabel(emptyList()),"empty history")
    checkIt(BattlesGoldenPolicy.forLeague(battles,League.MASTER).isEmpty(),"no synthetic league records")
    checkIt(BattlesGoldenPolicy.wins(emptyList())==0,"zero wins")
    checkIt(BattlesGoldenPolicy.losses(emptyList())==0,"zero losses")
    checkIt(BattlesGoldenPolicy.draws(emptyList())==0,"zero draws")
    val teams=listOf(SavedTeam("t1",League.GREAT,false,500),SavedTeam("t2",League.GREAT,true,100),SavedTeam("t3",League.GREAT,false,900),SavedTeam("t4",League.MASTER,true,999))
    checkIt(BattlesGoldenPolicy.eligibleTeams(teams,League.GREAT).map{it.id}==listOf("t2","t3","t1"),"primary first")
    checkIt(BattlesGoldenPolicy.eligibleTeams(teams,League.MASTER).single().id=="t4","league specific teams")
    checkIt(BattlesGoldenPolicy.eligibleTeams(emptyList(),League.GREAT).isEmpty(),"no invented team")
    val owned=listOf(OwnedPokemon("a",20.0,"fast",listOf("charged")),OwnedPokemon("b",null,"fast",listOf("charged")),OwnedPokemon("c",21.0,null,listOf("charged")),OwnedPokemon("d",22.0,"fast",emptyList()),OwnedPokemon("e",25.0,"fast",listOf("c1","c2")),OwnedPokemon("f",26.0,"",listOf("c1")))
    checkIt(BattlesGoldenPolicy.completeOwnedCount(owned)==2,"full build completeness")
    checkIt(BattlesGoldenPolicy.completeOwnedCount(emptyList())==0,"empty owned")
    val ready=MatchupAdvisorResult(MatchupAdvisorStatus.READY,listOf("a"))
    val refused=MatchupAdvisorResult(MatchupAdvisorStatus.REFUSED,emptyList())
    val none=MatchupAdvisorResult(MatchupAdvisorStatus.NO_ELIGIBLE_CANDIDATES,emptyList())
    checkIt(BattlesGoldenPolicy.canShowRatings(ready),"ready ratings")
    checkIt(!BattlesGoldenPolicy.canShowRatings(MatchupAdvisorResult(MatchupAdvisorStatus.READY,emptyList())),"empty ready no rating")
    checkIt(!BattlesGoldenPolicy.canShowRatings(refused),"refusal protects result")
    checkIt(!BattlesGoldenPolicy.canShowRatings(none),"no candidates protects result")
    checkIt(BattlesGoldenPolicy.resultHeadline(refused).contains("recusada"),"refusal state")
    checkIt(BattlesGoldenPolicy.resultHeadline(none).contains("Sem candidatos"),"empty result state")
    checkIt("Não é probabilidade" in BattlesGoldenPolicy.RATING_NOTE,"no false odds")
    checkIt("3v3" in BattlesGoldenPolicy.RATING_NOTE,"no false 3v3")
    println("BATTLES_GOLDEN_POLICY_PASS: $checks / $checks")
}
