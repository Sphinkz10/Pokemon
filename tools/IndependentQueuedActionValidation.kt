import com.rui.pvpgo.engine.*

/**
 * F44B independent queue/order matrix. Uses simple CHEAPEST/NEVER policies so the expected result
 * depends on battle scheduling, not on the production PVPOKE_BASELINE decision planner.
 */
private fun qs(id: String, dex: Int, atk: Int = 170, def: Int = 180, sta: Int = 260) = PokemonSpecies(
    dex=dex, name=id, speciesId=id, types=listOf("normal"), baseAttack=atk, baseDefense=def, baseStamina=sta
)
private fun qb(key:String,s:PokemonSpecies,fast:PvpMove,charge:PvpMove,energy:Int=0)=BattleBuild(
    key=key,species=s,iv=IvSpread(0,0,0),level=20.0,fastMove=fast,chargedMoves=listOf(charge),initialEnergy=energy
)
private val never = BattleSidePolicy(ChargeStrategy.CHEAPEST, ShieldStrategy.NEVER)

fun main(){
    val engine=BattleEngine()
    val tank=qs("tank",60001)
    val tank2=qs("tank2",60002)
    val harmless=PvpMove("HARMLESS","Harmless",type="normal",power=1,energyCost=40)
    val medium=PvpMove("MEDIUM","Medium",type="normal",power=45,energyCost=40)
    val nuke=PvpMove("NUKE","Nuke",type="normal",power=500,energyCost=40)
    val passive=PvpMove("PASSIVE","Passive",type="normal",power=0,energyGain=1,turns=5)
    var assertions=0
    fun ok(c:Boolean,m:String){check(c){m};assertions++}
    var scenarios=0

    // 1-4: A starts a 2..5-turn Fast on T1 while B throws a nonlethal Charged on T1.
    // The queued Fast is floated and resolves on T2 after the Charged sequence.
    for(turns in 2..5){
        scenarios++
        val fast=PvpMove("F$turns","F$turns",type="normal",power=6,energyGain=7,turns=turns,cooldownMs=turns*500)
        val r=engine.simulate(qb("a$turns",tank,fast,harmless),qb("b$turns",tank2,passive,harmless,40),
            BattleScenario(shieldsA=0,shieldsB=0,policyA=never,policyB=never,maxTurns=2))
        val hit=r.timeline.single{it.type==BattleEventType.FAST_MOVE && it.actorKey=="a$turns"}
        ok(hit.turn==2,"float $turns-turn expected T2 actual T${hit.turn}")
        ok(r.decisionTrace.none{it.turn==2 && it.type==BattleDecisionType.FAST_STARTED && it.actorKey=="a$turns"},"float $turns no new start T2")
    }

    // 5: 1-turn Fast selected alongside a Charged resolves on the same T1 after Charged priority.
    run{
        scenarios++
        val fast=PvpMove("F1","F1",type="normal",power=6,energyGain=7,turns=1)
        val r=engine.simulate(qb("a1",tank,fast,harmless),qb("b1",tank2,passive,harmless,40),
            BattleScenario(shieldsA=0,shieldsB=0,policyA=never,policyB=never,maxTurns=1))
        val hit=r.timeline.single{it.type==BattleEventType.FAST_MOVE && it.actorKey=="a1"}
        val chargeIndex=r.timeline.indexOfFirst{it.type==BattleEventType.CHARGED_MOVE && it.actorKey=="b1"}
        val fastIndex=r.timeline.indexOf(hit)
        ok(hit.turn==1,"1-turn same-turn resolution")
        ok(chargeIndex in 0 until fastIndex,"charged priority before same-turn fast")
    }

    // Helper: B uses a 2-turn Fast on T1, gains enough energy at T2, then charges at T3.
    // A's 3-turn Fast from T1 is due at T3, creating a factual Charged-vs-pending-Fast ordering case.
    val a3=PvpMove("A3","A3",type="normal",power=9,energyGain=7,turns=3,cooldownMs=1500)
    val b2=PvpMove("B2","B2",type="normal",power=0,energyGain=40,turns=2,cooldownMs=1000)

    // 6: lethal Charged at T3 invalidates A's due pending Fast.
    run{
        scenarios++
        val fragile=qs("fragile",60003,atk=170,def=80,sta=80)
        val r=engine.simulate(qb("a-ko",fragile,a3,harmless),qb("b-ko",tank2,b2,nuke),
            BattleScenario(shieldsA=0,shieldsB=0,policyA=never,policyB=never,maxTurns=3))
        ok(r.timeline.any{it.turn==3 && it.type==BattleEventType.CHARGED_MOVE && it.actorKey=="b-ko"},"ko charge T3")
        ok(r.decisionTrace.any{it.turn==3 && it.type==BattleDecisionType.FAST_DROPPED && it.actorKey=="a-ko"},"ko invalidates due fast")
        ok(r.timeline.none{it.turn==3 && it.type==BattleEventType.FAST_MOVE && it.actorKey=="a-ko"},"ko no fast damage")
    }

    // 7: nonlethal Charged at T3 lets the due pending Fast resolve afterwards on T3.
    run{
        scenarios++
        val r=engine.simulate(qb("a-live",tank,a3,harmless),qb("b-live",tank2,b2,medium),
            BattleScenario(shieldsA=0,shieldsB=0,policyA=never,policyB=never,maxTurns=3))
        val chargeIndex=r.timeline.indexOfFirst{it.turn==3 && it.type==BattleEventType.CHARGED_MOVE && it.actorKey=="b-live"}
        val fastIndex=r.timeline.indexOfFirst{it.turn==3 && it.type==BattleEventType.FAST_MOVE && it.actorKey=="a-live"}
        ok(chargeIndex>=0 && fastIndex>chargeIndex,"pending fast resolves after T3 charge")
    }

    // 8: pending Fast can KO the Charged attacker after surviving the Charged itself.
    run{
        scenarios++
        val glass=qs("glassB",60004,atk=170,def=65,sta=55)
        val hardA3=PvpMove("HARD_A3","Hard A3",type="normal",power=120,energyGain=7,turns=3,cooldownMs=1500)
        val r=engine.simulate(qb("a-counter",tank,hardA3,harmless),qb("b-counter",glass,b2,medium),
            BattleScenario(shieldsA=0,shieldsB=0,policyA=never,policyB=never,maxTurns=3))
        ok(r.timeline.any{it.turn==3 && it.type==BattleEventType.CHARGED_MOVE && it.actorKey=="b-counter"},"counter charge exists")
        ok(r.timeline.any{it.turn==3 && it.type==BattleEventType.FAST_MOVE && it.actorKey=="a-counter"},"counter fast exists")
        ok(r.finalB.hp==0,"pending fast may KO after charged")
    }

    // 9-11: post-Charged lock is exactly one no-input turn; new 1-turn Fast resolves on T3.
    // Variants cover unilateral Charged, shielded Charged, and simultaneous Charged actions.
    val f1=PvpMove("POST_F1","Post F1",type="normal",power=5,energyGain=8,turns=1)
    run{
        scenarios++
        val r=engine.simulate(qb("a-post",tank,f1,harmless,40),qb("b-post",tank2,f1,harmless),
            BattleScenario(shieldsA=0,shieldsB=0,policyA=never,policyB=never,maxTurns=3))
        ok(r.decisionTrace.none{it.turn==2 && (it.type==BattleDecisionType.FAST_STARTED || it.type==BattleDecisionType.CHARGED_SELECTED)},"unilateral no input T2")
        ok(r.timeline.any{it.turn==3 && it.type==BattleEventType.FAST_MOVE && it.actorKey=="a-post"},"unilateral resumes T3")
    }
    run{
        scenarios++
        val always=BattleSidePolicy(ChargeStrategy.CHEAPEST,ShieldStrategy.ALWAYS)
        val r=engine.simulate(qb("a-shieldpost",tank,f1,medium,40),qb("b-shieldpost",tank2,f1,harmless),
            BattleScenario(shieldsA=0,shieldsB=1,policyA=never,policyB=always,maxTurns=3))
        ok(r.timeline.any{it.turn==1 && it.type==BattleEventType.SHIELD},"shielded sequence T1")
        ok(r.decisionTrace.none{it.turn==2 && (it.type==BattleDecisionType.FAST_STARTED || it.type==BattleDecisionType.CHARGED_SELECTED)},"shielded no input T2")
        ok(r.timeline.any{it.turn==3 && it.type==BattleEventType.FAST_MOVE},"shielded resumes T3")
    }
    run{
        scenarios++
        val r=engine.simulate(qb("a-both",tank,f1,harmless,40),qb("b-both",tank2,f1,harmless,40),
            BattleScenario(shieldsA=0,shieldsB=0,policyA=never,policyB=never,maxTurns=3))
        ok(r.timeline.count{it.turn==1 && it.type==BattleEventType.CHARGED_MOVE}==2,"both charged T1")
        ok(r.decisionTrace.none{it.turn==2 && (it.type==BattleDecisionType.FAST_STARTED || it.type==BattleDecisionType.CHARGED_SELECTED)},"both no input T2")
        ok(r.timeline.count{it.turn==3 && it.type==BattleEventType.FAST_MOVE}==2,"both resume T3")
    }

    // 12: a Fast queued before a Charged sequence is not started a second time when floated.
    run{
        scenarios++
        val f5=PvpMove("UNIQUE_F5","Unique F5",type="normal",power=8,energyGain=9,turns=5,cooldownMs=2500)
        val r=engine.simulate(qb("a-unique",tank,f5,harmless),qb("b-unique",tank2,passive,harmless,40),
            BattleScenario(shieldsA=0,shieldsB=0,policyA=never,policyB=never,maxTurns=2))
        ok(r.decisionTrace.count{it.actorKey=="a-unique" && it.type==BattleDecisionType.FAST_STARTED}==1,"floating fast starts once")
        ok(r.timeline.count{it.actorKey=="a-unique" && it.type==BattleEventType.FAST_MOVE}==1,"floating fast resolves once")
        ok(r.finalA.fastMovesUsed==1,"floating fast usage count once")
    }

    check(scenarios==12)
    println("INDEPENDENT_QUEUED_ACTIONS_V1_29_OK")
    println("scenarios=$scenarios assertions=$assertions coverage=float-2-5,same-turn-1,ko-drop,charged-before-due-fast,post-lock,resume,no-duplicate")
}
