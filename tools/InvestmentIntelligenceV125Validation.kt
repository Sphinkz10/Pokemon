import com.rui.pvpgo.domain.*
import com.rui.pvpgo.engine.*

private fun ok(v:Boolean,m:String){if(!v) error(m)}
private fun species(id:String, family:String="fam", elite:Set<String> = emptySet())=PokemonSpecies(
    dex=1,name=id,speciesId=id,familyId=family,types=listOf("normal"),fastMoveIds=listOf("FAST","ELITE_FAST"),chargedMoveIds=listOf("CHARGE","ELITE_CHARGE","FRUSTRATION"),eliteMoveIds=elite,
    baseAttack=120,baseDefense=120,baseStamina=120
)
private fun fast(id:String)=PvpMove(id,id,type="normal",power=5,energyGain=8,turns=1)
private fun charged(id:String)=PvpMove(id,id,type="normal",power=80,energyCost=40,turns=1)

fun main(){
    val alpha=species("alpha", elite=setOf("ELITE_CHARGE"))
    val beta=species("beta")
    val catalog=listOf(alpha,beta)
    val moves=listOf(fast("FAST"),fast("ELITE_FAST"),charged("CHARGE"),charged("ELITE_CHARGE"),charged("FRUSTRATION")).associateBy{it.moveId}
    val steps=listOf(
        PowerUpStepCost(20.0,20.5,2500,2), PowerUpStepCost(20.5,21.0,2500,2),
        PowerUpStepCost(40.0,40.5,10000,xlCandy=10), PowerUpStepCost(40.5,41.0,10000,xlCandy=10),
        PowerUpStepCost(50.0,50.5,15000,xlCandy=15), PowerUpStepCost(50.5,51.0,15000,xlCandy=15)
    )
    val speciesCosts=listOf(
        SpeciesInvestmentCost("alpha","fam",secondChargedMoveStardust=50000,secondChargedMoveCandy=50,purificationStardust=3000,purificationCandy=3),
        SpeciesInvestmentCost("beta","fam",secondChargedMoveStardust=50000,secondChargedMoveCandy=50)
    )
    val evolutionCosts=listOf(EvolutionInvestmentCost("alpha","beta","fam",100))
    val pack=InvestmentCostPackValidator.build("costs","fixture-1","fixture","2026-09-14",1L,steps,speciesCosts,evolutionCosts)
    ok(InvestmentCostPackValidator.validate(pack).isEmpty(),"F80 pack should validate")
    val tampered=pack.copy(powerUpSteps=pack.powerUpSteps.mapIndexed{i,s->if(i==0)s.copy(stardust=s.stardust+1) else s})
    ok(InvestmentCostPackValidator.validate(tampered).any{it.contains("hash")},"Tampered cost pack must fail")

    val owned=OwnedPokemon(
        id="owned-a",speciesId="alpha",iv=IvSpread(0,15,15),level=20.0,fastMoveId="FAST",chargedMoveIds=listOf("CHARGE"),secondChargedMoveUnlocked=false,createdAtEpochMs=1L
    )
    val target=InvestmentTarget("owned-a",League.GREAT,"alpha",21.0,"FAST",listOf("CHARGE","ELITE_CHARGE"))
    val poorLedger=ResourceLedger(stardust=60000,candyByFamily=mapOf("fam" to 100),eliteChargedTm=0)
    val planMissing=InvestmentPlanner.plan(owned,target,catalog,moves,pack,poorLedger)
    ok(planMissing.costs.stardust==55000,"F80 Stardust total mismatch: ${planMissing.costs.stardust}")
    ok(planMissing.costs.candyByFamily["fam"]==54,"F80 Candy total mismatch")
    ok(planMissing.costs.eliteChargedTm==1,"F80 Elite Charged TM count mismatch")
    ok(planMissing.status==InvestmentPlanStatus.MISSING_RESOURCES,"Missing Elite TM should block")
    ok(planMissing.missingResources.any{it.kind==InvestmentRequirementKind.ELITE_CHARGED_TM},"Missing Elite TM not reported")

    val richLedger=poorLedger.copy(eliteChargedTm=1)
    val ready=InvestmentPlanner.plan(owned,target,catalog,moves,pack,richLedger)
    ok(ready.status==InvestmentPlanStatus.READY_NOW && ready.exactCostKnown,"F80 ready plan mismatch")

    val evoTarget=InvestmentTarget("owned-a",League.GREAT,"beta",20.0,"FAST",listOf("CHARGE"))
    val evoPlan=InvestmentPlanner.plan(owned,evoTarget,catalog,moves,pack,richLedger)
    ok(evoPlan.requirements.any{it.kind==InvestmentRequirementKind.EVOLUTION && it.amount==100},"Evolution cost missing")
    ok(evoPlan.status==InvestmentPlanStatus.PARTIAL_UNKNOWN_COST,"Post-evolution move roll must stay unknown")

    val evidence=InvestmentUtilityEvidence(matchupRatingDelta=80.0,teamCoverageDeltaPercent=4.0,readinessDelta=1.0,evidenceLabel="fixture-standard-1v1")
    val roi=InvestmentRoiAnalyzer.analyze(ready,evidence,ResourceLedger(stardust=200000,candyByFamily=mapOf("fam" to 500),eliteChargedTm=3))
    ok(roi.priorityIndex in 0.0..100.0 && roi.explanation.contains("not a win probability"),"F81 ROI semantics mismatch")

    val queue=BuildQueuePlanner.build(
        plans=listOf("p-ready" to ready,"p-evo" to evoPlan),priorities=mapOf("p-ready" to 70,"p-evo" to 100),roiByPlanId=mapOf("p-ready" to roi)
    )
    ok(queue.first().planId=="p-ready" && queue.first().state==BuildQueueState.DO_NOW,"F82 queue should prioritize feasible build before unknown-cost build")
    ok(queue.last().state==BuildQueueState.REVIEW_UNKNOWN_COST,"F82 unknown-cost state missing")

    val shadow=OwnedPokemon(
        id="shadow-a",speciesId="alpha",iv=IvSpread(11,12,14),level=20.0,isShadow=true,fastMoveId="FAST",chargedMoveIds=listOf("FRUSTRATION"),secondChargedMoveUnlocked=false,createdAtEpochMs=1L
    )
    val shadowTarget=InvestmentTarget("shadow-a",League.GREAT,"alpha",20.0,"FAST",listOf("CHARGE"))
    val shadowPlan=InvestmentPlanner.plan(shadow,shadowTarget,catalog,moves,pack,richLedger)
    ok(shadowPlan.status==InvestmentPlanStatus.WAIT_EVENT,"F83 Frustration should create WAIT_EVENT")
    val event=EventOpportunity("rocket-window","Rocket Event",EventOpportunityKind.REMOVE_FRUSTRATION,10L,20L,setOf("alpha"),sourceLabel="fixture")
    val eventAdvice=EventOpportunityAdvisor.assess(shadow,shadowPlan,listOf(event))
    ok(eventAdvice.waitRecommended && eventAdvice.matchedOpportunityIds==listOf("rocket-window"),"F83 event match failed")

    val shadowEvidence=ShadowPurifiedScenarioEvidence("shadow",620.0,73.0,"data1","1.28")
    val purifiedEvidence=ShadowPurifiedScenarioEvidence("purified",590.0,75.0,"data1","1.28")
    val compare=ShadowPurifiedAdvisor.compare(shadow,alpha,pack,shadowEvidence,purifiedEvidence)
    ok(compare.purifiedIv=="13/14/15","F84 purified IV projection mismatch: ${compare.purifiedIv}")
    ok(compare.battleRatingDeltaPurifiedMinusShadow==-30.0 && compare.coverageDeltaPurifiedMinusShadow==2.0,"F84 evidence deltas mismatch")
    ok(compare.recommendation=="REVIEW_BOTH" && compare.caveats.any{it.contains("irreversible",true)},"F84 must not auto-purify")

    val xlOwned=owned.copy(id="xl",level=40.0,isBestBuddy=false)
    val xlPlan=XlBestBuddyPlanner.plan(xlOwned,alpha,41.0,pack)
    ok(xlPlan.xlCandyRequired==20 && xlPlan.stardustRequired==20000 && !xlPlan.bestBuddyRequired,"F85 XL plan mismatch")
    val buddyOwned=owned.copy(id="buddy",level=50.0,isBestBuddy=true)
    val buddyPlan=XlBestBuddyPlanner.plan(buddyOwned,alpha,51.0,pack)
    ok(buddyPlan.bestBuddyRequired && buddyPlan.xlCandyRequired==30,"F85 Best Buddy Lv51 mismatch")

    println("RESOURCE_LEDGER_F79_V1_29_OK")
    println("INVESTMENT_PLANNER_F80_V1_29_OK stardust=${ready.costs.stardust} candy=${ready.costs.candyByFamily["fam"]} eliteCharged=${ready.costs.eliteChargedTm}")
    println("INVESTMENT_ROI_F81_V1_29_OK priority=${"%.2f".format(roi.priorityIndex)}")
    println("BUILD_QUEUE_F82_V1_29_OK order=${queue.map{it.planId+":"+it.state}}")
    println("EVENT_OPPORTUNITY_F83_V1_29_OK")
    println("SHADOW_PURIFIED_F84_V1_29_FOUNDATION_OK delta=${compare.battleRatingDeltaPurifiedMinusShadow}/${compare.coverageDeltaPurifiedMinusShadow}")
    println("XL_BEST_BUDDY_F85_V1_29_FOUNDATION_OK xl=${xlPlan.xlCandyRequired} buddyXl=${buddyPlan.xlCandyRequired}")
}
