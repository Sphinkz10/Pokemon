import com.rui.pvpgo.engine.*

/**
 * F44B independent policy-invariant matrix.
 *
 * This deliberately does not reproduce the production DP planner or ActionLogic line-by-line.
 * It checks only high-confidence behavioral invariants that a second implementation can derive
 * without copying the source algorithm. These scenarios therefore count toward, but do not yet
 * complete, the 24-scenario independent baseline-policy requirement.
 */
private fun ps(id:String,dex:Int,atk:Int,def:Int,sta:Int)=PokemonSpecies(
    dex=dex,name=id,speciesId=id,types=listOf("normal"),baseAttack=atk,baseDefense=def,baseStamina=sta
)
private fun pb(key:String,s:PokemonSpecies,fast:PvpMove,moves:List<PvpMove>,energy:Int=0)=BattleBuild(
    key=key,species=s,iv=IvSpread(0,0,0),level=20.0,fastMove=fast,chargedMoves=moves,initialEnergy=energy
)
private val policy=BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE,ShieldStrategy.NEVER,baitShields=false)
private fun firstDecision(r:BattleSimulationResult,key:String)=r.decisionTrace.firstOrNull{
    it.actorKey==key && (it.type==BattleDecisionType.CHARGED_SELECTED || it.type==BattleDecisionType.FAST_SELECTED)
}

