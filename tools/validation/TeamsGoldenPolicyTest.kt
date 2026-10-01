package com.rui.pvpgo
import com.rui.pvpgo.engine.*
import com.rui.pvpgo.domain.*

fun main() {
 var checks=0
 fun checkTeam(value:Boolean, label:String){ require(value){label}; checks++ }
 fun mon(id:String, species:String, level:Double?=20.0, fast:String?="FAST", charged:List<String> = listOf("CHARGED")) = OwnedPokemon(
    id=id, speciesId=species, iv=IvSpread(1,12,15), level=level, cp=1200, fastMoveId=fast,
    chargedMoveIds=charged, createdAtEpochMs=1L)
 val a=mon("a","carbink"); val b=mon("b","feraligatr"); val c=mon("c","mandibuzz")
 val incomplete=mon("d","clodsire",level=null)
 val nofast=mon("e","lanturn",fast=null)
 val nocharge=mon("f","azumarill",charged=emptyList())
 val pool=TeamsGoldenPolicy.completeCandidates(listOf(c,incomplete,nofast,a,nocharge,b))
 checkTeam(pool.map{it.id} == listOf("a","b","c"),"sort and complete")
 checkTeam(pool.none{it.id=="d"},"no missing level")
 checkTeam(pool.none{it.id=="e"},"no missing fast")
 checkTeam(pool.none{it.id=="f"},"no missing charged")
 checkTeam(TeamsGoldenPolicy.completeCandidates(emptyList()).isEmpty(),"empty candidate list")
 val members=listOf(TeamMember("a",TeamRole.LEAD),TeamMember("b",TeamRole.SAFE_SWITCH),TeamMember("c",TeamRole.CLOSER))
 fun team(id:String,league:League,primary:Boolean=false,time:Long=10L) = SavedTeam(id,id,league,members,primary,createdAtEpochMs=1L,updatedAtEpochMs=time)
 val g1=team("g1",League.GREAT,time=20L);val g2=team("g2",League.GREAT,primary=true,time=10L);val g3=team("g3",League.GREAT,time=30L); val u=team("u",League.ULTRA)
 val scoped=TeamsGoldenPolicy.forLeague(listOf(g1,u,g3,g2),League.GREAT)
 checkTeam(scoped.map{it.id}==listOf("g2","g3","g1"),"primary then recent")
 checkTeam(TeamsGoldenPolicy.forLeague(listOf(g1,u),League.MASTER).isEmpty(),"empty league")
 checkTeam(TeamsGoldenPolicy.forLeague(listOf(g1,u),League.ULTRA).single().id=="u","league isolation")
 val owned=listOf(a,b,c).associateBy{it.id}; val names=mapOf("carbink" to "Carbink","feraligatr" to "Feraligatr","mandibuzz" to "Mandibuzz")
 checkTeam(TeamsGoldenPolicy.memberNames(g1,owned,names)=="Carbink · Feraligatr · Mandibuzz","ordered roles")
 checkTeam(TeamsGoldenPolicy.memberNames(g1,owned.dropKey("b"),names).contains("Em falta"),"missing member display")
 checkTeam(TeamsGoldenPolicy.summary(g1,owned,emptyList()).startsWith("0/3"),"no assumed readiness")
 val plan=PvpBuildPlan("plan","a",League.GREAT,status=BuildStatus.READY,createdAtEpochMs=1L)
 checkTeam(TeamsGoldenPolicy.summary(g1,owned,listOf(plan)).startsWith("1/3"),"real readiness")
 val otherPlan=plan.copy(id="planU",league=League.ULTRA)
 checkTeam(TeamsGoldenPolicy.summary(g1,owned,listOf(otherPlan)).startsWith("0/3"),"league scoped plan")
 checkTeam(TeamsGoldenPolicy.summary(g1,owned.dropKey("a"),listOf(plan)).startsWith("1 exemplar(es) em falta · 0/3"),"missing does not count ready")
 checkTeam(TeamsGoldenPolicy.summary(g1,owned,emptyList()).contains("sem cobertura estimada"),"no fake coverage")
 checkTeam(g1.members.size==3,"three members validated")
 checkTeam(g1.members.map{it.role}.distinct().size==3,"unique roles")
 checkTeam(TeamsGoldenPolicy.forLeague(emptyList(), League.GREAT).isEmpty(),"empty data")
 checkTeam(TeamsGoldenPolicy.completeCandidates(listOf(a,a)).size==2,"policy preserves specimens; no silent dedup")
 checkTeam(pool.all{it.level!=null&&it.fastMoveId!=null&&it.chargedMoveIds.isNotEmpty()},"no invalid candidates")
 println("TEAMS_GOLDEN_POLICY_PASS checks=$checks")
}
private fun <K,V> Map<K,V>.dropKey(k:K) = filterKeys {it!=k}