fun main(){
    val engine=BattleEngine(); var scenarios=0; var assertions=0
    fun ok(c:Boolean,m:String){check(c){m};assertions++}
    val dummy=PvpMove("DUMMY","Dummy",type="normal",power=1,energyCost=100)

    // Family A: if no Charged Move is affordable, continue the Fast cycle. Three timing variants.
    for((idx,turns) in listOf(1,2,4).withIndex()){
        scenarios++
        val actor=ps("noaff-a$idx",61000+idx,180,180,220); val opp=ps("noaff-b$idx",61100+idx,180,180,220)
        val fast=PvpMove("NA_F$idx","NA F$idx",type="normal",power=5,energyGain=8,turns=turns)
        val charge=PvpMove("NA_C$idx","NA C$idx",type="normal",power=80,energyCost=40)
        val r=engine.simulate(pb("na$idx",actor,fast,listOf(charge),energy=39-idx),pb("nb$idx",opp,fast,listOf(dummy)),
            BattleScenario(shieldsA=0,shieldsB=0,policyA=policy,policyB=policy,maxTurns=1))
        val d=firstDecision(r,"na$idx")
        ok(d?.type==BattleDecisionType.FAST_SELECTED,"no-affordable variant=$idx")
    }

    // Family B: clean lethal with shields down and Fast nonlethal should be thrown immediately.
    repeat(3){idx->
        scenarios++
        val actor=ps("lethal-a$idx",61200+idx,220+idx*10,170,220); val opp=ps("lethal-b$idx",61300+idx,150,180,160+idx*10)
        val fast=PvpMove("L_F$idx","L F$idx",type="normal",power=1,energyGain=7,turns=2+idx)
        val lethal=PvpMove("L_C$idx","L C$idx",type="normal",power=5000,energyCost=40+idx*5)
        val r=engine.simulate(pb("la$idx",actor,fast,listOf(lethal),energy=lethal.energyCost),pb("lb$idx",opp,fast,listOf(dummy)),
            BattleScenario(shieldsA=0,shieldsB=0,policyA=policy,policyB=policy,maxTurns=1))
        val d=firstDecision(r,"la$idx")
        ok(d?.type==BattleDecisionType.CHARGED_SELECTED && d.moveId==lethal.moveId,"immediate lethal variant=$idx")
    }

    // Family C: a cadence delay must not be taken when one more Fast would overflow 100 energy.
    repeat(3){idx->
        scenarios++
        val actor=ps("overflow-a$idx",61400+idx,190,190,240); val opp=ps("overflow-b$idx",61500+idx,180,190,240)
        val fastA=PvpMove("O_FA$idx","O FA$idx",type="normal",power=1,energyGain=8+idx,turns=3)
        val fastB=PvpMove("O_FB$idx","O FB$idx",type="normal",power=1,energyGain=8,turns=2)
        val charge=PvpMove("O_C$idx","O C$idx",type="normal",power=30,energyCost=40)
        val energy=93+idx
        val r=engine.simulate(pb("oa$idx",actor,fastA,listOf(charge),energy),pb("ob$idx",opp,fastB,listOf(dummy)),
            BattleScenario(shieldsA=0,shieldsB=0,policyA=policy,policyB=policy,maxTurns=1))
        val d=firstDecision(r,"oa$idx")
        ok(energy+fastA.energyGain>100,"overflow fixture invariant variant=$idx")
        ok(d?.type==BattleDecisionType.CHARGED_SELECTED,"overflow must throw variant=$idx actual=${d?.type}")
    }

    // Family D: if the opponent's next Fast pressure makes another long Fast unsafe, throw the
    // highest immediate-damage affordable Charged path rather than farming.
    repeat(3){idx->
        scenarios++
        val actor=ps("urgent-a$idx",61600+idx,205,90,70+idx*5); val opp=ps("urgent-b$idx",61700+idx,300,130,200)
        val ownFast=PvpMove("U_FA$idx","U FA$idx",type="normal",power=1,energyGain=10,turns=5)
        val oppFast=PvpMove("U_FB$idx","U FB$idx",type="normal",power=55+idx*5,energyGain=8,turns=1)
        val cheap=PvpMove("U_CHEAP$idx","U Cheap$idx",type="normal",power=25+idx,energyCost=35)
        val big=PvpMove("U_BIG$idx","U Big$idx",type="normal",power=100+idx*10,energyCost=70)
        val r=engine.simulate(pb("ua$idx",actor,ownFast,listOf(cheap,big),100),pb("ub$idx",opp,oppFast,listOf(dummy)),
            BattleScenario(shieldsA=0,shieldsB=0,policyA=policy,policyB=policy,maxTurns=1))
        val d=firstDecision(r,"ua$idx")
        ok(d?.type==BattleDecisionType.CHARGED_SELECTED && d.moveId==big.moveId,"survival urgency highest damage variant=$idx actual=${d?.moveId}")
    }

    // Family E: with shields up and baiting enabled, build toward the more expensive move rather
    // than dumping the currently-affordable cheap move. Three cost/energy variants.
    repeat(3){idx->
        scenarios++
        val actor=ps("bait-a$idx",61800+idx,220,180,220); val opp=ps("bait-b$idx",61900+idx,180,200,260)
        val fast=PvpMove("B_F$idx","B F$idx",type="normal",power=3,energyGain=10+idx,turns=1)
        val cheap=PvpMove("B_CHEAP$idx","B Cheap$idx",type="normal",power=28+idx*2,energyCost=35)
        val nuke=PvpMove("B_NUKE$idx","B Nuke$idx",type="normal",power=115+idx*5,energyCost=50+idx*5)
        val r=engine.simulate(pb("ba$idx",actor,fast,listOf(cheap,nuke),35+idx*2),pb("bb$idx",opp,fast,listOf(dummy)),
            BattleScenario(shieldsA=0,shieldsB=1,policyA=policy.copy(baitShields=true),policyB=policy,maxTurns=1))
        val d=firstDecision(r,"ba$idx")
        ok(d?.type==BattleDecisionType.FAST_SELECTED,"bait build-up variant=$idx actual=${d?.type}/${d?.moveId}")
    }

    // Family F: when baiting is disabled and both moves are available, throw the larger immediate
    // damage option first instead of preserving the cheap-first order.
    repeat(3){idx->
        scenarios++
        val actor=ps("nobait-a$idx",62000+idx,220+idx*5,180,220); val opp=ps("nobait-b$idx",62100+idx,180,200,260)
        val fast=PvpMove("NB_F$idx","NB F$idx",type="normal",power=3,energyGain=10,turns=1+idx%2)
        val cheap=PvpMove("NB_CHEAP$idx","NB Cheap$idx",type="normal",power=25+idx*3,energyCost=35)
        val nuke=PvpMove("NB_NUKE$idx","NB Nuke$idx",type="normal",power=120+idx*10,energyCost=50+idx*5)
        val r=engine.simulate(pb("nba$idx",actor,fast,listOf(cheap,nuke),nuke.energyCost),pb("nbb$idx",opp,fast,listOf(dummy)),
            BattleScenario(shieldsA=0,shieldsB=1,policyA=policy.copy(baitShields=false),policyB=policy,maxTurns=1))
        val d=firstDecision(r,"nba$idx")
        ok(d?.type==BattleDecisionType.CHARGED_SELECTED && d.moveId==nuke.moveId,"no-bait reorder variant=$idx actual=${d?.moveId}")
    }

    // Family G: with shields down, a survivable opposing Charged already ready should make us
    // defer a self-debuffing throw instead of voluntarily weakening before taking that hit.
    repeat(3){idx->
        scenarios++
        val actor=ps("defer-a$idx",62200+idx,220,180+idx*5,240); val opp=ps("defer-b$idx",62300+idx,180,200,250)
        val ownFast=PvpMove("D_FA$idx","D FA$idx",type="normal",power=8,energyGain=10,turns=1)
        val oppFast=PvpMove("D_FB$idx","D FB$idx",type="normal",power=2,energyGain=8,turns=2)
        val debuff=PvpMove("D_DEBUFF$idx","D Debuff$idx",type="normal",power=88+idx*2,energyCost=45,attackerAttackStageDelta=-1)
        val safe=PvpMove("D_SAFE$idx","D Safe$idx",type="normal",power=68+idx*2,energyCost=50)
        val oppCharge=PvpMove("D_OPP$idx","D Opp$idx",type="normal",power=18+idx*2,energyCost=35)
        val r=engine.simulate(pb("da$idx",actor,ownFast,listOf(debuff,safe),90),pb("db$idx",opp,oppFast,listOf(oppCharge),35),
            BattleScenario(shieldsA=0,shieldsB=0,policyA=policy.copy(baitShields=false),policyB=policy,maxTurns=1))
        val d=firstDecision(r,"da$idx")
        ok(d?.type==BattleDecisionType.FAST_SELECTED,"self-debuff defer variant=$idx actual=${d?.type}/${d?.moveId}")
    }

    // Family H: if a pure Fast farm-down is already terminal but a guaranteed self-buff Charged is
    // available, throw the boost first so the policy does not discard a free guaranteed advantage.
    repeat(3){idx->
        scenarios++
        val actor=ps("boost-a$idx",62400+idx,220,180,220); val opp=ps("boost-b$idx",62500+idx,180,200,260)
        val farmFast=PvpMove("FB_F$idx","FB F$idx",type="normal",power=999,energyGain=10,turns=1)
        val oppFast=PvpMove("FB_OF$idx","FB OF$idx",type="normal",power=3,energyGain=10,turns=1)
        val boost=PvpMove("FB_C$idx","FB C$idx",type="normal",power=1+idx,energyCost=35,attackerAttackStageDelta=1,buffApplyChance=1.0)
        val farmBuild=BattleBuild("fba$idx",actor,IvSpread(0,15,15),30.0,farmFast,listOf(boost),initialEnergy=35)
        val farmDef=BattleBuild("fbb$idx",opp,IvSpread(0,15,15),30.0,oppFast,listOf(dummy),initialEnergy=0)
        val r=engine.simulate(farmBuild,farmDef,
            BattleScenario(shieldsA=0,shieldsB=0,policyA=policy.copy(baitShields=false),policyB=policy,maxTurns=1))
        val d=r.decisionTrace.firstOrNull { it.actorKey=="fba$idx" && it.type==BattleDecisionType.CHARGED_SELECTED }
        ok(d?.moveId==boost.moveId,"farm-down boost variant=$idx actual=${d?.type}/${d?.moveId}")
    }

    check(scenarios==24)
    println("INDEPENDENT_BASELINE_POLICY_V1_29_OK")
    println("scenarios=$scenarios assertions=$assertions families=8/8 status=PASS")
}
